package co.abogados.procesos.dominio.excepciones;

/** Otro servicio del que depende la operación no responde (respuesta 503). */
public class ServicioNoDisponibleException extends RuntimeException {

	public ServicioNoDisponibleException(String mensaje) {
		super(mensaje);
	}

}
