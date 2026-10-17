package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Paso del proceso que da la firma: poder, demanda, contestación, memorial, recurso, audiencia...
 * Si la fecha es futura, la actuación está programada (por ejemplo, una audiencia).
 * Un RECURSO siempre se interpone contra una providencia del mismo proceso (idProvidencia).
 */
public record Actuacion(
		Integer idActuacion,
		int idProceso,
		LocalDate fecha,
		TipoActuacion tipo,
		String descripcion,
		Long idPersona,
		Integer idProvidencia,
		String documentoUrl,
		Long registradoPor) {

	public Actuacion {
		if (tipo == null) {
			throw new ReglaNegocioException("Indique el tipo de actuación");
		}
		if (fecha == null) {
			throw new ReglaNegocioException("La fecha de la actuación es obligatoria");
		}
		if (descripcion == null || descripcion.isBlank()) {
			throw new ReglaNegocioException("Describa la actuación");
		}
		descripcion = descripcion.strip();
		if (descripcion.length() > 200) {
			throw new ReglaNegocioException("La descripción admite máximo 200 caracteres");
		}
		if (tipo == TipoActuacion.RECURSO && idProvidencia == null) {
			throw new ReglaNegocioException("Un recurso se interpone contra una providencia: elija cuál");
		}
		documentoUrl = documentoUrl == null || documentoUrl.isBlank() ? null : documentoUrl.strip();
		if (documentoUrl != null && !documentoUrl.matches("https?://\\S{3,290}")) {
			throw new ReglaNegocioException("El enlace del documento debe empezar por http:// o https://");
		}
	}

}
