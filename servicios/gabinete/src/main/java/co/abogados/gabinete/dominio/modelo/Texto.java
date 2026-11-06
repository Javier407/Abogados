package co.abogados.gabinete.dominio.modelo;

final class Texto {

	private Texto() {
	}

	/** Quita espacios sobrantes; una cadena vacía cuenta como no informada. */
	static String limpio(String valor) {
		if (valor == null) {
			return null;
		}
		String limpio = valor.strip().replaceAll("\s+", " ");
		return limpio.isEmpty() ? null : limpio;
	}

}
