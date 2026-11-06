package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

/**
 * Gasto de un proceso (copias, notificaciones, honorarios del perito, pólizas...).
 * Si lo adelantó la FIRMA y es reembolsable, el cliente debe devolverlo: queda "por reembolsar" en la cartera
 * hasta que se marque reembolsado. Un gasto que pagó el CLIENTE solo se registra para el expediente.
 */
public record Gasto(
		Integer idGasto,
		int idProceso,
		int idCliente,
		LocalDate fecha,
		String concepto,
		BigDecimal valor,
		PagadoPor pagadoPor,
		boolean reembolsable,
		LocalDate reembolsadoEn,
		String soporteUrl,
		Long registradoPor) {

	public enum PagadoPor { FIRMA, CLIENTE }

	public Gasto {
		concepto = Texto.limpio(concepto);
		if (concepto == null) {
			throw new ReglaNegocioException("Describa el gasto");
		}
		if (concepto.length() > 150) {
			throw new ReglaNegocioException("El concepto admite máximo 150 caracteres");
		}
		if (fecha == null) {
			throw new ReglaNegocioException("La fecha del gasto es obligatoria");
		}
		if (valor == null || valor.signum() <= 0) {
			throw new ReglaNegocioException("El valor del gasto debe ser mayor que cero");
		}
		if (pagadoPor == null) {
			throw new ReglaNegocioException("Indique si lo pagó la firma o el cliente");
		}
		if (reembolsable && pagadoPor != PagadoPor.FIRMA) {
			throw new ReglaNegocioException("Solo se reembolsa lo que adelantó la firma");
		}
		soporteUrl = Texto.limpio(soporteUrl);
		if (soporteUrl != null && !soporteUrl.matches("https?://\\S{3,290}")) {
			throw new ReglaNegocioException("El enlace del soporte debe empezar por http:// o https://");
		}
	}

	public boolean porReembolsar() {
		return reembolsable && reembolsadoEn == null;
	}

}
