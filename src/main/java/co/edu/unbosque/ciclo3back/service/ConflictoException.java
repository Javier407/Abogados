package co.edu.unbosque.ciclo3back.service;

/**
 * La operación choca con el estado actual de los datos (llave duplicada,
 * registro con dependencias, etc.). La capa API la traduce a HTTP 409.
 */
public class ConflictoException extends RuntimeException {

	public ConflictoException(String mensaje) {
		super(mensaje);
	}

}
