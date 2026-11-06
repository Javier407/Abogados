package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Código único de la factura electrónica (CUFE) y de la nota crédito (CUDE), según el Anexo Técnico de la DIAN:
 * SHA-384 de NumFac + FecFac + HorFac + ValFac + 01 + ValIva + 04 + ValInc + 03 + ValIca + ValTot + NitEmisor +
 * NumAdquirente + ClaveTécnica (o PIN del software en la nota) + TipoAmbiente (1 producción, 2 pruebas).
 * En producción lo calcula y lo valida el proveedor tecnológico; aquí lo usa el proveedor simulado.
 */
public final class Cufe {

	private static final ZoneOffset COLOMBIA = ZoneOffset.ofHours(-5);
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ssxxx");

	private Cufe() {
	}

	public static String calcular(String numero, OffsetDateTime emitida, BigDecimal subtotal, BigDecimal iva,
			BigDecimal total, String nitEmisor, String documentoAdquirente, String clave, String ambiente) {
		OffsetDateTime enColombia = emitida.withOffsetSameInstant(COLOMBIA);
		String texto = numero + FECHA.format(enColombia) + HORA.format(enColombia) + valor(subtotal)
				+ "01" + valor(iva) + "04" + valor(BigDecimal.ZERO) + "03" + valor(BigDecimal.ZERO)
				+ valor(total) + soloNit(nitEmisor) + documentoAdquirente + clave
				+ ("PRODUCCION".equals(ambiente) ? "1" : "2");
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-384").digest(texto.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** El NIT sin dígito de verificación ni puntos: "901234567-8" → "901234567". */
	public static String soloNit(String nit) {
		String sinDv = nit.contains("-") ? nit.substring(0, nit.indexOf('-')) : nit;
		return sinDv.replaceAll("\\D", "");
	}

	static String valor(BigDecimal v) {
		return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

}
