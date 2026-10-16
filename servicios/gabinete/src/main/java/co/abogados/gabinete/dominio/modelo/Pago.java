package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

public record Pago(Integer idPago, int idHonorario, LocalDate fecha, BigDecimal valor, MedioPago medio,
		String referencia) {

	public Pago {
		if (fecha == null || medio == null) {
			throw new ReglaNegocioException("La fecha y el medio de pago son obligatorios");
		}
		if (valor == null || valor.signum() <= 0) {
			throw new ReglaNegocioException("El valor del pago debe ser mayor que cero");
		}
		referencia = Texto.limpio(referencia);
	}
}
