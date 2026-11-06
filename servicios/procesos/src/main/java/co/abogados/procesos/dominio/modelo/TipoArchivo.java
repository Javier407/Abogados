package co.abogados.procesos.dominio.modelo;

import java.util.Locale;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Qué archivos se aceptan en el expediente y cuál es su tipo real. El tipo se decide por el contenido (los primeros
 * bytes) y la extensión debe coincidir: un .pdf que no empieza por %PDF se rechaza. No se aceptan HTML, SVG ni
 * ejecutables, que el navegador podría interpretar.
 */
public final class TipoArchivo {

	private TipoArchivo() {
	}

	/** Tipo MIME del archivo, o ReglaNegocioException si no es de un tipo permitido. */
	public static String detectar(String nombre, byte[] inicio) {
		String ext = extension(nombre);
		String mime = switch (ext) {
			case "pdf" -> empieza(inicio, "%PDF-".getBytes()) ? "application/pdf" : null;
			case "png" -> empieza(inicio, new byte[] { (byte) 0x89, 'P', 'N', 'G' }) ? "image/png" : null;
			case "jpg", "jpeg" -> empieza(inicio, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }) ? "image/jpeg" : null;
			case "docx" -> zip(inicio) ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" : null;
			case "xlsx" -> zip(inicio) ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : null;
			case "txt" -> texto(inicio) ? "text/plain; charset=utf-8" : null;
			default -> throw new ReglaNegocioException(
					"Tipo de archivo no permitido: use PDF, Word (.docx), Excel (.xlsx), imagen (.jpg, .png) o texto (.txt)");
		};
		if (mime == null) {
			throw new ReglaNegocioException("El contenido del archivo no corresponde a su extensión (." + ext + ")");
		}
		return mime;
	}

	static String extension(String nombre) {
		int punto = nombre == null ? -1 : nombre.lastIndexOf('.');
		return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
	}

	private static boolean empieza(byte[] datos, byte[] firma) {
		if (datos.length < firma.length) {
			return false;
		}
		for (int i = 0; i < firma.length; i++) {
			if (datos[i] != firma[i]) {
				return false;
			}
		}
		return true;
	}

	private static boolean zip(byte[] datos) {
		return empieza(datos, new byte[] { 'P', 'K', 3, 4 });
	}

	/** Texto: sin bytes nulos ni caracteres de control (salvo tabulador y saltos de línea), y sin parecer HTML. */
	private static boolean texto(byte[] datos) {
		for (byte b : datos) {
			if (b == 0 || (b >= 0 && b < 32 && b != '\t' && b != '\n' && b != '\r')) {
				return false;
			}
		}
		String inicio = new String(datos, java.nio.charset.StandardCharsets.UTF_8).stripLeading().toLowerCase(Locale.ROOT);
		return !(inicio.startsWith("<!doctype") || inicio.startsWith("<html") || inicio.startsWith("<svg")
				|| inicio.startsWith("<script"));
	}

}
