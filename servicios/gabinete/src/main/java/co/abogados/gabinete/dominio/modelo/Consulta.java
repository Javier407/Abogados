package co.abogados.gabinete.dominio.modelo;

import java.text.Normalizer;
import java.time.LocalDate;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

/**
 * Consulta inicial (F10): alguien llega a la firma con un caso antes de ser cliente. Se registra quién consulta,
 * contra quién sería el asunto y el resultado: si contrata, se crea (o se enlaza) su ficha de cliente.
 */
public record Consulta(
		Integer idConsulta,
		LocalDate fecha,
		String nombre,
		TipoDocumento tipoDocumento,
		String numeroDocumento,
		String email,
		String telefono,
		String contraparte,
		String contraparteDocumento,
		String codigoRama,
		String asunto,
		String descripcion,
		Long idAbogado,
		Estado estado,
		String motivoCierre,
		Integer idCliente,
		Long registradaPor) {

	public enum Estado { ABIERTA, CONTRATADA, NO_CONTRATADA, REMITIDA }

	public Consulta {
		nombre = Texto.limpio(nombre);
		asunto = Texto.limpio(asunto);
		if (nombre == null || nombre.length() > 150) {
			throw new ReglaNegocioException("El nombre de quien consulta es obligatorio (máximo 150 caracteres)");
		}
		if (asunto == null || asunto.length() > 300) {
			throw new ReglaNegocioException("Describa el asunto en una frase (máximo 300 caracteres)");
		}
		numeroDocumento = Texto.limpio(numeroDocumento);
		if ((tipoDocumento == null) != (numeroDocumento == null)) {
			throw new ReglaNegocioException("Indique el tipo y el número de documento, o ninguno de los dos");
		}
		if (numeroDocumento != null && !numeroDocumento.matches(
				tipoDocumento == TipoDocumento.PAS ? "[A-Za-z0-9]{5,15}" : "\\d{5,15}")) {
			throw new ReglaNegocioException("El documento debe tener entre 5 y 15 dígitos, sin puntos");
		}
		email = Texto.limpio(email);
		if (email != null && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			throw new ReglaNegocioException("El correo no es válido");
		}
		telefono = Texto.limpio(telefono);
		contraparte = Texto.limpio(contraparte);
		contraparteDocumento = Texto.limpio(contraparteDocumento);
		codigoRama = Texto.limpio(codigoRama);
		descripcion = Texto.limpio(descripcion);
		if (descripcion != null && descripcion.length() > 2000) {
			throw new ReglaNegocioException("La descripción admite máximo 2000 caracteres");
		}
		estado = estado == null ? Estado.ABIERTA : estado;
		motivoCierre = Texto.limpio(motivoCierre);
	}

	/** Persona que consulta como dato de cliente: jurídica si se identifica con NIT. */
	public TipoPersona tipoPersona() {
		return tipoDocumento == TipoDocumento.NIT ? TipoPersona.JURIDICA : TipoPersona.NATURAL;
	}

	/** Para comparar nombres escritos por personas: "Constructora Altamira S.A.S." = "constructora altamira sas". */
	public static String normalizar(String texto) {
		if (texto == null) {
			return "";
		}
		String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sinTildes.toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", " ").strip();
	}

}
