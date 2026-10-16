package co.abogados.identidad.dominio;

/** El recurso pedido no existe (respuesta 404). */
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String mensaje) {
		super(mensaje);
	}

}
