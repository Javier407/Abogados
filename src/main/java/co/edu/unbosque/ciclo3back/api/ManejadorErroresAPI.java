package co.edu.unbosque.ciclo3back.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import co.edu.unbosque.ciclo3back.service.ConflictoException;
import co.edu.unbosque.ciclo3back.service.RecursoNoEncontradoException;
import co.edu.unbosque.ciclo3back.service.ReglaNegocioException;

/**
 * Traduce las excepciones de la capa de negocio a respuestas HTTP.
 * Así los controladores quedan limpios: solo llaman al servicio y devuelven
 * el resultado. Los errores de negocio se devuelven como { "error": mensaje }
 * y los de Bean Validation como { campo: mensaje }.
 */
@RestControllerAdvice
public class ManejadorErroresAPI {

	@ExceptionHandler(RecursoNoEncontradoException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, String> noEncontrado(RecursoNoEncontradoException ex) {
		return error(ex.getMessage());
	}

	@ExceptionHandler(ConflictoException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Map<String, String> conflicto(ConflictoException ex) {
		return error(ex.getMessage());
	}

	@ExceptionHandler(ReglaNegocioException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> reglaNegocio(ReglaNegocioException ex) {
		return error(ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> validacion(MethodArgumentNotValidException ex) {
		Map<String, String> errores = new HashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errores.put(error.getField(), error.getDefaultMessage());
		}
		return errores;
	}

	/** JSON mal formado, fecha con formato incorrecto o estado que no existe en EstadoAsunto. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> jsonInvalido(HttpMessageNotReadableException ex) {
		return error("El cuerpo de la petición no es válido: revise el formato de fechas (AAAA-MM-DD) y el estado");
	}

	/** Parámetro de ruta con tipo incorrecto, p. ej. /asuntos/estado/CERRADO o /clientes/abc. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> parametroInvalido(MethodArgumentTypeMismatchException ex) {
		return error("Valor inválido para '" + ex.getName() + "': " + ex.getValue());
	}

	private Map<String, String> error(String mensaje) {
		return Collections.singletonMap("error", mensaje);
	}

}
