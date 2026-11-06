package co.abogados.identidad.dominio;

/** Usuario o contraseña incorrectos, sin decir cuál de los dos (respuesta 401). */
public class CredencialesInvalidasException extends RuntimeException {

	public CredencialesInvalidasException(String mensaje) {
		super(mensaje);
	}

}
