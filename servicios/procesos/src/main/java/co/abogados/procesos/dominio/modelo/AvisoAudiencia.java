package co.abogados.procesos.dominio.modelo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Evento audiencia.proxima: la audiencia es el próximo día hábil (contrato: docs/contratos/eventos.md). */
public record AvisoAudiencia(
		int idActuacion,
		int idProceso,
		String radicado,
		String descripcion,
		LocalDate fecha,
		LocalTime hora,
		String lugar,
		long idAbogado,
		Instant ocurrido) {
}
