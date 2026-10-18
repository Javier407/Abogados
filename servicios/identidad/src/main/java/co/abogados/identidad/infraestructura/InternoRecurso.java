package co.abogados.identidad.infraestructura;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.identidad.aplicacion.CuentasServicio;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.TokenAcceso;

/**
 * Rutas para los otros servicios, solo dentro de la red de Docker (el gateway no las enruta: /interno no está en
 * sus rutas y lo que no está se niega).
 * - /interno/token: un servicio entrega su clave (infra/.env) y recibe un token de 15 min con rol SERVICIO.
 * - /interno/revocados: jti de los tokens de acceso cortados antes de vencer (el gateway los rechaza).
 * - /interno/clientes/{id}/cuentas: a quién avisar de un cliente (lo usa notificaciones con su token).
 */
@RestController
public class InternoRecurso {

	private static final Logger log = LoggerFactory.getLogger(InternoRecurso.class);

	public record SolicitudToken(String servicio, String clave) {
	}

	public record CuentaCliente(long idUsuario, String nombre, String email) {
	}

	private final Map<String, byte[]> claves = new HashMap<>();
	private final EmisorJwtRsa emisor;
	private final SesionesServicio sesiones;
	private final CuentasServicio cuentas;
	private final Clock reloj;

	/** claves: "procesos=...,gabinete=..." (cada servicio recibe solo la suya). */
	public InternoRecurso(@Value("${servicios.claves:}") String claves, EmisorJwtRsa emisor, SesionesServicio sesiones,
			CuentasServicio cuentas, Clock reloj) {
		Arrays.stream(claves.split(",")).map(String::strip).filter(c -> c.contains("=")).forEach(c -> {
			String[] partes = c.split("=", 2);
			if (!partes[1].isBlank()) {
				this.claves.put(partes[0].strip(), sha256(partes[1].strip()));
			}
		});
		this.emisor = emisor;
		this.sesiones = sesiones;
		this.cuentas = cuentas;
		this.reloj = reloj;
		log.info("Servicios con clave para pedir token: {}", this.claves.keySet());
	}

	@PostMapping("/interno/token")
	public ResponseEntity<?> token(@RequestBody SolicitudToken s) {
		byte[] esperada = s == null || s.servicio() == null ? null : claves.get(s.servicio());
		// Comparación en tiempo constante de los hash: no revela cuántos caracteres coinciden.
		boolean valida = esperada != null && s.clave() != null && MessageDigest.isEqual(esperada, sha256(s.clave()));
		if (!valida) {
			log.warn("Pedido de token de servicio rechazado para '{}'", s == null ? null : s.servicio());
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Servicio o clave incorrectos"));
		}
		TokenAcceso t = emisor.servicio(s.servicio(), Instant.now(reloj));
		return ResponseEntity.ok(Map.of("token", t.token(), "expira", t.expira()));
	}

	@GetMapping("/interno/revocados")
	public List<String> revocados() {
		return sesiones.revocados();
	}

	@GetMapping("/interno/clientes/{idCliente}/cuentas")
	public ResponseEntity<?> cuentasDeCliente(@PathVariable int idCliente,
			@RequestHeader(name = "X-Usuario-Roles", required = false) String roles) {
		if (roles == null || Arrays.stream(roles.split(",")).noneMatch("SERVICIO"::equals)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Solo para servicios internos"));
		}
		return ResponseEntity.ok(cuentas.deCliente(idCliente).stream()
				.map(c -> new CuentaCliente(c.idUsuario(), c.nombre(), c.email())).toList());
	}

	/** Cada hora se borran los refresh tokens y las revocaciones ya vencidos. */
	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
	public void limpiar() {
		sesiones.limpiar();
	}

	private static byte[] sha256(String texto) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
