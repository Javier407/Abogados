package co.abogados.procesos.infraestructura.rest;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.excepciones.ServicioNoDisponibleException;

/**
 * Traduce las excepciones a respuestas HTTP con el cuerpo { "error": mensaje }
 * (y "campos" cuando falla la validación de la petición).
 */
@RestControllerAdvice
public class ManejadorErrores {

	@ExceptionHandler(RecursoNoEncontradoException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, Object> noEncontrado(RecursoNoEncontradoException ex) {
		return error(ex.getMessage());
	}

	@ExceptionHandler(ConflictoException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Map<String, Object> conflicto(ConflictoException ex) {
		return error(ex.getMessage());
	}

	@ExceptionHandler(ReglaNegocioException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
	public Map<String, Object> reglaNegocio(ReglaNegocioException ex) {
		return error(ex.getMessage());
	}

	@ExceptionHandler(ServicioNoDisponibleException.class)
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	public Map<String, Object> noDisponible(ServicioNoDisponibleException ex) {
		return error(ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> validacion(MethodArgumentNotValidException ex) {
		Map<String, String> campos = new LinkedHashMap<>();
		for (FieldError e : ex.getBindingResult().getFieldErrors()) {
			campos.putIfAbsent(e.getField(), e.getDefaultMessage());
		}
		Map<String, Object> cuerpo = error("Hay datos inválidos en la petición");
		cuerpo.put("campos", campos);
		return cuerpo;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> jsonInvalido(HttpMessageNotReadableException ex) {
		return error("El cuerpo de la petición no es válido: revise las fechas (AAAA-MM-DD) y los valores de las listas");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> parametroInvalido(MethodArgumentTypeMismatchException ex) {
		return error("Valor inválido para '" + ex.getName() + "': " + ex.getValue());
	}

	@ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
	@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
	public Map<String, Object> archivoGrande(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
		return error("El archivo pasa del máximo permitido (20 MB)");
	}

	@ExceptionHandler({ org.springframework.web.multipart.support.MissingServletRequestPartException.class,
			org.springframework.web.bind.MissingServletRequestParameterException.class,
			org.springframework.web.multipart.MultipartException.class })
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> faltaArchivo(Exception ex) {
		return error("Adjunte el archivo y elija la categoría");
	}

	private static Map<String, Object> error(String mensaje) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("error", mensaje);
		return cuerpo;
	}

}
