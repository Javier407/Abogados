package co.abogados.identidad.dominio;

/** La contraseña nueva no cumple una regla (respuesta 422). */
public class ReglaNegocioException extends RuntimeException {

	public ReglaNegocioException(String mensaje) {
		super(mensaje);
	}

}
