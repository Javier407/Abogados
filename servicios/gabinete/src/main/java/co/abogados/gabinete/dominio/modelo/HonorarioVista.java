package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Acuerdo de honorarios con lo pagado. saldo = valor fijo - pagado (null si el acuerdo es solo
 * porcentaje: lo que se debe depende del resultado del proceso).
 */
public record HonorarioVista(
		int idHonorario,
		int idProceso,
		int idCliente,
		String cliente,
		Short idTarifa,
		String formaCobro,
		BigDecimal valorFijo,
		BigDecimal porcentaje,
		BigDecimal valorEstimado,
		LocalDate fechaPacto,
		String estado,
		BigDecimal pagado,
		BigDecimal saldo,
		int pagos) {
}
