package co.abogados.gabinete.dominio.modelo;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

/** Motivo para anular un pago: obligatorio y explicativo, porque queda como rastro en el historial. */
public record Anulacion(String motivo) {

	public Anulacion {
		motivo = Texto.limpio(motivo);
		if (motivo == null || motivo.length() < 5) {
			throw new ReglaNegocioException("Escriba el motivo de la anulación (mínimo 5 caracteres)");
		}
		if (motivo.length() > 200) {
			throw new ReglaNegocioException("El motivo admite máximo 200 caracteres");
		}
	}

}
