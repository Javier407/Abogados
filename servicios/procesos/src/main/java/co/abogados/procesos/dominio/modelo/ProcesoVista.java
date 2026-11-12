package co.abogados.procesos.dominio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Proceso con los nombres de su rama, tipo y despachos, tal como lo muestra la interfaz. Los datos de cierre solo
 * vienen en un proceso cerrado con la acción «Cerrar proceso» (los cerrados antes no los tienen).
 */
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
		Long idResponsable,
		LocalDate fechaCierre,
		String motivoCierre,
		String notaCierre,
		String etapaAntesCierre) {

	/** Proceso sin datos de cierre. */
	public ProcesoVista(int idProceso, String radicado, String radicadoLegible, short idTipo, String tipoProceso,
			String codigoRama, String rama, String despachoOrigen, Short idDespachoActual, String despachoActual,
			int instancia, int idCliente, String rolCliente, String asunto, BigDecimal cuantia, String claseCuantia,
			String tipoServicio, String estado, String etapa, LocalDate fechaRadicacion, boolean vigilado,
			Long idResponsable) {
		this(idProceso, radicado, radicadoLegible, idTipo, tipoProceso, codigoRama, rama, despachoOrigen,
				idDespachoActual, despachoActual, instancia, idCliente, rolCliente, asunto, cuantia, claseCuantia,
				tipoServicio, estado, etapa, fechaRadicacion, vigilado, idResponsable, null, null, null, null);
	}
}
