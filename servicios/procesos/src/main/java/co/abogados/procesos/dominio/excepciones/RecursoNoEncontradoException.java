package co.abogados.procesos.dominio.excepciones;

/** El recurso pedido no existe (respuesta 404). */
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String mensaje) {
		super(mensaje);
	}

}
