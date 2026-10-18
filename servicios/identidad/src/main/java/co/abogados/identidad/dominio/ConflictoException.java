package co.abogados.identidad.dominio;

/** Ya existe una cuenta con el mismo documento, usuario o correo (respuesta 409). */
public class ConflictoException extends RuntimeException {

	public ConflictoException(String mensaje) {
		super(mensaje);
	}

}
