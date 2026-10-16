package co.abogados.identidad.infraestructura;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.dominio.CredencialesInvalidasException;
import co.abogados.identidad.dominio.CuentaBloqueadaException;
import co.abogados.identidad.dominio.RecursoNoEncontradoException;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * API de identidad. /auth/login es pública (la única ruta sin token). /auth/yo y /auth/clave las llama el
 * gateway después de validar el token: identifica a la persona con la cabecera X-Usuario-Id, que el gateway
 * siempre reescribe (este servicio no está publicado fuera de la red interna).
 */
@RestController
public class AuthRecurso {

	public record Ingreso(@NotBlank(message = "Escriba el usuario") @Size(max = 120) String usuario,
			@NotBlank(message = "Escriba la contraseña") @Size(max = 200) String clave) {
	}

	public record CambioClave(@NotBlank(message = "Escriba la contraseña actual") String actual,
			@NotBlank(message = "Escriba la contraseña nueva") String nueva) {
	}

	public record Perfil(long idUsuario, String usuario, String nombre, String email, Set<String> roles) {
		static Perfil de(Usuario u) {
			return new Perfil(u.idUsuario(), u.usuario(), u.nombre(), u.email(), u.roles());
		}
	}

	private final AutenticacionServicio servicio;
	private final EmisorJwtRsa emisor;

	public AuthRecurso(AutenticacionServicio servicio, EmisorJwtRsa emisor) {
		this.servicio = servicio;
		this.emisor = emisor;
	}

	@PostMapping("/auth/login")
	public Sesion login(@Valid @RequestBody Ingreso ingreso, HttpServletRequest peticion) {
		return servicio.iniciarSesion(ingreso.usuario(), ingreso.clave(), ip(peticion));
	}

	@GetMapping("/auth/yo")
	public Perfil yo(@RequestHeader("X-Usuario-Id") long idUsuario) {
		return Perfil.de(servicio.yo(idUsuario));
	}

	@PostMapping("/auth/clave")
	public ResponseEntity<Void> cambiarClave(@RequestHeader("X-Usuario-Id") long idUsuario,
			@Valid @RequestBody CambioClave cambio) {
		servicio.cambiarClave(idUsuario, cambio.actual(), cambio.nueva());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/.well-known/jwks.json")
	public Map<String, Object> jwks() {
		return emisor.jwks();
	}

	/** IP del cliente: el gateway agrega X-Forwarded-For. */
	private static String ip(HttpServletRequest p) {
		String reenviada = p.getHeader("X-Forwarded-For");
		return reenviada != null && !reenviada.isBlank() ? reenviada.split(",")[0].strip() : p.getRemoteAddr();
	}

	// ----------------------------------------------------------------- errores { "error": ... }

	@ExceptionHandler(CredencialesInvalidasException.class)
	public ResponseEntity<Map<String, Object>> credenciales(CredencialesInvalidasException e) {
		return error(HttpStatus.UNAUTHORIZED, e.getMessage());
	}

	@ExceptionHandler(CuentaBloqueadaException.class)
	public ResponseEntity<Map<String, Object>> bloqueada(CuentaBloqueadaException e) {
		return ResponseEntity.status(HttpStatus.LOCKED).header("Retry-After", String.valueOf(e.minutos() * 60))
				.body(Map.of("error", e.getMessage()));
	}

	@ExceptionHandler(ReglaNegocioException.class)
	public ResponseEntity<Map<String, Object>> regla(ReglaNegocioException e) {
		return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}

	@ExceptionHandler(RecursoNoEncontradoException.class)
	public ResponseEntity<Map<String, Object>> noEncontrado(RecursoNoEncontradoException e) {
		return error(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<Map<String, Object>> sinIdentidad(MissingRequestHeaderException e) {
		return error(HttpStatus.UNAUTHORIZED, "Falta la identidad del usuario");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("error", "Hay datos inválidos en la petición");
		Map<String, String> campos = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors().forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
		cuerpo.put("campos", campos);
		return ResponseEntity.badRequest().body(cuerpo);
	}

	private static ResponseEntity<Map<String, Object>> error(HttpStatus estado, String mensaje) {
		return ResponseEntity.status(estado).body(Map.of("error", mensaje));
	}

}
