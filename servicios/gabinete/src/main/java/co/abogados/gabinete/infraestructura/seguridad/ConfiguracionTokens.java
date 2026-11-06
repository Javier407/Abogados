package co.abogados.gabinete.infraestructura.seguridad;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Verificación del token en este servicio (defensa en profundidad, F9). La llave pública se descarga del JWKS de
 * identidad y se guarda en caché. Solo se apaga con seguridad.tokens.verificar=false (pruebas sin identidad).
 */
@Configuration
@ConditionalOnProperty(name = "seguridad.tokens.verificar", havingValue = "true", matchIfMissing = true)
public class ConfiguracionTokens {

	@Bean
	VerificadorToken verificadorToken(@Value("${jwt.jwks-uri}") String jwks,
			@Value("${jwt.emisor:gestion-juridica}") String emisor) throws MalformedURLException {
		return new VerificadorToken(
				JWKSourceBuilder.<SecurityContext>create(URI.create(jwks).toURL()).retrying(true).build(), emisor);
	}

	@Bean
	FilterRegistrationBean<FiltroToken> filtroToken(VerificadorToken verificador) {
		FilterRegistrationBean<FiltroToken> registro = new FilterRegistrationBean<>(
				new FiltroToken(verificador, List.of("/actuator/health", "/actuator/health/**"), List.of()));
		registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		return registro;
	}

}
