package co.abogados.procesos.dominio.excepciones;

/** El usuario no tiene el rol que pide la operación (respuesta 403). El gateway ya lo filtra; esto es la segunda barrera. */
public class AccesoDenegadoException extends RuntimeException {

	public AccesoDenegadoException(String mensaje) {
		super(mensaje);
	}

}
