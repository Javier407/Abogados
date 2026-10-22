package co.abogados.identidad.infraestructura.seguridad;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import co.abogados.identidad.infraestructura.EmisorJwtRsa;

/**
 * Verificación del token en identidad (F9). Usa su propia llave pública, sin pasar por la red.
 * Públicas: entrar, renovar y cerrar la sesión (van con la cookie, no con el token), el JWKS y lo que piden los
 * servicios internos para obtener su token y la lista de tokens revocados.
 */
@Configuration
@ConditionalOnProperty(name = "seguridad.tokens.verificar", havingValue = "true", matchIfMissing = true)
public class ConfiguracionTokens {

	static final List<String> PUBLICAS = List.of("/auth/login", "/auth/login/mfa", "/auth/login/mfa/**", "/auth/renovar", "/auth/salir", "/.well-known/jwks.json",
			"/interno/token", "/interno/revocados", "/actuator/health", "/actuator/health/**");

	@Bean
	FilterRegistrationBean<FiltroToken> filtroToken(EmisorJwtRsa emisor,
			co.abogados.identidad.dominio.puertos.SesionesRepositorio sesiones, java.time.Clock reloj) {
		VerificadorToken verificador = new VerificadorToken(
				new ImmutableJWKSet<SecurityContext>(new JWKSet(emisor.publica())), EmisorJwtRsa.EMISOR,
				jti -> sesiones.revocado(jti, java.time.Instant.now(reloj)));
		FilterRegistrationBean<FiltroToken> registro = new FilterRegistrationBean<>(
				new FiltroToken(verificador, PUBLICAS, List.of("/auth/yo", "/auth/clave")));
		registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		return registro;
	}

}
