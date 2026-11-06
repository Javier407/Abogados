package co.abogados.procesos.infraestructura.seguridad;

import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/**
 * Verifica un token del servicio identidad: firma RS256 con su llave pública (JWKS), emisor y vencimiento.
 * El gateway ya lo verificó; este servicio lo verifica otra vez para no confiar en la red interna (F9).
 */
public class VerificadorToken {

	/** Quién llama: una persona (sujeto = documento) o un servicio interno (sujeto = "servicio:nombre"). */
	public record Identidad(String sujeto, Set<String> roles, Integer idCliente, boolean claveTemporal) {

		public boolean esServicio() {
			return sujeto.startsWith("servicio:") && roles.contains("SERVICIO");
		}
	}

	private final DefaultJWTProcessor<SecurityContext> procesador = new DefaultJWTProcessor<>();

	public VerificadorToken(JWKSource<SecurityContext> llaves, String emisor) {
		procesador.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, llaves));
		procesador.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
				new JWTClaimsSet.Builder().issuer(emisor).build(), Set.of("sub", "exp", "iat", "jti")));
	}

	public Optional<Identidad> verificar(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		try {
			JWTClaimsSet c = procesador.process(token, null);
			List<String> roles = c.getStringListClaim("roles");
			Long cliente = c.getLongClaim("idCliente");
			Boolean temporal = c.getBooleanClaim("clave_temporal");
			return Optional.of(new Identidad(c.getSubject(), roles == null ? Set.of() : new TreeSet<>(roles),
					cliente == null ? null : cliente.intValue(), Boolean.TRUE.equals(temporal)));
		} catch (ParseException | BadJOSEException | JOSEException | RuntimeException e) {
			return Optional.empty();
		}
	}

}
