package co.abogados.identidad.infraestructura;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.identidad.aplicacion.AccesoExternoServicio;
import co.abogados.identidad.aplicacion.AccesoExternoServicio.Ida;
import co.abogados.identidad.dominio.AccesoExterno.Enlace;
import co.abogados.identidad.dominio.AccesoExterno.Modo;
import co.abogados.identidad.dominio.AccesoExterno.Proveedor;
import co.abogados.identidad.dominio.AccesoExterno.Resultado;
import co.abogados.identidad.dominio.RecursoNoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Entrar con Google, Microsoft o GitHub (solo cuentas ya existentes, enlazadas desde «Mi cuenta»).
 * - POST .../entrar y .../enlazar devuelven la URL del proveedor y dejan en el navegador una cookie con el
 *   comprobante del flujo (HttpOnly, SameSite=Lax para que vuelva con la redirección del proveedor, 10 min).
 * - GET .../vuelta es a donde regresa el proveedor: termina siempre en una redirección a /login o /cuenta. Si se
 *   abrió la sesión, deja la cookie de la sesión y la interfaz pide el token con /auth/renovar, como al recargar.
 *   Si falta el segundo paso, el desafío va en el fragmento (#), que el navegador no envía a ningún servidor.
 */
@RestController
public class AccesoExternoRecurso {

	static final String COOKIE_FLUJO = "gj_oauth";
	static final String RUTA = "/api/auth/externo";

	public record OpcionProveedor(String codigo, String nombre) {
	}

	public record Url(String url) {
	}

	private final AccesoExternoServicio servicio;
	private final LimiteIntentosPorIp limite;
	private final boolean cookieSegura;
	private final String urlPublica;

	public AccesoExternoRecurso(AccesoExternoServicio servicio, LimiteIntentosPorIp limite,
			@Value("${sesiones.cookie-segura:true}") boolean cookieSegura, @Value("${externo.url-publica:}") String urlPublica) {
		this.servicio = servicio;
		this.limite = limite;
		this.cookieSegura = cookieSegura;
		this.urlPublica = urlPublica == null ? "" : urlPublica.strip().replaceAll("/+$", "");
	}

	/** Botones del inicio de sesión: los proveedores configurados (ninguno si falta la URL pública). */
	@GetMapping("/auth/externo/proveedores")
	public List<OpcionProveedor> proveedores() {
		if (urlPublica.isEmpty()) {
			return List.of();
		}
		return servicio.disponibles().stream().map(p -> new OpcionProveedor(p.name(), p.nombre())).toList();
	}

	@PostMapping("/auth/externo/{proveedor}/entrar")
	public ResponseEntity<?> entrar(@PathVariable String proveedor,
			@RequestHeader(name = "X-Requested-With", required = false) String ajax, HttpServletRequest peticion) {
		if (ajax == null || ajax.isBlank()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Petición no permitida"));
		}
		if (!limite.permitir(AuthRecurso.ip(peticion))) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
					.body(Map.of("error", "Demasiados intentos desde esta dirección. Espere unos minutos."));
		}
		return ida(proveedorDe(proveedor), Modo.ENTRAR, null);
	}

	@PostMapping("/auth/externo/{proveedor}/enlazar")
	public ResponseEntity<?> enlazar(@PathVariable String proveedor, @RequestHeader("X-Usuario-Id") long idUsuario) {
		return ida(proveedorDe(proveedor), Modo.ENLAZAR, idUsuario);
	}

	@GetMapping("/auth/externo/{proveedor}/vuelta")
	public ResponseEntity<Void> vuelta(@PathVariable String proveedor,
			@RequestParam(required = false) String code, @RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			@CookieValue(name = COOKIE_FLUJO, required = false) String comprobante,
			HttpServletRequest peticion, HttpServletResponse respuesta) {
		Proveedor p = Proveedor.de(proveedor).orElse(null);
		Resultado r = p == null ? new Resultado.Fallo(Modo.ENTRAR, "proveedor")
				: servicio.volver(p, code, state, comprobante, error, urlVuelta(p), AuthRecurso.ip(peticion));
		HttpHeaders cabeceras = new HttpHeaders();
		cabeceras.add(HttpHeaders.SET_COOKIE, cookieFlujo("", Duration.ZERO).toString());   // el flujo terminó
		cabeceras.setCacheControl("no-store");
		cabeceras.setLocation(URI.create(destino(r, cabeceras)));
		return new ResponseEntity<>(cabeceras, HttpStatus.FOUND);
	}

	@GetMapping("/auth/externo/enlaces")
	public List<Enlace> enlaces(@RequestHeader("X-Usuario-Id") long idUsuario) {
		return servicio.enlaces(idUsuario);
	}

	@DeleteMapping("/auth/externo/enlaces/{proveedor}")
	public ResponseEntity<Void> quitar(@PathVariable String proveedor, @RequestHeader("X-Usuario-Id") long idUsuario) {
		servicio.quitar(idUsuario, proveedorDe(proveedor));
		return ResponseEntity.noContent().build();
	}

	/** A dónde vuelve el navegador según el resultado (rutas de la propia app, nunca externas). */
	private String destino(Resultado r, HttpHeaders cabeceras) {
		if (r instanceof Resultado.SesionAbierta s) {
			cabeceras.add(HttpHeaders.SET_COOKIE, AuthRecurso.cookieDe(s.sesion(), cookieSegura).toString());
			return "/login?externo=ok";
		}
		if (r instanceof Resultado.SegundoPaso s) {
			return "/login#desafio=" + codificar(s.desafio()) + "&configurar=" + s.configurar();
		}
		if (r instanceof Resultado.Enlazada e) {
			return "/cuenta?enlazado=" + e.proveedor().name();
		}
		Resultado.Fallo f = (Resultado.Fallo) r;
		return (f.modo() == Modo.ENLAZAR ? "/cuenta?enlazado=error&motivo=" : "/login?externo=error&motivo=") + codificar(f.motivo());
	}

	private ResponseEntity<Url> ida(Proveedor p, Modo modo, Long idUsuario) {
		if (urlPublica.isEmpty()) {
			throw new RecursoNoEncontradoException("Falta configurar la URL pública (URL_PUBLICA) para entrar con " + p.nombre());
		}
		Ida ida = servicio.iniciar(p, modo, idUsuario, urlVuelta(p));
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookieFlujo(ida.comprobante(), AccesoExternoServicio.VIDA_ESTADO).toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(new Url(ida.url()));
	}

	/** La dirección registrada en el proveedor: {URL_PUBLICA}/api/auth/externo/{proveedor}/vuelta. */
	String urlVuelta(Proveedor p) {
		return urlPublica + RUTA + "/" + p.name().toLowerCase(java.util.Locale.ROOT) + "/vuelta";
	}

	private ResponseCookie cookieFlujo(String valor, Duration vida) {
		return ResponseCookie.from(COOKIE_FLUJO, valor).httpOnly(true).secure(cookieSegura).sameSite("Lax")
				.path(RUTA).maxAge(vida).build();
	}

	private static Proveedor proveedorDe(String texto) {
		return Proveedor.de(texto).orElseThrow(() -> new RecursoNoEncontradoException("Proveedor desconocido"));
	}

	private static String codificar(String texto) {
		return URLEncoder.encode(texto, StandardCharsets.UTF_8);
	}

}
