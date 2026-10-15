package co.abogados.gabinete.dominio.excepciones;

/** Una regla del negocio no se cumple (respuesta 422). */
public class ReglaNegocioException extends RuntimeException {

	public ReglaNegocioException(String mensaje) {
		super(mensaje);
	}

}
