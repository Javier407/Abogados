package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

/**
 * Liquidación de la parte variable de un acuerdo cuando termina el proceso: porcentaje × base (lo obtenido por
 * el cliente en la cuota litis, o la cuantía en PORCENTAJE_CUANTIA), más el valor fijo si es MIXTO.
 * Se redondea al peso.
 */
public final class Liquidacion {

	private Liquidacion() {
	}

	public static BigDecimal calcular(Honorario h, BigDecimal base) {
		if (!h.formaCobro().llevaPorcentaje()) {
			throw new ReglaNegocioException("Solo se liquidan los acuerdos con porcentaje (cuota litis, porcentaje de la cuantía o mixto)");
		}
		if (base == null || base.signum() < 0) {
			throw new ReglaNegocioException("El valor obtenido no puede ser negativo");
		}
		BigDecimal variable = base.multiply(h.porcentaje()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
		return h.formaCobro() == FormaCobro.MIXTO ? variable.add(h.valorFijo()) : variable;
	}

}
