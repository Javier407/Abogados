package co.edu.unbosque.ciclo3back.service;

/** El recurso solicitado no existe en la BD. La capa API la traduce a HTTP 404. */
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String mensaje) {
		super(mensaje);
	}

}
