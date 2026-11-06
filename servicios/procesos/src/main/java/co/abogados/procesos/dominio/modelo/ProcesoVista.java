package co.abogados.procesos.dominio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Proceso con los nombres de su rama, tipo y despachos, tal como lo muestra la interfaz. */
public record ProcesoVista(
		int idProceso,
		String radicado,
		String radicadoLegible,
		short idTipo,
		String tipoProceso,
		String codigoRama,
		String rama,
		String despachoOrigen,
		Short idDespachoActual,
		String despachoActual,
		int instancia,
		int idCliente,
		String rolCliente,
		String asunto,
		BigDecimal cuantia,
		String claseCuantia,
		String tipoServicio,
		String estado,
		String etapa,
		LocalDate fechaRadicacion,
		boolean vigilado,
		Long idResponsable) {
}
