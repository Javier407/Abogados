package co.abogados.identidad.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.http.HttpServletRequest;

/** F9: el servicio no confía en las cabeceras; la identidad sale del token firmado por identidad. */
class FiltroTokenTest {

	static final RSAKey LLAVE = generar();
	static final RSAKey OTRA = generar();

	final FiltroToken filtro = new FiltroToken(
			new VerificadorToken(new ImmutableJWKSet<SecurityContext>(new JWKSet(LLAVE.toPublicJWK())), "gestion-juridica"),
			List.of("/actuator/health/**"), List.of("/auth/clave"));

	static RSAKey generar() {
		try {
			return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	static String token(RSAKey llave, String sujeto, List<String> roles, Integer cliente, boolean temporal, Instant expira)
			throws Exception {
		JWTClaimsSet.Builder c = new JWTClaimsSet.Builder().issuer("gestion-juridica").subject(sujeto).claim("roles", roles)
				.issueTime(new Date()).expirationTime(Date.from(expira)).jwtID(UUID.randomUUID().toString());
		if (cliente != null) {
			c.claim("idCliente", cliente);
		}
		if (temporal) {
			c.claim("clave_temporal", true);
		}
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(llave.getKeyID()).build(), c.build());
		jwt.sign(new RSASSASigner(llave));
		return jwt.serialize();
	}

	static String valido(String sujeto, String... roles) throws Exception {
		return token(LLAVE, sujeto, List.of(roles), null, false, Instant.now().plusSeconds(600));
	}

	/** Pasa la petición por el filtro y devuelve la que llegó al servicio (null si el filtro respondió). */
	HttpServletRequest pasar(MockHttpServletRequest peticion, MockHttpServletResponse respuesta) throws Exception {
		AtomicReference<HttpServletRequest> recibida = new AtomicReference<>();
		filtro.doFilter(peticion, respuesta, (p, r) -> recibida.set((HttpServletRequest) p));
		return recibida.get();
	}

	static MockHttpServletRequest peticion(String metodo, String ruta, String token) {
		MockHttpServletRequest p = new MockHttpServletRequest(metodo, ruta);
		if (token != null) {
			p.addHeader("Authorization", "Bearer " + token);
		}
		return p;
	}

	@Test
	void sinTokenOConFirmaAjenaOVencidoEsUn401() throws Exception {
		for (String t : new String[] { null, "basura",
				token(OTRA, "79123456", List.of("ADMIN"), null, false, Instant.now().plusSeconds(600)),
				token(LLAVE, "79123456", List.of("ADMIN"), null, false, Instant.now().minusSeconds(600)) }) {
			MockHttpServletResponse r = new MockHttpServletResponse();
			MockHttpServletRequest p = peticion("GET", "/procesos", t);
			p.addHeader("X-Usuario-Id", "79123456");
			p.addHeader("X-Usuario-Roles", "ADMIN");
			assertThat(pasar(p, r)).isNull();
			assertThat(r.getStatus()).isEqualTo(401);
		}
	}

	@Test
	void laIdentidadSaleDelTokenAunqueLaPeticionTraigaOtrasCabeceras() throws Exception {
		MockHttpServletRequest p = peticion("GET", "/procesos", valido("1019087654", "ABOGADO"));
		p.addHeader("X-Usuario-Id", "79123456");
		p.addHeader("X-Usuario-Roles", "ADMIN");
		p.addHeader("X-Cliente-Id", "1");

		HttpServletRequest recibida = pasar(p, new MockHttpServletResponse());

		assertThat(recibida.getHeader("X-Usuario-Id")).isEqualTo("1019087654");
		assertThat(recibida.getHeader("x-usuario-roles")).isEqualTo("ABOGADO");
		assertThat(recibida.getHeader("X-Cliente-Id")).isNull();
	}

	@Test
	void elClienteLlevaSuFichaYUnServicioSoloConsulta() throws Exception {
		HttpServletRequest cliente = pasar(peticion("GET", "/portal/procesos",
				token(LLAVE, "1032456781", List.of("CLIENTE"), 6, false, Instant.now().plusSeconds(600))),
				new MockHttpServletResponse());
		assertThat(cliente.getHeader("X-Cliente-Id")).isEqualTo("6");

		String servicio = valido("servicio:notificaciones", "SERVICIO");
		HttpServletRequest lectura = pasar(peticion("GET", "/procesos/3", servicio), new MockHttpServletResponse());
		assertThat(lectura.getHeader("X-Usuario-Id")).isNull();
		assertThat(lectura.getHeader("X-Usuario-Roles")).isEqualTo("SERVICIO");

		MockHttpServletResponse r = new MockHttpServletResponse();
		assertThat(pasar(peticion("POST", "/procesos", servicio), r)).isNull();
		assertThat(r.getStatus()).isEqualTo(403);
	}

	@Test
	void conClaveTemporalSoloSePuedeCambiarLaClave() throws Exception {
		String temporal = token(LLAVE, "1099000001", List.of("ABOGADO"), null, true, Instant.now().plusSeconds(600));
		MockHttpServletResponse r = new MockHttpServletResponse();
		assertThat(pasar(peticion("GET", "/procesos", temporal), r)).isNull();
		assertThat(r.getStatus()).isEqualTo(403);
		assertThat(pasar(peticion("POST", "/auth/clave", temporal), new MockHttpServletResponse())).isNotNull();
	}

	@Test
	void laSaludNoPideToken() throws Exception {
		assertThat(pasar(peticion("GET", "/actuator/health/liveness", null), new MockHttpServletResponse())).isNotNull();
	}

}
