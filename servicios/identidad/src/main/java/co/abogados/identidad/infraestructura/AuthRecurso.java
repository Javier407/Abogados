package co.abogados.identidad.infraestructura;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.SesionInvalidaException;
import co.abogados.identidad.dominio.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * API de identidad.
 * - /auth/login: la única ruta que recibe usuario y contraseña. Devuelve el token de acceso (15 min) y deja el
 *   refresh token en una cookie HttpOnly (el JavaScript de la página no puede leerla), SameSite=Strict y, con
 *   HTTPS, Secure. La cookie solo viaja a /api/auth.
 * - /auth/renovar y /auth/salir usan esa cookie y además piden la cabecera X-Requested-With: otra página no
 *   puede enviarla sin permiso de CORS, así que no se pueden forzar desde otro sitio (CSRF).
 * - /auth/yo y /auth/clave identifican a la persona con el token (X-Usuario-Id, que el filtro toma del token).
 */
@RestController
public class AuthRecurso {

	static final String COOKIE = "gj_refresco";
	static final String RUTA_COOKIE = "/api/auth";

	public record Ingreso(@NotBlank(message = "Escriba el usuario") @Size(max = 120) String usuario,
			@NotBlank(message = "Escriba la contraseña") @Size(max = 200) String clave) {
	}

	public record CambioClave(@NotBlank(message = "Escriba la contraseña actual") String actual,
			@NotBlank(message = "Escriba la contraseña nueva") String nueva) {
	}

	public record Perfil(long idUsuario, String usuario, String nombre, String email, Set<String> roles,
			Integer idCliente) {
		static Perfil de(Usuario u) {
			return new Perfil(u.idUsuario(), u.usuario(), u.nombre(), u.email(), u.roles(), u.idCliente());
		}
	}

	/** Lo que ve la interfaz: nunca el refresh token (va en la cookie). */
	public record SesionRespuesta(String token, Instant expira, long idUsuario, String usuario, String nombre,
			Set<String> roles, boolean debeCambiarClave, Integer idCliente) {
		static SesionRespuesta de(Sesion s) {
			return new SesionRespuesta(s.token(), s.expira(), s.idUsuario(), s.usuario(), s.nombre(), s.roles(),
					s.debeCambiarClave(), s.idCliente());
		}
	}

	private final AutenticacionServicio servicio;
	private final SesionesServicio sesiones;
	private final EmisorJwtRsa emisor;
	private final LimiteIntentosPorIp limite;
	private final boolean cookieSegura;

	public AuthRecurso(AutenticacionServicio servicio, SesionesServicio sesiones, EmisorJwtRsa emisor,
			LimiteIntentosPorIp limite, @Value("${sesiones.cookie-segura:true}") boolean cookieSegura) {
		this.servicio = servicio;
		this.sesiones = sesiones;
		this.emisor = emisor;
		this.limite = limite;
		this.cookieSegura = cookieSegura;
	}

	@PostMapping("/auth/login")
	public ResponseEntity<?> login(@Valid @RequestBody Ingreso ingreso, HttpServletRequest peticion) {
		String ip = ip(peticion);
		if (!limite.permitir(ip)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
					.header("Retry-After", String.valueOf(LimiteIntentosPorIp.VENTANA.toSeconds()))
					.body(Map.of("error", "Demasiados intentos desde esta dirección. Espere unos minutos."));
		}
		return conCookie(servicio.iniciarSesion(ingreso.usuario(), ingreso.clave(), ip));
	}

	/** Token de acceso nuevo a cambio del refresh token de la cookie (que se gasta y se reemplaza). */
	@PostMapping("/auth/renovar")
	public ResponseEntity<?> renovar(@CookieValue(name = COOKIE, required = false) String refresco,
			@RequestHeader(name = "X-Requested-With", required = false) String ajax, HttpServletRequest peticion) {
		if (ajax == null || ajax.isBlank()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Petición no permitida"));
		}
		if (refresco == null || refresco.isBlank()) {
			return sinSesion("Inicie sesión para continuar");
		}
		try {
			return conCookie(sesiones.renovar(refresco, ip(peticion)));
		} catch (SesionInvalidaException e) {
			return sinSesion(e.getMessage());
		}
	}

	/** Cierra la sesión en el servidor: el refresh token y los tokens de acceso que siguen vigentes dejan de servir. */
	@PostMapping("/auth/salir")
	public ResponseEntity<Void> salir(@CookieValue(name = COOKIE, required = false) String refresco,
			@RequestHeader(name = "X-Requested-With", required = false) String ajax) {
		if (ajax == null || ajax.isBlank()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		sesiones.cerrar(refresco);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
	}

	@GetMapping("/auth/yo")
	public Perfil yo(@RequestHeader("X-Usuario-Id") long idUsuario) {
		return Perfil.de(servicio.yo(idUsuario));
	}

	/** Cambia la contraseña; las demás sesiones abiertas de la persona se cierran (la actual sigue). */
	@PostMapping("/auth/clave")
	public ResponseEntity<Void> cambiarClave(@RequestHeader("X-Usuario-Id") long idUsuario,
			@CookieValue(name = COOKIE, required = false) String refresco, @Valid @RequestBody CambioClave cambio) {
		servicio.cambiarClave(idUsuario, cambio.actual(), cambio.nueva(), refresco);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/.well-known/jwks.json")
	public Map<String, Object> jwks() {
		return emisor.jwks();
	}

	private ResponseEntity<SesionRespuesta> conCookie(Sesion s) {
		Duration vida = s.refrescoExpira() == null ? Duration.ZERO : Duration.between(Instant.now(), s.refrescoExpira());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie(s.refresco() == null ? "" : s.refresco(), vida).toString())
				.body(SesionRespuesta.de(s));
	}

	private ResponseEntity<Map<String, String>> sinSesion(String mensaje) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
				.body(Map.of("error", mensaje));
	}

	private ResponseCookie cookie(String valor, Duration vida) {
		return ResponseCookie.from(COOKIE, valor).httpOnly(true).secure(cookieSegura).sameSite("Strict")
				.path(RUTA_COOKIE).maxAge(vida.isNegative() ? Duration.ZERO : vida).build();
	}

	/** IP del cliente: el gateway agrega X-Forwarded-For. */
	private static String ip(HttpServletRequest p) {
		String reenviada = p.getHeader("X-Forwarded-For");
		return reenviada != null && !reenviada.isBlank() ? reenviada.split(",")[0].strip() : p.getRemoteAddr();
	}

}
