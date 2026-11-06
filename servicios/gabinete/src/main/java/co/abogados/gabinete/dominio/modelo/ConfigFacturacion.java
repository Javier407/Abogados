package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

/**
 * Configuración de la facturación (F14).
 * - IVA y retenciones: de referencia hasta que el contador las valide (validadaPor / validadaEn). Cambiar alguno
 *   de esos valores la deja otra vez sin validar.
 * - Resolución de numeración de la DIAN: prefijo, rango autorizado y vigencia. siguiente = el próximo número.
 * - ambiente PRUEBAS mientras la firma se habilita; en PRODUCCION no se acepta el proveedor SIMULADO.
 */
public record ConfigFacturacion(
		boolean responsableIva,
		BigDecimal tarifaIva,
		BigDecimal retencionJuridica,
		BigDecimal retencionNatural,
		int diasCredito,
		String resolucion,
		String prefijo,
		long rangoDesde,
		long rangoHasta,
		long siguiente,
		LocalDate vigenteDesde,
		LocalDate vigenteHasta,
		String prefijoNota,
		long siguienteNota,
		String ambiente,
		String proveedor,
		Long validadaPor,
		Instant validadaEn) {

	public static final String SIMULADO = "SIMULADO";

	/** Revisa que la configuración sea coherente (al guardarla). */
	public void validar() {
		for (BigDecimal pct : new BigDecimal[] { tarifaIva, retencionJuridica, retencionNatural }) {
			if (pct == null || pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
				throw new ReglaNegocioException("El IVA y las retenciones van de 0 a 100 %");
			}
		}
		if (diasCredito < 0 || diasCredito > 365) {
			throw new ReglaNegocioException("Los días de crédito van de 0 a 365");
		}
		if (vacio(resolucion) || vacio(prefijo) || !prefijo.matches("[A-Z0-9]{1,4}") || vacio(prefijoNota)
				|| !prefijoNota.matches("[A-Z0-9]{1,4}")) {
			throw new ReglaNegocioException("Escriba el número de resolución y prefijos de hasta 4 letras o números");
		}
		if (rangoDesde <= 0 || rangoHasta < rangoDesde || siguiente < rangoDesde || siguiente > rangoHasta + 1) {
			throw new ReglaNegocioException("El rango autorizado no es válido o el siguiente número está fuera de él");
		}
		if (vigenteDesde == null || vigenteHasta == null || vigenteHasta.isBefore(vigenteDesde)) {
			throw new ReglaNegocioException("Revise las fechas de vigencia de la resolución");
		}
		if (!"PRUEBAS".equals(ambiente) && !"PRODUCCION".equals(ambiente)) {
			throw new ReglaNegocioException("El ambiente es PRUEBAS o PRODUCCION");
		}
		if ("PRODUCCION".equals(ambiente) && SIMULADO.equals(proveedor)) {
			throw new ReglaNegocioException("En producción hace falta un proveedor tecnológico real, no el simulado");
		}
	}

	/** ¿Cambió algo de lo que debe revisar el contador? */
	public boolean cambiaImpuestos(ConfigFacturacion otra) {
		return responsableIva != otra.responsableIva || tarifaIva.compareTo(otra.tarifaIva) != 0
				|| retencionJuridica.compareTo(otra.retencionJuridica) != 0
				|| retencionNatural.compareTo(otra.retencionNatural) != 0;
	}

	/** IVA de un servicio: la tarifa si la firma es responsable de IVA; si no, cero. */
	public BigDecimal ivaServicios() {
		return responsableIva ? tarifaIva : BigDecimal.ZERO;
	}

	public BigDecimal retencionSugerida(TipoPersona cliente) {
		return cliente == TipoPersona.JURIDICA ? retencionJuridica : retencionNatural;
	}

	public long restantes() {
		return Math.max(0, rangoHasta - siguiente + 1);
	}

	/** Por qué no se puede facturar hoy (null si se puede). */
	public String impedimento(LocalDate hoy) {
		if (hoy.isBefore(vigenteDesde) || hoy.isAfter(vigenteHasta)) {
			return "La resolución de numeración no está vigente (" + vigenteDesde + " a " + vigenteHasta + "). Actualícela.";
		}
		if (restantes() == 0) {
			return "Se acabó el rango de numeración autorizado. Pida una nueva resolución a la DIAN.";
		}
		return null;
	}

	public ConfigFacturacion conValidacion(Long quien, Instant cuando) {
		return new ConfigFacturacion(responsableIva, tarifaIva, retencionJuridica, retencionNatural, diasCredito, resolucion,
				prefijo, rangoDesde, rangoHasta, siguiente, vigenteDesde, vigenteHasta, prefijoNota, siguienteNota, ambiente,
				proveedor, quien, cuando);
	}

	public boolean validada() {
		return validadaPor != null;
	}

	private static boolean vacio(String s) {
		return s == null || s.isBlank();
	}

}
