package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Acuerdo de honorarios con lo pagado (sin contar pagos anulados).
 * saldo = total - pagado, donde el total es el valor liquidado si ya se liquidó la parte variable, o el
 * valor fijo en FIJO y MIXTO. Es null si no hay total conocido (porcentaje sin liquidar, o MENSUAL).
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
		int pagos,
		BigDecimal valorBase,
		BigDecimal valorLiquidado,
		LocalDate fechaLiquidacion) {

	public HonorarioVista(int idHonorario, int idProceso, int idCliente, String cliente, Short idTarifa,
			String formaCobro, BigDecimal valorFijo, BigDecimal porcentaje, BigDecimal valorEstimado,
			LocalDate fechaPacto, String estado, BigDecimal pagado, BigDecimal saldo, int pagos) {
		this(idHonorario, idProceso, idCliente, cliente, idTarifa, formaCobro, valorFijo, porcentaje, valorEstimado,
				fechaPacto, estado, pagado, saldo, pagos, null, null, null);
	}

}
