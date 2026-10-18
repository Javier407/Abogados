package co.abogados.identidad.infraestructura;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import co.abogados.identidad.dominio.ConflictoException;
import co.abogados.identidad.dominio.CredencialesInvalidasException;
import co.abogados.identidad.dominio.CuentaBloqueadaException;
import co.abogados.identidad.dominio.RecursoNoEncontradoException;
import co.abogados.identidad.dominio.ReglaNegocioException;

/** Errores de la API de identidad con el cuerpo { "error": mensaje } (y "campos" si falla la validación). */
@RestControllerAdvice
public class ManejadorErrores {

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

	@ExceptionHandler(ConflictoException.class)
	public ResponseEntity<Map<String, Object>> conflicto(ConflictoException e) {
		return error(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(RecursoNoEncontradoException.class)
	public ResponseEntity<Map<String, Object>> noEncontrado(RecursoNoEncontradoException e) {
		return error(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(UsuariosRecurso.SinPermisoException.class)
	public ResponseEntity<Map<String, Object>> sinPermiso(UsuariosRecurso.SinPermisoException e) {
		return error(HttpStatus.FORBIDDEN, e.getMessage());
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<Map<String, Object>> sinIdentidad(MissingRequestHeaderException e) {
		return error(HttpStatus.UNAUTHORIZED, "Falta la identidad del usuario");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException e) {
		return error(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido");
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
