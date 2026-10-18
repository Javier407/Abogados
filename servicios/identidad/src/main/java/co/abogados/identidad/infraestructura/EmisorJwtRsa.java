package co.abogados.identidad.infraestructura;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import co.abogados.identidad.dominio.TokenAcceso;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.EmisorTokens;

/**
 * Firma los tokens con RS256. Solo este servicio tiene la llave privada; el gateway los verifica con la
 * llave pública que publica /.well-known/jwks.json. La llave privada se lee de un archivo PEM (PKCS#8)
 * montado desde infra/secretos; si no existe (pruebas, desarrollo) se genera una temporal y se avisa.
 *
 * Claims: sub (documento o "servicio:<nombre>"), roles, usuario, nombre, jti (para revocarlo), idCliente (cuentas
 * del portal) y clave_temporal (debe cambiar la contraseña antes de usar la app).
 */
@Component
public class EmisorJwtRsa implements EmisorTokens {

	private static final Logger log = LoggerFactory.getLogger(EmisorJwtRsa.class);
	public static final String EMISOR = "gestion-juridica";

	private final RSAKey llave;
	private final Duration vigencia;

	public EmisorJwtRsa(@Value("${jwt.llave-privada:}") String rutaLlave,
			@Value("${jwt.vigencia:PT15M}") Duration vigencia) {
		this.llave = cargar(rutaLlave);
		this.vigencia = vigencia;
	}

	@Override
	public TokenAcceso acceso(Usuario u, boolean claveTemporal, Instant ahora) {
		JWTClaimsSet.Builder claims = base(String.valueOf(u.idUsuario()), List.copyOf(u.roles()), ahora)
				.claim("usuario", u.usuario())
				.claim("nombre", u.nombre());
		if (u.idCliente() != null) {
			claims.claim("idCliente", u.idCliente());
		}
		if (claveTemporal) {
			claims.claim("clave_temporal", true);
		}
		return firmar(claims, ahora);
	}

	@Override
	public TokenAcceso servicio(String nombre, Instant ahora) {
		return firmar(base("servicio:" + nombre, List.of("SERVICIO"), ahora), ahora);
	}

	private JWTClaimsSet.Builder base(String sujeto, List<String> roles, Instant ahora) {
		return new JWTClaimsSet.Builder()
				.issuer(EMISOR)
				.subject(sujeto)
				.claim("roles", roles)
				.issueTime(Date.from(ahora))
				.expirationTime(Date.from(ahora.plus(vigencia)));
	}

	private TokenAcceso firmar(JWTClaimsSet.Builder claims, Instant ahora) {
		UUID jti = UUID.randomUUID();
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(llave.getKeyID()).build(),
				claims.jwtID(jti.toString()).build());
		try {
			jwt.sign(new RSASSASigner(llave));
		} catch (JOSEException e) {
			throw new IllegalStateException("No se pudo firmar el token", e);
		}
		return new TokenAcceso(jwt.serialize(), jti, ahora.plus(vigencia));
	}

	/** Llave pública: este mismo servicio verifica con ella los tokens que recibe. */
	public RSAKey publica() {
		return llave.toPublicJWK();
	}

	/** Conjunto de llaves públicas (JWKS) para verificar los tokens. Nunca incluye la parte privada. */
	public Map<String, Object> jwks() {
		return new JWKSet(llave.toPublicJWK()).toJSONObject(true);
	}

	private static RSAKey cargar(String ruta) {
		try {
			if (ruta != null && !ruta.isBlank() && Files.exists(Path.of(ruta))) {
				String pem = Files.readString(Path.of(ruta));
				String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
				KeyFactory fabrica = KeyFactory.getInstance("RSA");
				RSAPrivateCrtKey privada = (RSAPrivateCrtKey) fabrica
						.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
				RSAPublicKey publica = (RSAPublicKey) fabrica
						.generatePublic(new RSAPublicKeySpec(privada.getModulus(), privada.getPublicExponent()));
				RSAKey llave = new RSAKey.Builder(publica).privateKey(privada).keyUse(KeyUse.SIGNATURE)
						.algorithm(JWSAlgorithm.RS256).keyIDFromThumbprint().build();
				log.info("Llave de firma cargada desde el archivo (kid {})", llave.getKeyID());
				return llave;
			}
			log.warn("Sin llave privada en '{}': se usa una TEMPORAL; los tokens dejarán de valer al reiniciar", ruta);
			KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
			generador.initialize(2048);
			var par = generador.generateKeyPair();
			return new RSAKey.Builder((RSAPublicKey) par.getPublic()).privateKey(par.getPrivate())
					.keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).keyIDFromThumbprint().build();
		} catch (IOException | GeneralSecurityException | JOSEException | ClassCastException e) {
			throw new IllegalStateException("No se pudo cargar la llave de firma de " + ruta, e);
		}
	}

}
