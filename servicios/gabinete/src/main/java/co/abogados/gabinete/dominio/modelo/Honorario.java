package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

/**
 * Acuerdo de honorarios con el cliente para un proceso (uno por proceso).
 * - FIJO y MENSUAL: valor fijo (total o por mes).
 * - CUOTA_LITIS y PORCENTAJE_CUANTIA: porcentaje de lo que se obtenga o de la cuantía.
 * - MIXTO: un valor fijo más un porcentaje.
 */
public record Honorario(
		Integer idHonorario,
		int idProceso,
		int idCliente,
		Short idTarifa,
		FormaCobro formaCobro,
		BigDecimal valorFijo,
		BigDecimal porcentaje,
		BigDecimal valorEstimado,
		LocalDate fechaPacto,
		EstadoHonorario estado) {

	/** Tope usado en la práctica colombiana para la cuota litis (la jurisprudencia la limita a la mitad). */
	public static final BigDecimal PORCENTAJE_MAXIMO = new BigDecimal("50");

	public Honorario {
		if (formaCobro == null) {
			throw new ReglaNegocioException("La forma de cobro es obligatoria");
		}
		if (fechaPacto == null) {
			throw new ReglaNegocioException("La fecha del acuerdo es obligatoria");
		}
		if (estado == null) {
			estado = EstadoHonorario.VIGENTE;
		}
		boolean pideFijo = formaCobro.llevaValorFijo();
		boolean pidePorcentaje = formaCobro.llevaPorcentaje();
		if (pideFijo && (valorFijo == null || valorFijo.signum() <= 0)) {
			throw new ReglaNegocioException("La forma " + formaCobro + " requiere un valor fijo mayor que cero");
		}
		if (!pideFijo && valorFijo != null) {
			throw new ReglaNegocioException("La forma " + formaCobro + " no lleva valor fijo");
		}
		if (pidePorcentaje && (porcentaje == null || porcentaje.signum() <= 0)) {
			throw new ReglaNegocioException("La forma " + formaCobro + " requiere un porcentaje mayor que cero");
		}
		if (!pidePorcentaje && porcentaje != null) {
			throw new ReglaNegocioException("La forma " + formaCobro + " no lleva porcentaje");
		}
		if (porcentaje != null && porcentaje.compareTo(PORCENTAJE_MAXIMO) > 0) {
			throw new ReglaNegocioException("El porcentaje no puede superar el " + PORCENTAJE_MAXIMO + " %");
		}
		if (valorEstimado != null && valorEstimado.signum() < 0) {
			throw new ReglaNegocioException("El valor estimado no puede ser negativo");
		}
	}

	public Honorario conEstado(EstadoHonorario nuevo) {
		return new Honorario(idHonorario, idProceso, idCliente, idTarifa, formaCobro, valorFijo, porcentaje,
				valorEstimado, fechaPacto, nuevo);
	}

}
