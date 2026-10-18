package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Audiencia de la agenda: una actuación de tipo AUDIENCIA con los datos del proceso.
 * idAsistente es quien la atiende (la persona de la actuación o, si no se indicó, el responsable del proceso).
 */
public record AudienciaVista(
		int idActuacion,
		int idProceso,
		String radicado,
		String radicadoLegible,
		String asunto,
		LocalDate fecha,
		LocalTime hora,
		String descripcion,
		String lugar,
		Long idAsistente) {
}
