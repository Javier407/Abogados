package co.abogados.identidad.dominio;

/**
 * La contraseña es correcta pero falta el segundo paso (F13). desafio: comprobante firmado y de corta vida que se
 * presenta junto con el código. configurar = la cuenta debe activar la verificación en dos pasos antes de entrar.
 */
public class MfaRequeridoException extends RuntimeException {

	private final String desafio;
	private final boolean configurar;

	public MfaRequeridoException(String desafio, boolean configurar) {
		super(configurar ? "Active la verificación en dos pasos para continuar" : "Escriba el código de su app autenticadora");
		this.desafio = desafio;
		this.configurar = configurar;
	}

	public String desafio() {
		return desafio;
	}

	public boolean configurar() {
		return configurar;
	}

}
