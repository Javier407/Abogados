package co.abogados.gateway;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Seguridad del gateway: ninguna ruta de la API responde sin un token válido, salvo el inicio de sesión.
 * - Token RS256 firmado por el servicio identidad; se verifica con su JWKS y se exige el emisor.
 * - Permisos por rol según PoliticaAcceso (lo que no está en la tabla se niega).
 * - Sin sesiones ni cookies: el token viaja en la cabecera Authorization, por eso no hace falta CSRF.
 * - Solo el stream de avisos acepta el token en la URL (?access_token=), porque EventSource no permite
 *   cabeceras; el gateway lo quita antes de reenviar para que no quede en los logs de los servicios.
 */
@Configuration
@EnableWebFluxSecurity
public class ConfiguracionSeguridad {

	static final String RUTA_STREAM = "/api/notificaciones/stream";

	@Bean
	SecurityWebFilterChain cadena(ServerHttpSecurity http, PoliticaAcceso politica) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.logout(ServerHttpSecurity.LogoutSpec::disable)
				.authorizeExchange(a -> a
						.pathMatchers(HttpMethod.OPTIONS).permitAll()                 // preflight de CORS
						.pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						.pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.pathMatchers("/api/**").access(politica)
						.anyExchange().denyAll())
				.oauth2ResourceServer(o -> o
						.bearerTokenConverter(convertidorToken())
						.jwt(j -> j.jwtAuthenticationConverter(conversorRoles()))
						.authenticationEntryPoint((intercambio, e) -> responder(intercambio, HttpStatus.UNAUTHORIZED,
								"Inicie sesión para continuar"))
						.accessDeniedHandler((intercambio, e) -> responder(intercambio, HttpStatus.FORBIDDEN,
								"Su rol no tiene permiso para esta acción")))
				.exceptionHandling(e -> e
						.authenticationEntryPoint((intercambio, ex) -> responder(intercambio, HttpStatus.UNAUTHORIZED,
								"Inicie sesión para continuar"))
						.accessDeniedHandler((intercambio, ex) -> responder(intercambio, HttpStatus.FORBIDDEN,
								"Su rol no tiene permiso para esta acción")))
				.build();
	}

	@Bean
	ReactiveJwtDecoder decodificador(@Value("${jwt.jwks-uri}") String jwksUri, @Value("${jwt.emisor}") String emisor) {
		NimbusReactiveJwtDecoder d = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri)
				.jwsAlgorithm(SignatureAlgorithm.RS256).build();
		OAuth2TokenValidator<Jwt> validador = new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(emisor));
		d.setJwtValidator(validador);
		return d;
	}

	/** Token en la cabecera Authorization; en la URL solo para el stream de avisos. */
	static ServerAuthenticationConverter convertidorToken() {
		ServerBearerTokenAuthenticationConverter cabecera = new ServerBearerTokenAuthenticationConverter();
		ServerBearerTokenAuthenticationConverter cabeceraOUrl = new ServerBearerTokenAuthenticationConverter();
		cabeceraOUrl.setAllowUriQueryParameter(true);
		return intercambio -> RUTA_STREAM.equals(intercambio.getRequest().getPath().value())
				? cabeceraOUrl.convert(intercambio)
				: cabecera.convert(intercambio);
	}

	static ReactiveJwtAuthenticationConverterAdapter conversorRoles() {
		JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName("roles");
		roles.setAuthorityPrefix("ROL_");
		JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
		conversor.setJwtGrantedAuthoritiesConverter(roles);
		return new ReactiveJwtAuthenticationConverterAdapter(conversor);
	}

	/** Mismo formato de error que los servicios: { "error": "..." }. */
	static Mono<Void> responder(ServerWebExchange intercambio, HttpStatus estado, String mensaje) {
		var respuesta = intercambio.getResponse();
		if (respuesta.isCommitted()) {
			return Mono.empty();
		}
		respuesta.setStatusCode(estado);
		respuesta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		if (estado == HttpStatus.UNAUTHORIZED) {
			respuesta.getHeaders().set("WWW-Authenticate", "Bearer");
		}
		DataBuffer cuerpo = respuesta.bufferFactory()
				.wrap(("{\"error\":\"" + mensaje + "\"}").getBytes(StandardCharsets.UTF_8));
		return respuesta.writeWith(Mono.just(cuerpo));
	}

}
