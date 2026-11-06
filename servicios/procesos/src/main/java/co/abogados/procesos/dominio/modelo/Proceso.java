package co.abogados.procesos.dominio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Proceso judicial que lleva la firma. Las reglas que solo dependen del propio proceso
 * se comprueban al construirlo; las que dependen de otros datos (catálogos, clientes,
 * abogados) las comprueba ProcesoServicio.
 *
 * idCliente e idResponsable son referencias al servicio gabinete (sin llave foránea).
 */
public record Proceso(
		Integer idProceso,
		Radicado radicado,
		short idTipo,
		Short idDespachoActual,
		int idCliente,
		RolCliente rolCliente,
		String asunto,
		BigDecimal cuantia,
		ClaseCuantia claseCuantia,
		TipoServicio tipoServicio,
		EstadoProceso estado,
		EtapaProceso etapa,
		LocalDate fechaRadicacion,
		boolean vigilado,
		long idResponsable) {

	public Proceso {
		requerido(radicado, "radicado");
		requerido(rolCliente, "rolCliente");
		requerido(claseCuantia, "claseCuantia");
		requerido(tipoServicio, "tipoServicio");
		requerido(etapa, "etapa");
		requerido(fechaRadicacion, "fechaRadicacion");
		if (asunto == null || asunto.isBlank()) {
			throw new ReglaNegocioException("El asunto del proceso es obligatorio");
		}
		asunto = asunto.strip();
		if (estado == null) {
			estado = EstadoProceso.ACTIVO;
		}
		if (estado.esCerrado() && etapa != EtapaProceso.CERRADO) {
			throw new ReglaNegocioException("Un proceso " + estado + " debe quedar en la etapa CERRADO");
		}
		if (!estado.esCerrado() && etapa == EtapaProceso.CERRADO) {
			throw new ReglaNegocioException("La etapa CERRADO solo aplica a procesos terminados o archivados");
		}
		if (cuantia != null && cuantia.signum() < 0) {
			throw new ReglaNegocioException("La cuantía no puede ser negativa");
		}
		if (claseCuantia == ClaseCuantia.SIN_CUANTIA && cuantia != null) {
			throw new ReglaNegocioException("Un proceso sin cuantía no puede tener valor de cuantía");
		}
		// En segunda instancia el radicado conserva el año original; la fecha es la de llegada al tribunal.
		if (radicado.instancia() == 0 && radicado.anio() != fechaRadicacion.getYear()) {
			throw new ReglaNegocioException("El año del radicado (" + radicado.anio()
					+ ") no coincide con el año de radicación (" + fechaRadicacion.getYear() + ")");
		}
		if (idCliente <= 0) {
			throw new ReglaNegocioException("El cliente es obligatorio");
		}
		if (idResponsable <= 0) {
			throw new ReglaNegocioException("El abogado responsable es obligatorio");
		}
	}

	public Proceso conId(int id) {
		return new Proceso(id, radicado, idTipo, idDespachoActual, idCliente, rolCliente, asunto, cuantia,
				claseCuantia, tipoServicio, estado, etapa, fechaRadicacion, vigilado, idResponsable);
	}

	private static void requerido(Object valor, String campo) {
		if (valor == null) {
			throw new ReglaNegocioException("El campo '" + campo + "' es obligatorio");
		}
	}

}
