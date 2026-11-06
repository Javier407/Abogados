package co.abogados.procesos.dominio.modelo;

import java.text.Normalizer;

/** Comparación de textos escritos por personas: sin tildes, mayúsculas, puntuación ni espacios repetidos. */
public final class Texto {

	private Texto() {
	}

	/** "Constructora  Altamira S.A.S." → "constructora altamira sas"; "Admite la DEMANDA" → "admite la demanda". */
	public static String normalizar(String texto) {
		if (texto == null) {
			return "";
		}
		String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		// La puntuación se quita (no se cambia por espacio) para que "S.A.S." y "SAS" coincidan.
		return sinTildes.toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", " ").strip();
	}

}
