package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

/**
 * Decisión del juez. fuente = MANUAL (registrada por la firma) o VIGILANCIA (detectada en la Rama Judicial).
 * anticipada: sentencia anticipada (CGP art. 278), dictada sin agotar las etapas del proceso.
 */
public record ProvidenciaVista(int idProvidencia, int idProceso, String tipo, String clase, LocalDate fechaProvidencia,
		LocalDate fechaEstado, String decision, String sentido, String fuente, boolean anticipada) {

	public ProvidenciaVista(int idProvidencia, int idProceso, String tipo, String clase, LocalDate fechaProvidencia,
			LocalDate fechaEstado, String decision, String sentido, String fuente) {
		this(idProvidencia, idProceso, tipo, clase, fechaProvidencia, fechaEstado, decision, sentido, fuente, false);
	}
}
