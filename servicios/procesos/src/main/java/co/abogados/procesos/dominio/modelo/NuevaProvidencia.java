package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;
import java.util.Set;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Auto o sentencia que registra la firma a mano (procesos sin vigilancia o fuente MANUAL).
 * Una sentencia debe decir su sentido para el cliente; un auto no lo lleva.
 */
public record NuevaProvidencia(
		String tipo,
		String clase,
		LocalDate fechaProvidencia,
		LocalDate fechaEstado,
		String decision,
		String sentido) {

	private static final Set<String> SENTIDOS = Set.of("FAVORABLE", "DESFAVORABLE", "PARCIAL");

	public NuevaProvidencia {
		if (!"AUTO".equals(tipo) && !"SENTENCIA".equals(tipo)) {
			throw new ReglaNegocioException("El tipo de providencia debe ser AUTO o SENTENCIA");
		}
		if (clase == null || clase.isBlank()) {
			throw new ReglaNegocioException("Indique qué decidió el juez (por ejemplo, Admite demanda)");
		}
		clase = clase.strip();
		if (clase.length() > 120) {
			throw new ReglaNegocioException("La clase de la providencia admite máximo 120 caracteres");
		}
		if (fechaProvidencia == null || fechaEstado == null) {
			throw new ReglaNegocioException("Las fechas de la providencia y del estado son obligatorias");
		}
		if (fechaEstado.isBefore(fechaProvidencia)) {
			throw new ReglaNegocioException("La notificación por estado no puede ser anterior a la providencia");
		}
		decision = decision == null || decision.isBlank() ? null : decision.strip();
		if (decision != null && decision.length() > 300) {
			throw new ReglaNegocioException("La decisión admite máximo 300 caracteres");
		}
		sentido = sentido == null || sentido.isBlank() ? null : sentido;
		if ("SENTENCIA".equals(tipo) && sentido == null) {
			throw new ReglaNegocioException("Indique si la sentencia fue favorable, desfavorable o parcial");
		}
		if ("AUTO".equals(tipo) && sentido != null) {
			throw new ReglaNegocioException("Solo las sentencias tienen sentido favorable o desfavorable");
		}
		if (sentido != null && !SENTIDOS.contains(sentido)) {
			throw new ReglaNegocioException("Sentido inválido: use FAVORABLE, DESFAVORABLE o PARCIAL");
		}
	}

	public boolean esSentencia() {
		return "SENTENCIA".equals(tipo);
	}

}
