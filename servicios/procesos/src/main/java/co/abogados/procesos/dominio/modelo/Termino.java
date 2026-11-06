package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Plazo que corre contra la firma dentro de un proceso (contestar, recurrir, aportar...).
 * fechaInicio es la notificación (normalmente el estado electrónico); vence al cumplirse
 * diasHabiles días hábiles contados desde el día hábil siguiente.
 */
public record Termino(
		Integer idTermino,
		int idProceso,
		Integer idProvidencia,
		String descripcion,
		LocalDate fechaInicio,
		int diasHabiles,
		LocalDate fechaVencimiento,
		EstadoTermino estado,
		long idResponsable) {

	public Termino {
		if (descripcion == null || descripcion.isBlank()) {
			throw new ReglaNegocioException("La descripción del término es obligatoria");
		}
		descripcion = descripcion.strip();
		if (diasHabiles < 1 || diasHabiles > 365) {
			throw new ReglaNegocioException("El término debe tener entre 1 y 365 días hábiles");
		}
		if (fechaInicio == null || fechaVencimiento == null || !fechaVencimiento.isAfter(fechaInicio)) {
			throw new ReglaNegocioException("El vencimiento debe ser posterior a la fecha de notificación");
		}
		if (estado == null) {
			estado = EstadoTermino.PENDIENTE;
		}
	}

}
