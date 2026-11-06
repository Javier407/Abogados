package co.abogados.procesos.dominio.modelo;

import java.util.Set;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Persona que interviene en el proceso: el cliente de la firma (esCliente) o la contraparte y los terceros.
 * La parte que es el cliente la crea el propio proceso; las demás se agregan en el expediente.
 */
public record Parte(
		Integer idParte,
		int idProceso,
		RolParte rol,
		String tipoDocumento,
		String numeroDocumento,
		String nombre,
		boolean esCliente,
		String apoderado) {

	private static final Set<String> TIPOS_DOCUMENTO = Set.of("CC", "CE", "NIT", "PAS");

	public Parte {
		if (rol == null) {
			throw new ReglaNegocioException("Indique el rol de la parte");
		}
		if (nombre == null || nombre.isBlank()) {
			throw new ReglaNegocioException("El nombre de la parte es obligatorio");
		}
		nombre = nombre.strip().replaceAll("\\s+", " ");
		if (nombre.length() > 150) {
			throw new ReglaNegocioException("El nombre de la parte admite máximo 150 caracteres");
		}
		numeroDocumento = vacioANulo(numeroDocumento);
		tipoDocumento = vacioANulo(tipoDocumento);
		if ((numeroDocumento == null) != (tipoDocumento == null)) {
			throw new ReglaNegocioException("Indique el tipo y el número de documento, o ninguno de los dos");
		}
		if (tipoDocumento != null) {
			if (!TIPOS_DOCUMENTO.contains(tipoDocumento)) {
				throw new ReglaNegocioException("Tipo de documento inválido: use CC, CE, NIT o PAS");
			}
			String patron = "PAS".equals(tipoDocumento) ? "[A-Za-z0-9]{5,15}" : "\\d{5,15}";
			if (!numeroDocumento.matches(patron)) {
				throw new ReglaNegocioException("El número de documento debe tener entre 5 y 15 "
						+ ("PAS".equals(tipoDocumento) ? "letras o dígitos" : "dígitos, sin puntos ni guiones"));
			}
		}
		apoderado = vacioANulo(apoderado);
		if (apoderado != null && apoderado.length() > 120) {
			throw new ReglaNegocioException("El nombre del apoderado admite máximo 120 caracteres");
		}
	}

	private static String vacioANulo(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

}
