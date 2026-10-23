package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

/**
 * Factura electrónica de venta (F14). Una vez emitida no se edita: se anula con una nota crédito.
 * - SERVICIO: honorarios u otros servicios; lleva IVA si la firma es responsable de IVA.
 * - REEMBOLSO: gastos que la firma adelantó por el cliente; se cobran sin IVA ni retención (ingresos para terceros).
 * - retencion: la retención en la fuente que el cliente descuenta al pagar (si es agente retenedor). Es informativa:
 *   el total facturado no cambia, cambia lo que se espera recibir (neto).
 * Los datos del cliente se copian al emitir (adquirente) para que la factura no cambie si después cambia su ficha.
 */
public record Factura(
		Integer idFactura,
		String prefijo,
		long numero,
		String resolucion,
		String ambiente,
		int idCliente,
		Integer idProceso,
		OffsetDateTime emitida,
		LocalDate vence,
		FormaPago formaPago,
		Totales totales,
		String observaciones,
		String cufe,
		Estado estado,
		String respuesta,
		long emitidaPor,
		Adquirente adquirente,
		List<Linea> lineas,
		NotaCredito notaCredito) {

	public enum FormaPago { CONTADO, CREDITO }

	public enum Estado { ACEPTADA, RECHAZADA, ANULADA }

	public enum TipoLinea { SERVICIO, REEMBOLSO }

	public String numeroCompleto() {
		return prefijo + numero;
	}

	public Factura conEnvio(Estado nuevo, String codigo, String mensaje) {
		return new Factura(idFactura, prefijo, numero, resolucion, ambiente, idCliente, idProceso, emitida, vence, formaPago,
				totales, observaciones, codigo, nuevo, mensaje, emitidaPor, adquirente, lineas, notaCredito);
	}

	/** Datos del cliente tal como quedan en la factura. documento: "NIT 900456789", "CC 1032456781"… */
	public record Adquirente(String nombre, String tipoDocumento, String numeroDocumento, String direccion, String ciudad,
			String email) {

		public String documento() {
			return tipoDocumento + " " + numeroDocumento;
		}
	}

	public record Linea(int linea, TipoLinea tipo, String descripcion, BigDecimal cantidad, BigDecimal valorUnitario,
			BigDecimal ivaPct, BigDecimal valor, BigDecimal iva, Integer idHonorario, Integer idGasto) {

		/** Calcula el valor (cantidad × unitario) y el IVA de la línea; un reembolso nunca lleva IVA. */
		public static Linea nueva(int linea, TipoLinea tipo, String descripcion, BigDecimal cantidad,
				BigDecimal valorUnitario, BigDecimal ivaPct, Integer idHonorario, Integer idGasto) {
			String texto = Texto.limpio(descripcion);
			if (tipo == null || texto == null) {
				throw new ReglaNegocioException("Cada línea necesita el tipo y la descripción");
			}
			if (texto.length() > 200) {
				throw new ReglaNegocioException("La descripción de la línea " + linea + " pasa de 200 caracteres");
			}
			if (cantidad == null || cantidad.signum() <= 0 || valorUnitario == null || valorUnitario.signum() <= 0) {
				throw new ReglaNegocioException("La línea " + linea + " necesita cantidad y valor mayores que cero");
			}
			BigDecimal pct = tipo == TipoLinea.REEMBOLSO || ivaPct == null ? BigDecimal.ZERO : ivaPct;
			BigDecimal valor = dinero(cantidad.multiply(valorUnitario));
			return new Linea(linea, tipo, texto, cantidad, dinero(valorUnitario), pct, valor, porcentaje(valor, pct),
					idHonorario, idGasto);
		}
	}

	/** Totales: total = subtotal + iva + reembolsos; neto = total - retención (lo que se espera recibir). */
	public record Totales(BigDecimal subtotal, BigDecimal iva, BigDecimal reembolsos, BigDecimal total,
			BigDecimal retencionPct, BigDecimal retencion, BigDecimal neto) {

		/** La retención se calcula sobre los servicios (los reembolsos no son ingreso de la firma). */
		public static Totales de(List<Linea> lineas, BigDecimal retencionPct) {
			if (lineas == null || lineas.isEmpty()) {
				throw new ReglaNegocioException("La factura necesita al menos una línea");
			}
			BigDecimal pct = retencionPct == null ? BigDecimal.ZERO : retencionPct;
			if (pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
				throw new ReglaNegocioException("La retención va de 0 a 100 %");
			}
			BigDecimal subtotal = suma(lineas, TipoLinea.SERVICIO, Linea::valor);
			BigDecimal iva = suma(lineas, TipoLinea.SERVICIO, Linea::iva);
			BigDecimal reembolsos = suma(lineas, TipoLinea.REEMBOLSO, Linea::valor);
			BigDecimal total = subtotal.add(iva).add(reembolsos);
			BigDecimal retencion = porcentaje(subtotal, pct);
			return new Totales(subtotal, iva, reembolsos, total, pct, retencion, total.subtract(retencion));
		}

		private static BigDecimal suma(List<Linea> lineas, TipoLinea tipo, java.util.function.Function<Linea, BigDecimal> campo) {
			return lineas.stream().filter(l -> l.tipo() == tipo).map(campo).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
		}
	}

	public record NotaCredito(Integer idNota, String prefijo, long numero, int idFactura, String motivo, BigDecimal valor,
			OffsetDateTime emitida, long emitidaPor, String cude, Estado estado, String respuesta) {

		public String numeroCompleto() {
			return prefijo + numero;
		}
	}

	static BigDecimal dinero(BigDecimal v) {
		return v.setScale(2, RoundingMode.HALF_UP);
	}

	static BigDecimal porcentaje(BigDecimal base, BigDecimal pct) {
		return dinero(base.multiply(pct).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
	}

}
