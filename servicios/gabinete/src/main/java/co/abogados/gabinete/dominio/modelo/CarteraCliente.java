package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que debe un cliente: saldo de honorarios con total conocido (fijo o liquidado) y gastos que la firma adelantó
 * y aún no le devuelven. diasSinPagar cuenta desde el último pago válido (null si nunca ha pagado).
 */
public record CarteraCliente(
		int idCliente,
		String cliente,
		int acuerdos,
		BigDecimal pactado,
		BigDecimal pagado,
		BigDecimal saldoHonorarios,
		BigDecimal gastosPorReembolsar,
		BigDecimal totalPorCobrar,
		LocalDate ultimoPago,
		Long diasSinPagar) {
}
