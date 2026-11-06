package co.abogados.procesos.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Un archivo del expediente (poder, demanda, prueba, providencia...). El contenido está en el almacén de archivos
 * con un nombre aleatorio (archivo); el nombre original solo se usa al descargarlo.
 * visibleCliente: el abogado decide qué ve el cliente en su portal (por defecto, nada).
 */
public record Documento(
		Integer idDocumento,
		int idProceso,
		UUID archivo,
		String nombre,
		String tipoMime,
		long tamano,
		String sha256,
		Categoria categoria,
		String descripcion,
		boolean visibleCliente,
		long subidoPor,
		Instant subido) {

	public enum Categoria { PODER, DEMANDA, CONTESTACION, MEMORIAL, PRUEBA, PROVIDENCIA, CONTRATO, OTRO }

	public Documento {
		if (categoria == null) {
			throw new ReglaNegocioException("Elija la categoría del documento");
		}
		descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.strip();
		if (descripcion != null && descripcion.length() > 300) {
			throw new ReglaNegocioException("La descripción admite máximo 300 caracteres");
		}
	}

	/**
	 * Nombre seguro para guardar y descargar: sin rutas ni caracteres de control, máximo 200 caracteres
	 * (conserva la extensión).
	 */
	public static String nombreSeguro(String original) {
		String n = original == null ? "" : original;
		n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1);   // sin carpetas
		n = n.replaceAll("[\\p{Cntrl}\"<>:|?*]", "").strip();
		if (n.isEmpty() || n.equals(".") || n.equals("..")) {
			n = "documento";
		}
		if (n.length() > 200) {
			int punto = n.lastIndexOf('.');
			String ext = punto > 0 && n.length() - punto <= 10 ? n.substring(punto) : "";
			n = n.substring(0, 200 - ext.length()) + ext;
		}
		return n;
	}

}
