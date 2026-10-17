package co.abogados.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;

/** Ninguna ruta de la API responde sin token, y cada rol solo llega a lo que le corresponde. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
		"GABINETE_URL=http://127.0.0.1:9", "PROCESOS_URL=http://127.0.0.1:9", "IDENTIDAD_URL=http://127.0.0.1:9" })
@AutoConfigureWebTestClient
class SeguridadTest {

	@Autowired
	WebTestClient web;

	static boolean permitido(String metodo, String ruta, String... roles) {
		return PoliticaAcceso.permitido(HttpMethod.valueOf(metodo), PathContainer.parsePath(ruta), Set.of(roles));
	}

	// ----------------------------------------------------------------- tabla de permisos

	@Test
	void cadaRolLlegaSoloASusRutas() {
		assertThat(permitido("GET", "/api/procesos", "DEPENDIENTE")).isTrue();
		assertThat(permitido("POST", "/api/procesos", "DEPENDIENTE")).isFalse();
		assertThat(permitido("POST", "/api/procesos/34/terminos", "DEPENDIENTE")).isTrue();
		assertThat(permitido("POST", "/api/procesos/34/actuaciones", "DEPENDIENTE")).isTrue();
		assertThat(permitido("DELETE", "/api/procesos/34/actuaciones/3", "DEPENDIENTE")).isFalse();
		assertThat(permitido("POST", "/api/procesos/34/partes", "DEPENDIENTE")).isFalse();
		assertThat(permitido("POST", "/api/procesos/34/providencias", "ABOGADO")).isTrue();
		assertThat(permitido("GET", "/api/procesos/34/expediente", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("POST", "/api/procesos", "ABOGADO")).isTrue();
		assertThat(permitido("GET", "/api/honorarios", "DEPENDIENTE")).isFalse();
		assertThat(permitido("GET", "/api/honorarios", "ABOGADO")).isTrue();
		assertThat(permitido("POST", "/api/honorarios/3/pagos", "ABOGADO")).isFalse();
		assertThat(permitido("POST", "/api/honorarios/3/pagos", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("POST", "/api/abogados", "ABOGADO")).isFalse();
		assertThat(permitido("POST", "/api/abogados", "SOCIO")).isTrue();
		assertThat(permitido("POST", "/api/asistente/borradores", "ADMINISTRATIVO")).isFalse();
		assertThat(permitido("PUT", "/api/vigilancia/radicados/1", "DEPENDIENTE")).isTrue();
	}

	@Test
	void elClienteSoloVeSuPerfilYLoNoListadoSeNiega() {
		assertThat(permitido("GET", "/api/auth/yo", "CLIENTE")).isTrue();
		assertThat(permitido("GET", "/api/procesos", "CLIENTE")).isFalse();
		assertThat(permitido("GET", "/api/notificaciones", "CLIENTE")).isFalse();
		assertThat(permitido("GET", "/api/ruta-nueva-sin-regla", "ADMIN")).isFalse();
	}

	// ----------------------------------------------------------------- cadena completa

	@Test
	void sinTokenEsUn401ConElFormatoComun() {
		web.get().uri("/api/procesos").exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().valueEquals("WWW-Authenticate", "Bearer")
				.expectBody().jsonPath("$.error").isEqualTo("Inicie sesión para continuar");
	}

	@Test
	void unTokenFalsoEsUn401() {
		web.get().uri("/api/procesos").header("Authorization", "Bearer abc.def.ghi").exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void unRolSinPermisoEsUn403() {
		web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROL_CLIENTE")))
				.get().uri("/api/procesos").exchange()
				.expectStatus().isForbidden()
				.expectBody().jsonPath("$.error").isEqualTo("Su rol no tiene permiso para esta acción");
	}

	@Test
	void conPermisoPasaAlServicio() {
		// El servicio de destino no existe en la prueba: lo importante es que no se detiene en 401 ni 403.
		web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROL_ABOGADO")))
				.get().uri("/api/procesos").exchange()
				.expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
	}

	@Test
	void elLoginNoPideToken() {
		web.post().uri("/api/auth/login").exchange()
				.expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
	}

	@Test
	void losEndpointsInternosDelGatewayNoEstanExpuestos() {
		web.get().uri("/actuator/gateway/routes").exchange().expectStatus().value(s -> assertThat(s).isNotEqualTo(200));
		web.get().uri("/actuator/health").exchange().expectStatus().isOk();
	}

	// ----------------------------------------------------------------- cabeceras de identidad

	@Test
	void lasCabecerasDeIdentidadSeReescribenConLasDelToken() {
		// Como en producción (Netty), las cabeceras de la petición original son de solo lectura.
		MockServerHttpRequest original = MockServerHttpRequest.get("/api/procesos")
				.header("X-Usuario-Id", "1").header("X-Usuario-Roles", "ADMIN").header("Accept", "application/json").build();
		ServerWebExchange falsificado = MockServerWebExchange.from(original).mutate()
				.request(new org.springframework.http.server.reactive.ServerHttpRequestDecorator(original) {
					@Override
					public org.springframework.http.HttpHeaders getHeaders() {
						return org.springframework.http.HttpHeaders.readOnlyHttpHeaders(original.getHeaders());
					}
				}).build();
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("1023987456").claim("roles", "ABOGADO").build();
		JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, java.util.List.of(new SimpleGrantedAuthority("ROL_ABOGADO")));

		var conToken = IdentidadEnCabeceras.conIdentidad(falsificado, token).getRequest().getHeaders();
		assertThat(conToken.get("X-Usuario-Id")).containsExactly("1023987456");
		assertThat(conToken.get("X-Usuario-Roles")).containsExactly("ABOGADO");

		var sinToken = IdentidadEnCabeceras.sinIdentidad(falsificado).getRequest().getHeaders();
		assertThat(sinToken.containsKey("X-Usuario-Id")).isFalse();
		assertThat(sinToken.containsKey("X-Usuario-Roles")).isFalse();
		assertThat(sinToken.getFirst("Accept")).isEqualTo("application/json");     // las demás se conservan
	}

}
