package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

/**
 * Término que el sistema propone al llegar una providencia; el abogado decide si lo crea.
 * origen = REGLA (tabla de reglas) o PROVIDENCIA (el plazo está escrito en la decisión, "en 5 días").
 * fechaInicio es la notificación por estado y fechaVencimiento ya está calculada en días hábiles.
 */
public record SugerenciaTermino(
		String descripcion,
		int diasHabiles,
		LocalDate fechaInicio,
		LocalDate fechaVencimiento,
		String norma,
		String origen,
		int prioridad,
		boolean validada,
		String momento) {

	public SugerenciaTermino(String descripcion, int diasHabiles, LocalDate fechaInicio, LocalDate fechaVencimiento,
			String norma, String origen, int prioridad, boolean validada) {
		this(descripcion, diasHabiles, fechaInicio, fechaVencimiento, norma, origen, prioridad, validada, null);
	}
}
