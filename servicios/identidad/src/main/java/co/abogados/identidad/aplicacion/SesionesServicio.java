package co.abogados.identidad.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.identidad.dominio.Refresco;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.SesionInvalidaException;
import co.abogados.identidad.dominio.TokenAcceso;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.EmisorTokens;
import co.abogados.identidad.dominio.puertos.SesionesRepositorio;
import co.abogados.identidad.dominio.puertos.UsuarioRepositorio;

/**
 * Sesiones renovables y revocables.
 * - Al entrar se entrega un token de acceso corto (15 min) y un refresh token de un solo uso (12 h).
 * - Renovar gasta el refresh token y entrega otro de la misma familia. Si llega uno ya gastado, alguien lo copió:
 *   se revoca toda la familia (también los tokens de acceso que siguen vigentes) y hay que entrar de nuevo.
 * - Una sesión dura como máximo 7 días aunque se renueve.
 * - Cerrar sesión, cambiar la contraseña, desactivar la cuenta o cambiarle los roles cortan las sesiones abiertas.
 * - Del refresh token solo se guarda el SHA-256: quien lea la base no puede usarlo.
 */
@Service
public class SesionesServicio {

	private static final Logger log = LoggerFactory.getLogger(SesionesServicio.class);
	private static final SecureRandom AZAR = new SecureRandom();
	private static final String VENCIDA = "La sesión venció; inicie sesión de nuevo";

	private final SesionesRepositorio sesiones;
	private final UsuarioRepositorio usuarios;
	private final EmisorTokens tokens;
	private final Clock reloj;
	private final Duration vigenciaRefresco;
	private final Duration maximoSesion;

	public SesionesServicio(SesionesRepositorio sesiones, UsuarioRepositorio usuarios, EmisorTokens tokens, Clock reloj,
			@Value("${sesiones.refresco:PT12H}") Duration vigenciaRefresco,
			@Value("${sesiones.maximo:P7D}") Duration maximoSesion) {
		this.sesiones = sesiones;
		this.usuarios = usuarios;
		this.tokens = tokens;
		this.reloj = reloj;
		this.vigenciaRefresco = vigenciaRefresco;
		this.maximoSesion = maximoSesion;
	}

	/** Abre una sesión nueva (familia nueva) para quien acaba de verificar su contraseña. */
	@Transactional
	public Sesion abrir(Usuario u, String ip) {
		Instant ahora = Instant.now(reloj);
		return emitir(u, UUID.randomUUID(), ahora.plus(maximoSesion), ahora, ip);
	}

	/** Gasta el refresh token y entrega otro con un token de acceso nuevo (roles y estado actuales de la cuenta). */
	@Transactional(noRollbackFor = SesionInvalidaException.class)
	public Sesion renovar(String refresco, String ip) {
		Instant ahora = Instant.now(reloj);
		Refresco r = sesiones.porHash(hash(refresco)).orElseThrow(() -> new SesionInvalidaException(VENCIDA));
		if (r.revocado() || r.vencido(ahora)) {
			throw new SesionInvalidaException(VENCIDA);
		}
		if (r.usado() || !sesiones.marcarUsado(r.id(), ahora)) {
			int cortados = sesiones.revocarFamilia(r.familia(), "REUTILIZADO", ahora);
			log.warn("Refresh token reutilizado de la cuenta {} (IP {}): se cierra la sesión ({} tokens de acceso cortados)",
					r.idUsuario(), ip, cortados);
			throw new SesionInvalidaException("La sesión se cerró por seguridad; inicie sesión de nuevo");
		}
		Usuario u = usuarios.porId(r.idUsuario()).orElse(null);
		if (u == null || !u.activo() || u.bloqueado(ahora)) {
			sesiones.revocarFamilia(r.familia(), "CUENTA_INACTIVA", ahora);
			throw new SesionInvalidaException(VENCIDA);
		}
		return emitir(u, r.familia(), r.familiaExpira(), ahora, ip);
	}

	/** Cierra la sesión del refresh token (si existe). Cerrar dos veces no es error. */
	@Transactional
	public void cerrar(String refresco) {
		if (refresco == null || refresco.isBlank()) {
			return;
		}
		sesiones.porHash(hash(refresco))
				.ifPresent(r -> sesiones.revocarFamilia(r.familia(), "CERRADA", Instant.now(reloj)));
	}

	/** Corta todas las sesiones de la persona salvo, si se indica, la del refresh token con que hace la petición. */
	@Transactional
	public int cerrarTodas(long idUsuario, String motivo, String salvoRefresco) {
		Instant ahora = Instant.now(reloj);
		UUID salvo = salvoRefresco == null ? null
				: sesiones.porHash(hash(salvoRefresco)).filter(r -> r.idUsuario() == idUsuario).map(Refresco::familia)
						.orElse(null);
		int n = sesiones.revocarUsuario(idUsuario, salvo, motivo, ahora);
		if (n > 0) {
			log.info("Sesiones de la cuenta {} cortadas ({}): {} tokens de acceso revocados", idUsuario, motivo, n);
		}
		return n;
	}

	@Transactional(readOnly = true)
	public List<String> revocados() {
		return sesiones.revocados(Instant.now(reloj));
	}

	@Transactional
	public void limpiar() {
		sesiones.limpiar(Instant.now(reloj));
	}

	private Sesion emitir(Usuario u, UUID familia, Instant familiaExpira, Instant ahora, String ip) {
		boolean temporal = usuarios.debeCambiarClave(u.idUsuario());
		TokenAcceso acceso = tokens.acceso(u, temporal, ahora);
		String refresco = nuevoToken();
		Instant expira = ahora.plus(vigenciaRefresco);
		if (expira.isAfter(familiaExpira)) {
			expira = familiaExpira;
		}
		if (!expira.isAfter(ahora)) {
			throw new SesionInvalidaException(VENCIDA);   // la sesión llegó a su duración máxima
		}
		sesiones.guardar(new Refresco(UUID.randomUUID(), familia, u.idUsuario(), expira, familiaExpira, null, null),
				hash(refresco), acceso.jti(), acceso.expira(), ip);
		return new Sesion(acceso.token(), acceso.expira(), u.idUsuario(), u.usuario(), u.nombre(), u.roles(), temporal,
				u.idCliente(), refresco, expira);
	}

	/** 256 bits al azar, en Base64 para URL. */
	private static String nuevoToken() {
		byte[] b = new byte[32];
		AZAR.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	static String hash(String token) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
