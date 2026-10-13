package co.edu.unbosque.ciclo3back.service;

/** Los datos son válidos en forma pero violan una regla del negocio. La capa API la traduce a HTTP 400. */
public class ReglaNegocioException extends RuntimeException {

	public ReglaNegocioException(String mensaje) {
		super(mensaje);
	}

}
