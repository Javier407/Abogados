package co.abogados.identidad.dominio;

/** El refresh token no existe, venció, fue revocado o ya se había usado (respuesta 401: hay que entrar de nuevo). */
public class SesionInvalidaException extends RuntimeException {

	public SesionInvalidaException(String mensaje) {
		super(mensaje);
	}

}
