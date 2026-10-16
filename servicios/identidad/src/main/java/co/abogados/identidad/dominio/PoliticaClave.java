package co.abogados.identidad.dominio;

import java.util.Locale;

/** Reglas para una contraseña nueva. */
public final class PoliticaClave {

	public static final int LARGO_MINIMO = 10;

	private PoliticaClave() {
	}

	public static void validar(String nueva, Usuario usuario) {
		if (nueva == null || nueva.length() < LARGO_MINIMO) {
			throw new ReglaNegocioException("La contraseña debe tener al menos " + LARGO_MINIMO + " caracteres");
		}
		if (nueva.length() > 72) {
			// BCrypt solo usa los primeros 72 bytes: más largo daría una falsa sensación de seguridad.
			throw new ReglaNegocioException("La contraseña puede tener como máximo 72 caracteres");
		}
		if (!nueva.matches(".*\\p{L}.*") || !nueva.matches(".*\\d.*")) {
			throw new ReglaNegocioException("La contraseña debe combinar letras y números");
		}
		String minuscula = nueva.toLowerCase(Locale.ROOT);
		if (minuscula.contains(usuario.usuario().toLowerCase(Locale.ROOT))
				|| minuscula.contains(String.valueOf(usuario.idUsuario()))) {
			throw new ReglaNegocioException("La contraseña no puede contener el usuario ni el documento");
		}
		if (minuscula.equals("prueba2026*")) {
			throw new ReglaNegocioException("No use la contraseña de la base de pruebas");
		}
	}

}
