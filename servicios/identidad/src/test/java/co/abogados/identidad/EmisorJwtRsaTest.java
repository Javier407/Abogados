package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import co.abogados.identidad.dominio.TokenAcceso;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;
import co.abogados.identidad.infraestructura.seguridad.VerificadorToken;

/** Firma RS256 con la llave del archivo PEM y verificación de los tokens que emite. */
class EmisorJwtRsaTest {

	static final Instant AHORA = Instant.now();   // el verificador mira el vencimiento con la hora real

	static Usuario usuario(Integer idCliente) {
		return new Usuario(79123456L, "arodriguez", "Andrés", "arodriguez@firma.co", "h", true, Set.of("ADMIN", "SOCIO"),
				0, null, idCliente);
	}

	static VerificadorToken verificador(EmisorJwtRsa emisor) {
		return new VerificadorToken(new ImmutableJWKSet<SecurityContext>(new JWKSet(emisor.publica())),
				EmisorJwtRsa.EMISOR);
	}

	static Path pem(Path carpeta) throws Exception {
		var generador = java.security.KeyPairGenerator.getInstance("RSA");
		generador.initialize(2048);
		String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(generador.generateKeyPair()
				.getPrivate().getEncoded());
		Path archivo = carpeta.resolve("jwt.pem");
		Files.writeString(archivo, "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n");
		return archivo;
	}

	@Test
	void conLaMismaLlaveDelArchivoDosInstanciasFirmanIgualYSeVerificanEntreSi(@TempDir Path carpeta) throws Exception {
		Path archivo = pem(carpeta);
		EmisorJwtRsa uno = new EmisorJwtRsa(archivo.toString(), Duration.ofMinutes(15));
		EmisorJwtRsa otro = new EmisorJwtRsa(archivo.toString(), Duration.ofMinutes(15));

		assertThat(uno.publica().getKeyID()).isEqualTo(otro.publica().getKeyID());
		TokenAcceso t = uno.acceso(usuario(null), false, AHORA);
		assertThat(verificador(otro).verificar(t.token())).hasValueSatisfying(id -> {
			assertThat(id.sujeto()).isEqualTo("79123456");
			assertThat(id.roles()).containsExactly("ADMIN", "SOCIO");
			assertThat(id.idCliente()).isNull();
			assertThat(id.claveTemporal()).isFalse();
			assertThat(id.esServicio()).isFalse();
		});
	}

	@Test
	void sinArchivoSeGeneraUnaLlaveTemporalDistintaEnCadaArranque() {
		EmisorJwtRsa uno = new EmisorJwtRsa(null, Duration.ofMinutes(15));
		EmisorJwtRsa otro = new EmisorJwtRsa("/no/existe/jwt.pem", Duration.ofMinutes(15));

		assertThat(uno.publica().getKeyID()).isNotEqualTo(otro.publica().getKeyID());
		assertThat(verificador(otro).verificar(uno.acceso(usuario(null), false, AHORA).token())).isEmpty();
	}

	@Test
	void unArchivoQueNoEsUnaLlaveImpideArrancar(@TempDir Path carpeta) throws Exception {
		Path malo = carpeta.resolve("malo.pem");
		Files.writeString(malo, "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n");

		assertThatThrownBy(() -> new EmisorJwtRsa(malo.toString(), Duration.ofMinutes(15)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining(malo.toString());
	}

	@Test
	void elTokenDeUnClienteConClaveTemporalLlevaSuFichaYLaMarca() throws Exception {
		EmisorJwtRsa emisor = new EmisorJwtRsa("", Duration.ofMinutes(15));
		TokenAcceso t = emisor.acceso(usuario(6), true, AHORA);

		JWTClaimsSet c = SignedJWT.parse(t.token()).getJWTClaimsSet();
		assertThat(c.getJWTID()).isEqualTo(t.jti().toString());
		assertThat(c.getStringClaim("usuario")).isEqualTo("arodriguez");
		assertThat(c.getBooleanClaim("clave_temporal")).isTrue();
		assertThat(t.expira()).isEqualTo(AHORA.plus(Duration.ofMinutes(15)));
		assertThat(verificador(emisor).verificar(t.token())).hasValueSatisfying(id -> {
			assertThat(id.idCliente()).isEqualTo(6);
			assertThat(id.claveTemporal()).isTrue();
		});
	}

	@Test
	void elTokenDeServicioSoloLlevaElRolServicio() throws Exception {
		EmisorJwtRsa emisor = new EmisorJwtRsa("", Duration.ofMinutes(15));
		TokenAcceso t = emisor.servicio("notificaciones", AHORA);

		JWTClaimsSet c = SignedJWT.parse(t.token()).getJWTClaimsSet();
		assertThat(c.getSubject()).isEqualTo("servicio:notificaciones");
		assertThat(c.getStringListClaim("roles")).containsExactly("SERVICIO");
		assertThat(c.getClaim("usuario")).isNull();
		assertThat(verificador(emisor).verificar(t.token())).hasValueSatisfying(id -> assertThat(id.esServicio()).isTrue());
	}

	@Test
	void elVerificadorRechazaTokensVaciosVencidosODeOtroEmisor() {
		EmisorJwtRsa emisor = new EmisorJwtRsa("", Duration.ofMinutes(15));
		VerificadorToken v = verificador(emisor);

		assertThat(v.verificar(null)).isEmpty();
		assertThat(v.verificar("  ")).isEmpty();
		assertThat(v.verificar("a.b.c")).isEmpty();
		assertThat(v.verificar(emisor.acceso(usuario(null), false, AHORA.minus(Duration.ofHours(1))).token())).isEmpty();
		VerificadorToken otroEmisor = new VerificadorToken(
				new ImmutableJWKSet<SecurityContext>(new JWKSet(emisor.publica())), "otro-emisor");
		assertThat(otroEmisor.verificar(emisor.acceso(usuario(null), false, AHORA).token())).isEmpty();
	}

	@Test
	void unSujetoQueEmpiezaComoServicioSinElRolNoEsUnServicio() {
		assertThat(new VerificadorToken.Identidad("servicio:falso", Set.of("ABOGADO"), null, false).esServicio()).isFalse();
		assertThat(new VerificadorToken.Identidad("79123456", Set.of("SERVICIO"), null, false).esServicio()).isFalse();
	}

}
