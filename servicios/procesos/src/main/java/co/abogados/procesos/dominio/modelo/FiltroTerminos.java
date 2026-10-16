package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

/** Criterios opcionales (null = sin filtrar). desde/hasta filtran por fecha de vencimiento. */
public record FiltroTerminos(SituacionTermino situacion, Long idResponsable, Integer idProceso, LocalDate desde,
		LocalDate hasta) {
}
