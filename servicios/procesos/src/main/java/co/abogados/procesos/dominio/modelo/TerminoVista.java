package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

/**
 * Término con la situación de hoy y los datos del proceso para mostrarlo en la agenda. norma: la que fija el plazo;
 * providencia: la decisión del juez que lo abrió (su clase), si se creó desde una.
 */
public record TerminoVista(
		int idTermino,
		int idProceso,
		String radicado,
		String radicadoLegible,
		String rama,
		String asunto,
		Integer idProvidencia,
		String descripcion,
		LocalDate fechaInicio,
		int diasHabiles,
		LocalDate fechaVencimiento,
		String estado,
		long idResponsable,
		int diasHabilesRestantes,
		String situacion,
		String norma,
		String tipoProceso,
		String etapa,
		String providencia) {

	public TerminoVista(int idTermino, int idProceso, String radicado, String radicadoLegible, String rama,
			String asunto, Integer idProvidencia, String descripcion, LocalDate fechaInicio, int diasHabiles,
			LocalDate fechaVencimiento, String estado, long idResponsable, int diasHabilesRestantes, String situacion) {
		this(idTermino, idProceso, radicado, radicadoLegible, rama, asunto, idProvidencia, descripcion, fechaInicio,
				diasHabiles, fechaVencimiento, estado, idResponsable, diasHabilesRestantes, situacion, null, null,
				null, null);
	}
}
