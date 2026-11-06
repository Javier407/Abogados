package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import co.abogados.identidad.dominio.TokenAcceso;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;
import co.abogados.identidad.infraestructura.seguridad.VerificadorToken;

/** F13: identidad también rechaza un token de acceso revocado (antes solo lo hacía el gateway). */
class RevocacionEnIdentidadTest {

	@Test
	void unTokenRevocadoDejaDeServirAunqueNoHayaVencido() {
		EmisorJwtRsa emisor = new EmisorJwtRsa("", Duration.ofMinutes(15));
		Usuario u = new Usuario(1L, "u", "U", "u@firma.co", "h", true, Set.of("ABOGADO"), 0, null);
		TokenAcceso t = emisor.acceso(u, false, Instant.now());
		Set<String> revocados = new HashSet<>();
		VerificadorToken verificador = new VerificadorToken(new ImmutableJWKSet<SecurityContext>(new JWKSet(emisor.publica())),
				EmisorJwtRsa.EMISOR, revocados::contains);

		assertThat(verificador.verificar(t.token())).isPresent();
		revocados.add(t.jti().toString());
		assertThat(verificador.verificar(t.token())).isEmpty();
	}

}
