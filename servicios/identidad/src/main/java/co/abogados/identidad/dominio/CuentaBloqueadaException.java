package co.abogados.identidad.dominio;

/** Demasiados intentos fallidos: la cuenta está bloqueada un tiempo (respuesta 423). */
public class CuentaBloqueadaException extends RuntimeException {

	private final long minutos;

	public CuentaBloqueadaException(long minutos) {
		super("Demasiados intentos fallidos. Intente de nuevo en " + minutos + " minuto(s).");
		this.minutos = minutos;
	}

	public long minutos() {
		return minutos;
	}

}
