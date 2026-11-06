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
@AutoConfigureWebTestClient(timeout = "30s")   // la primera petición (JVM en frío, servidor cargado) puede pasar de 5 s
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
		assertThat(permitido("GET", "/api/audiencias", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("POST", "/api/audiencias/avisos", "DEPENDIENTE")).isTrue();
		assertThat(permitido("POST", "/api/audiencias/avisos", "ADMINISTRATIVO")).isFalse();
		assertThat(permitido("POST", "/api/procesos/34/pruebas", "DEPENDIENTE")).isFalse();
		assertThat(permitido("GET", "/api/audiencias", "CLIENTE")).isFalse();
		assertThat(permitido("GET", "/api/usuarios", "ADMIN")).isTrue();
		assertThat(permitido("POST", "/api/usuarios/5/clave", "SOCIO")).isFalse();
		assertThat(permitido("GET", "/api/reportes/carga", "SOCIO")).isTrue();
		assertThat(permitido("GET", "/api/reportes/carga", "ABOGADO")).isFalse();
		assertThat(permitido("GET", "/api/honorarios/cartera", "ABOGADO")).isFalse();
		assertThat(permitido("GET", "/api/honorarios/cartera", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("POST", "/api/gastos", "ABOGADO")).isTrue();
		assertThat(permitido("POST", "/api/gastos/3/reembolso", "ABOGADO")).isFalse();
		assertThat(permitido("POST", "/api/honorarios/2/pagos/5/anulacion", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("POST", "/api/honorarios/2/pagos/5/anulacion", "ABOGADO")).isFalse();
		assertThat(permitido("POST", "/api/procesos", "ABOGADO")).isTrue();
		assertThat(permitido("GET", "/api/honorarios", "DEPENDIENTE")).isFalse();
		assertThat(permitido("GET", "/api/honorarios", "ABOGADO")).isTrue();
		assertThat(permitido("POST", "/api/honorarios/3/pagos", "ABOGADO")).isFalse();
		assertThat(permitido("POST", "/api/honorarios/3/pagos", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("POST", "/api/abogados", "ABOGADO")).isFalse();
		assertThat(permitido("POST", "/api/abogados", "SOCIO")).isTrue();
		assertThat(permitido("POST", "/api/asistente/borradores", "ADMINISTRATIVO")).isFalse();
		assertThat(permitido("PUT", "/api/vigilancia/radicados/1", "DEPENDIENTE")).isTrue();
		// F14: facturación
		assertThat(permitido("GET", "/api/facturas/5", "ABOGADO")).isTrue();
		assertThat(permitido("POST", "/api/facturas", "ABOGADO")).isFalse();
		assertThat(permitido("POST", "/api/facturas", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("POST", "/api/facturas/5/anulacion", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("GET", "/api/facturas", "DEPENDIENTE")).isFalse();
		assertThat(permitido("PUT", "/api/facturacion/config", "ADMINISTRATIVO")).isFalse();
		assertThat(permitido("PUT", "/api/facturacion/config", "SOCIO")).isTrue();
		assertThat(permitido("POST", "/api/facturacion/config/validacion", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("GET", "/api/facturacion/config", "ABOGADO")).isFalse();
		// F13: cada quien configura su verificación en dos pasos; reiniciarla es del administrador
		assertThat(permitido("POST", "/api/auth/mfa/inicio", "CLIENTE")).isTrue();
		assertThat(permitido("GET", "/api/auth/mfa", "DEPENDIENTE")).isTrue();
		assertThat(permitido("POST", "/api/usuarios/7/mfa/reinicio", "SOCIO")).isFalse();
		assertThat(permitido("POST", "/api/usuarios/7/mfa/reinicio", "ADMIN")).isTrue();
		// F12: reglas de términos (las valida un socio) y estado del servidor (solo el administrador)
		assertThat(permitido("GET", "/api/reglas-termino", "DEPENDIENTE")).isTrue();
		assertThat(permitido("POST", "/api/reglas-termino/4/validacion", "SOCIO")).isTrue();
		assertThat(permitido("POST", "/api/reglas-termino/4/validacion", "ADMIN")).isFalse();
		assertThat(permitido("PUT", "/api/reglas-termino/4", "ABOGADO")).isFalse();
		assertThat(permitido("PUT", "/api/catalogos/tipos/3/contestacion", "SOCIO")).isTrue();
		assertThat(permitido("PUT", "/api/catalogos/tipos/3/contestacion", "ADMIN")).isFalse();
		assertThat(permitido("GET", "/api/notificaciones/servidor", "ADMIN")).isTrue();
		assertThat(permitido("GET", "/api/notificaciones/servidor", "SOCIO")).isFalse();
		assertThat(permitido("DELETE", "/api/notificaciones/servidor/revisiones", "ADMIN")).isFalse();
		assertThat(permitido("GET", "/api/notificaciones", "CLIENTE")).isTrue();
		// F11: catálogos de la firma y equipo del proceso
		assertThat(permitido("POST", "/api/gabinete/sedes", "SOCIO")).isTrue();
		assertThat(permitido("PUT", "/api/gabinete/cargos/2", "ABOGADO")).isFalse();
		assertThat(permitido("GET", "/api/gabinete/areas", "DEPENDIENTE")).isTrue();
		assertThat(permitido("POST", "/api/procesos/34/asignaciones", "ABOGADO")).isTrue();
		assertThat(permitido("POST", "/api/procesos/34/asignaciones", "DEPENDIENTE")).isFalse();
		// F10: documentos y consultas iniciales
		assertThat(permitido("POST", "/api/procesos/34/documentos", "DEPENDIENTE")).isTrue();
		assertThat(permitido("DELETE", "/api/procesos/34/documentos/2", "DEPENDIENTE")).isFalse();
		assertThat(permitido("DELETE", "/api/procesos/34/documentos/2", "ABOGADO")).isTrue();
		assertThat(permitido("GET", "/api/procesos/34/documentos/2/archivo", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("GET", "/api/procesos/34/documentos/2/archivo", "CLIENTE")).isFalse();
		assertThat(permitido("GET", "/api/portal/procesos/7/documentos/2/archivo", "CLIENTE")).isTrue();
		assertThat(permitido("POST", "/api/consultas", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("POST", "/api/consultas/3/contratacion", "DEPENDIENTE")).isFalse();
		assertThat(permitido("POST", "/api/consultas/3/contratacion", "ADMINISTRATIVO")).isTrue();
		assertThat(permitido("GET", "/api/consultas", "CLIENTE")).isFalse();
	}

	@Test
	void elClienteSoloVeSuPerfilSuPortalYSusAvisos() {
		assertThat(permitido("GET", "/api/auth/yo", "CLIENTE")).isTrue();
		assertThat(permitido("GET", "/api/procesos", "CLIENTE")).isFalse();
		assertThat(permitido("GET", "/api/portal/procesos", "CLIENTE")).isTrue();
		assertThat(permitido("GET", "/api/portal/finanzas", "CLIENTE")).isTrue();
		assertThat(permitido("POST", "/api/portal/procesos", "CLIENTE")).isFalse();
		assertThat(permitido("GET", "/api/portal/procesos", "ABOGADO")).isFalse();
		assertThat(permitido("GET", "/api/notificaciones", "CLIENTE")).isTrue();
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
	void conClaveTemporalSoloSePuedeCambiarLaClave() {
		var temporal = mockJwt().jwt(j -> j.claim("clave_temporal", true))
				.authorities(new SimpleGrantedAuthority("ROL_ADMIN"));
		web.mutateWith(temporal).get().uri("/api/usuarios").exchange().expectStatus().isForbidden();
		web.mutateWith(temporal).post().uri("/api/auth/clave").exchange()
				.expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
	}

	@Test
	void renovarYSalirNoPidenToken() {
		web.post().uri("/api/auth/renovar").exchange().expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
		web.post().uri("/api/auth/salir").exchange().expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
		// F13: el segundo paso del login tampoco (aún no hay token)
		web.post().uri("/api/auth/login/mfa").exchange().expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
		web.post().uri("/api/auth/login/mfa/activacion").exchange().expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
		web.post().uri("/api/auth/mfa/inicio").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void unTokenRevocadoOUnTokenDeServicioNoSirvenDesdeFuera() {
		ListaRevocados lista = new ListaRevocados(org.springframework.web.reactive.function.client.WebClient.builder(),
				"http://127.0.0.1:9");
		lista.reemplazar(Set.of("jti-cortado"));
		Jwt cortado = Jwt.withTokenValue("t").header("alg", "RS256").subject("79123456").jti("jti-cortado").build();
		Jwt vigente = Jwt.withTokenValue("t").header("alg", "RS256").subject("79123456").jti("otro").build();
		Jwt servicio = Jwt.withTokenValue("t").header("alg", "RS256").subject("servicio:procesos").jti("s").build();

		assertThat(lista.validate(cortado).hasErrors()).isTrue();
		assertThat(lista.validate(vigente).hasErrors()).isFalse();
		assertThat(lista.validate(servicio).hasErrors()).isTrue();
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
		assertThat(conToken.getFirst("Authorization")).isEqualTo("Bearer t");   // el servicio verifica el token también

		var sinToken = IdentidadEnCabeceras.sinIdentidad(falsificado).getRequest().getHeaders();
		assertThat(sinToken.containsKey("X-Usuario-Id")).isFalse();
		assertThat(sinToken.containsKey("X-Usuario-Roles")).isFalse();
		assertThat(sinToken.getFirst("Accept")).isEqualTo("application/json");     // las demás se conservan
	}

}
