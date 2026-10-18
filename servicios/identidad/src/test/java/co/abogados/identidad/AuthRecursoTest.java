package co.abogados.identidad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.SesionInvalidaException;
import co.abogados.identidad.dominio.CredencialesInvalidasException;
import co.abogados.identidad.dominio.CuentaBloqueadaException;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.infraestructura.AuthRecurso;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;
import co.abogados.identidad.infraestructura.LimiteIntentosPorIp;

@WebMvcTest(AuthRecurso.class)
class AuthRecursoTest {

	@Autowired
	MockMvc mvc;

	@MockBean
	AutenticacionServicio servicio;

	@MockBean
	EmisorJwtRsa emisor;

	@MockBean
	SesionesServicio sesiones;

	@MockBean
	LimiteIntentosPorIp limite;

	@org.junit.jupiter.api.BeforeEach
	void sinLimite() {
		given(limite.permitir(any())).willReturn(true);
	}

	@Test
	void loginDevuelveElTokenYUsaLaIpReenviadaPorElGateway() throws Exception {
		given(servicio.iniciarSesion(eq("arodriguez"), eq("Prueba2026*"), eq("100.64.0.5"))).willReturn(new Sesion(
				"eyJ...", Instant.parse("2026-10-16T23:00:00Z"), 79123456L, "arodriguez", "Andrés", Set.of("SOCIO"), false,
				null, "refresco-secreto", Instant.now().plusSeconds(3600)));

		mvc.perform(post("/auth/login").header("X-Forwarded-For", "100.64.0.5, 172.20.0.9")
				.contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"arodriguez\",\"clave\":\"Prueba2026*\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("eyJ..."))
				.andExpect(jsonPath("$.roles[0]").value("SOCIO"))
				// El refresh token va solo en la cookie HttpOnly, nunca en el cuerpo
				.andExpect(jsonPath("$.refresco").doesNotExist())
				.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
						org.hamcrest.Matchers.startsWith("gj_refresco=refresco-secreto"),
						org.hamcrest.Matchers.containsString("Path=/api/auth"),
						org.hamcrest.Matchers.containsString("HttpOnly"),
						org.hamcrest.Matchers.containsString("Secure"),
						org.hamcrest.Matchers.containsString("SameSite=Strict"))));
	}

	@Test
	void renovarUsaLaCookieYPideLaCabeceraAntiCsrf() throws Exception {
		given(sesiones.renovar(eq("r1"), any())).willReturn(new Sesion("eyJ2", Instant.now().plusSeconds(900), 79123456L,
				"arodriguez", "Andrés", Set.of("SOCIO"), false, null, "r2", Instant.now().plusSeconds(3600)));

		mvc.perform(post("/auth/renovar").cookie(new jakarta.servlet.http.Cookie("gj_refresco", "r1")))
				.andExpect(status().isForbidden());
		mvc.perform(post("/auth/renovar").header("X-Requested-With", "XMLHttpRequest")
				.cookie(new jakarta.servlet.http.Cookie("gj_refresco", "r1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("eyJ2"))
				.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("gj_refresco=r2")));
	}

	@Test
	void renovarConUnaSesionInvalidaBorraLaCookie() throws Exception {
		given(sesiones.renovar(eq("viejo"), any())).willThrow(new SesionInvalidaException("La sesión venció"));

		mvc.perform(post("/auth/renovar").header("X-Requested-With", "XMLHttpRequest")
				.cookie(new jakarta.servlet.http.Cookie("gj_refresco", "viejo")))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
		mvc.perform(post("/auth/renovar").header("X-Requested-With", "XMLHttpRequest"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void salirCierraLaSesionEnElServidor() throws Exception {
		mvc.perform(post("/auth/salir").header("X-Requested-With", "XMLHttpRequest")
				.cookie(new jakarta.servlet.http.Cookie("gj_refresco", "r1")))
				.andExpect(status().isNoContent());
		org.mockito.Mockito.verify(sesiones).cerrar("r1");
	}

	@Test
	void credencialesInvalidasEsUn401SinDetalles() throws Exception {
		given(servicio.iniciarSesion(any(), any(), any()))
				.willThrow(new CredencialesInvalidasException("Usuario o contraseña incorrectos"));

		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"usuario\":\"x\",\"clave\":\"y\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Usuario o contraseña incorrectos"));
	}

	@Test
	void cuentaBloqueadaEsUn423ConRetryAfter() throws Exception {
		given(servicio.iniciarSesion(any(), any(), any())).willThrow(new CuentaBloqueadaException(15));

		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"usuario\":\"x\",\"clave\":\"y\"}"))
				.andExpect(status().isLocked())
				.andExpect(header().string("Retry-After", "900"));
	}

	@Test
	void sinCabeceraDeIdentidadNoHayPerfil() throws Exception {
		mvc.perform(get("/auth/yo")).andExpect(status().isUnauthorized());
	}

	@Test
	void faltanDatosEsUn400() throws Exception {
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.clave").exists());
	}

	@Test
	void superarElLimiteDeIntentosPorIpEsUn429() throws Exception {
		given(limite.permitir("100.64.0.9")).willReturn(false);

		mvc.perform(post("/auth/login").header("X-Forwarded-For", "100.64.0.9")
				.contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"x\",\"clave\":\"y\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "600"));
	}

	@Test
	void jwks() throws Exception {
		given(emisor.jwks()).willReturn(Map.of("keys", java.util.List.of(Map.of("kty", "RSA", "kid", "k1"))));
		mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk()).andExpect(jsonPath("$.keys[0].kid").value("k1"));
	}

}
