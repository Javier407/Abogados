package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Prueba del proceso. Solo la PERICIAL lleva perito. fechaEntrega es cuándo debe aportarse (por ejemplo, el
 * dictamen); si la hay, la controla un término (idTermino) que avisa como cualquier otro.
 */
public record Prueba(
		Integer idPrueba,
		int idProceso,
		Tipo tipo,
		String descripcion,
		Integer idPerito,
		Estado estado,
		LocalDate fechaEntrega,
		Integer idTermino) {

	public enum Tipo { DOCUMENTAL, PERICIAL, TESTIMONIAL, INTERROGATORIO, INSPECCION, VIDEO, ANEXO }

	/** SOLICITADA por la firma → DECRETADA (o NEGADA) por el juez → PRACTICADA. */
	public enum Estado { SOLICITADA, DECRETADA, PRACTICADA, NEGADA }

	public Prueba {
		if (tipo == null) {
			throw new ReglaNegocioException("Indique el tipo de prueba");
		}
		if (descripcion == null || descripcion.isBlank()) {
			throw new ReglaNegocioException("Describa la prueba");
		}
		descripcion = descripcion.strip();
		if (descripcion.length() > 200) {
			throw new ReglaNegocioException("La descripción admite máximo 200 caracteres");
		}
		if (idPerito != null && tipo != Tipo.PERICIAL) {
			throw new ReglaNegocioException("Solo una prueba pericial lleva perito");
		}
		if (estado == null) {
			estado = Estado.SOLICITADA;
		}
	}

	/** Aún debe hacerse algo con ella (no está practicada ni negada). */
	public boolean pendiente() {
		return estado == Estado.SOLICITADA || estado == Estado.DECRETADA;
	}

}
