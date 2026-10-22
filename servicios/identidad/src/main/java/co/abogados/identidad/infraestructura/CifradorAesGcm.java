package co.abogados.identidad.infraestructura;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.abogados.identidad.dominio.puertos.CifradorSecretos;

/**
 * Cifra los secretos de la verificación en dos pasos con AES-256-GCM. La llave (32 bytes en Base64) viene de
 * MFA_CLAVE_CIFRADO en infra/.env, fuera de la base y de los respaldos. Formato guardado: "v1:" + Base64(iv | cifrado).
 * Sin llave (desarrollo y pruebas) se usa una fija y se avisa en el log: no sirve para datos reales.
 */
@Component
public class CifradorAesGcm implements CifradorSecretos {

	private static final Logger log = LoggerFactory.getLogger(CifradorAesGcm.class);
	private static final SecureRandom AZAR = new SecureRandom();
	private static final String VERSION = "v1:";

	private final SecretKeySpec llave;

	public CifradorAesGcm(@Value("${mfa.clave-cifrado:}") String claveBase64) {
		byte[] bytes;
		if (claveBase64 == null || claveBase64.isBlank()) {
			log.warn("Sin MFA_CLAVE_CIFRADO: los secretos de la verificación en dos pasos usan una llave de desarrollo");
			bytes = sha256("gestion-juridica-llave-de-desarrollo");
		} else {
			bytes = Base64.getDecoder().decode(claveBase64.strip());
			if (bytes.length != 32) {
				throw new IllegalStateException("MFA_CLAVE_CIFRADO debe ser de 32 bytes en Base64 (openssl rand -base64 32)");
			}
		}
		this.llave = new SecretKeySpec(bytes, "AES");
	}

	@Override
	public String cifrar(byte[] secreto) {
		try {
			byte[] iv = new byte[12];
			AZAR.nextBytes(iv);
			Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
			c.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(128, iv));
			byte[] cifrado = c.doFinal(secreto);
			return VERSION + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length)
					.put(iv).put(cifrado).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public byte[] descifrar(String guardado) {
		if (guardado == null || !guardado.startsWith(VERSION)) {
			throw new IllegalStateException("Secreto con un formato desconocido");
		}
		try {
			byte[] todo = Base64.getDecoder().decode(guardado.substring(VERSION.length()));
			Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
			c.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(128, todo, 0, 12));
			return c.doFinal(todo, 12, todo.length - 12);
		} catch (GeneralSecurityException e) {
			// Llave distinta o dato alterado: GCM lo detecta.
			throw new IllegalStateException("No se pudo descifrar el secreto (¿cambió MFA_CLAVE_CIFRADO?)", e);
		}
	}

	private static byte[] sha256(String texto) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

}
