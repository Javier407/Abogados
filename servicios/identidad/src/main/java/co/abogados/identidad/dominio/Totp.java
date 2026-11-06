package co.abogados.identidad.dominio;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Códigos de un solo uso por tiempo (TOTP, RFC 6238), los de Google Authenticator, Microsoft Authenticator o Authy:
 * HMAC-SHA1, 6 dígitos, intervalos de 30 segundos. Se acepta el intervalo anterior y el siguiente por si el reloj
 * del teléfono está algo corrido.
 */
public final class Totp {

	public static final int DIGITOS = 6;
	public static final long INTERVALO_SEGUNDOS = 30;
	private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

	private Totp() {
	}

	/** 160 bits al azar (lo que recomienda el RFC 4226 para HMAC-SHA1). */
	public static byte[] nuevoSecreto(SecureRandom azar) {
		byte[] s = new byte[20];
		azar.nextBytes(s);
		return s;
	}

	public static long paso(Instant momento) {
		return Math.floorDiv(momento.getEpochSecond(), INTERVALO_SEGUNDOS);
	}

	public static String codigo(byte[] secreto, long paso) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(secreto, "HmacSHA1"));
			byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
			int desplazamiento = h[h.length - 1] & 0x0f;
			int binario = ((h[desplazamiento] & 0x7f) << 24) | ((h[desplazamiento + 1] & 0xff) << 16)
					| ((h[desplazamiento + 2] & 0xff) << 8) | (h[desplazamiento + 3] & 0xff);
			return String.format("%06d", binario % 1_000_000);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * El intervalo en que el código es válido (el anterior, el actual o el siguiente) y posterior a ultimoPaso
	 * (un código ya usado no vuelve a servir). Vacío si no coincide.
	 */
	public static OptionalLong verificar(byte[] secreto, String codigo, Instant ahora, Long ultimoPaso) {
		if (codigo == null || !codigo.strip().matches("\\d{" + DIGITOS + "}")) {
			return OptionalLong.empty();
		}
		byte[] escrito = codigo.strip().getBytes();
		long actual = paso(ahora);
		for (long p = actual - 1; p <= actual + 1; p++) {
			boolean nuevo = ultimoPaso == null || p > ultimoPaso;
			// Comparación en tiempo constante: el tiempo de respuesta no revela cuántos dígitos acertó.
			if (nuevo && MessageDigest.isEqual(codigo(secreto, p).getBytes(), escrito)) {
				return OptionalLong.of(p);
			}
		}
		return OptionalLong.empty();
	}

	/** Base32 sin relleno: así se escribe el secreto en la app autenticadora. */
	public static String base32(byte[] datos) {
		StringBuilder sb = new StringBuilder();
		int buffer = 0, bits = 0;
		for (byte b : datos) {
			buffer = (buffer << 8) | (b & 0xff);
			bits += 8;
			while (bits >= 5) {
				sb.append(BASE32[(buffer >> (bits - 5)) & 31]);
				bits -= 5;
			}
		}
		if (bits > 0) {
			sb.append(BASE32[(buffer << (5 - bits)) & 31]);
		}
		return sb.toString();
	}

}
