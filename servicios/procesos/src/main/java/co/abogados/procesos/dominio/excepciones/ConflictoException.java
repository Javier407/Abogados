package co.abogados.procesos.dominio.excepciones;

/** La operación choca con datos existentes (respuesta 409). */
public class ConflictoException extends RuntimeException {

	public ConflictoException(String mensaje) {
		super(mensaje);
	}

}
