package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

/** Decisión del juez. fuente = MANUAL (registrada por la firma) o VIGILANCIA (detectada en la Rama Judicial). */
public record ProvidenciaVista(int idProvidencia, int idProceso, String tipo, String clase, LocalDate fechaProvidencia,
		LocalDate fechaEstado, String decision, String sentido, String fuente) {
}
