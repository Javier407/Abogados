package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

/**
 * Pago del cliente a un acuerdo de honorarios. Un pago nunca se borra: si se registró mal se anula con un
 * motivo (anuladoEn, anuladoPor, motivoAnulacion) y deja de contar para lo pagado y el saldo.
 */
public record Pago(Integer idPago, int idHonorario, LocalDate fecha, BigDecimal valor, MedioPago medio,
		String referencia, Instant anuladoEn, Long anuladoPor, String motivoAnulacion) {

	public Pago {
		if (fecha == null || medio == null) {
			throw new ReglaNegocioException("La fecha y el medio de pago son obligatorios");
		}
		if (valor == null || valor.signum() <= 0) {
			throw new ReglaNegocioException("El valor del pago debe ser mayor que cero");
		}
		referencia = Texto.limpio(referencia);
	}

	public Pago(Integer idPago, int idHonorario, LocalDate fecha, BigDecimal valor, MedioPago medio, String referencia) {
		this(idPago, idHonorario, fecha, valor, medio, referencia, null, null, null);
	}

	public boolean anulado() {
		return anuladoEn != null;
	}

}
