package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

/** Término con la situación de hoy y los datos del proceso para mostrarlo en la agenda. */
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
		String situacion) {
}
