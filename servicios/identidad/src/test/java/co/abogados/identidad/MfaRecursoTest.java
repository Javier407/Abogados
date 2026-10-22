package co.abogados.identidad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.aplicacion.MfaServicio;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.CredencialesInvalidasException;
import co.abogados.identidad.dominio.MfaRequeridoException;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.infraestructura.AuthRecurso;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;
import co.abogados.identidad.infraestructura.LimiteIntentosPorIp;
import co.abogados.identidad.infraestructura.MfaRecurso;

/** F13: la API de la verificación en dos pasos y la respuesta del login cuando falta el código. */
@WebMvcTest({ MfaRecurso.class, AuthRecurso.class })
class MfaRecursoTest {

	static final String JSON = MediaType.APPLICATION_JSON_VALUE;

	@Autowired
	MockMvc mvc;

	@MockBean
	MfaServicio mfa;

	@MockBean
	AutenticacionServicio auth;

	@MockBean
	SesionesServicio sesiones;

	@MockBean
	EmisorJwtRsa emisor;

	@MockBean
	LimiteIntentosPorIp limite;

	static Sesion sesion() {
		return new Sesion("eyJ...", Instant.now().plusSeconds(900), 1019087654L, "crojas", "Carolina", Set.of("ABOGADO"),
				false, null, "refresco", Instant.now().plusSeconds(3600));
	}

	@BeforeEach
	void sinLimite() {
		given(limite.permitir(any())).willReturn(true);
	}

	@Test
	void conVerificacionActivaElLoginNoDaTokenNiCookieSinoUnDesafio() throws Exception {
		given(auth.iniciarSesion(any(), any(), any())).willThrow(new MfaRequeridoException("desafio-firmado", false));

		mvc.perform(post("/auth/login").contentType(JSON).content("{\"usuario\":\"crojas\",\"clave\":\"x\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mfaRequerido").value(true))
				.andExpect(jsonPath("$.configurarMfa").value(false))
				.andExpect(jsonPath("$.desafio").value("desafio-firmado"))
				.andExpect(jsonPath("$.token").doesNotExist())
				.andExpect(header().doesNotExist("Set-Cookie"));
	}

	@Test
	void conElCodigoCorrectoSeAbreLaSesionConSuCookie() throws Exception {
		given(mfa.completar(eq("d"), eq("123456"), any())).willReturn(sesion());

		mvc.perform(post("/auth/login/mfa").contentType(JSON).content("{\"desafio\":\"d\",\"codigo\":\"123456\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("eyJ..."))
				.andExpect(jsonPath("$.codigosRecuperacion").doesNotExist())
				.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("gj_refresco=refresco")));
	}

	@Test
	void unCodigoIncorrectoEs401YDemasiadosIntentosDesdeLaIpEs429() throws Exception {
		given(mfa.completar(any(), any(), any())).willThrow(new CredencialesInvalidasException("El código no es correcto o ya se usó"));
		mvc.perform(post("/auth/login/mfa").contentType(JSON).content("{\"desafio\":\"d\",\"codigo\":\"000000\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("El código no es correcto o ya se usó"));

		given(limite.permitir(any())).willReturn(false);
		mvc.perform(post("/auth/login/mfa").contentType(JSON).content("{\"desafio\":\"d\",\"codigo\":\"000000\"}"))
				.andExpect(status().isTooManyRequests());
		mvc.perform(post("/auth/login/mfa/activacion").contentType(JSON).content("{\"desafio\":\"d\",\"codigo\":\"000000\"}"))
				.andExpect(status().isTooManyRequests());
		mvc.perform(post("/auth/login/mfa").contentType(JSON).content("{\"codigo\":\"000000\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void quienDebeActivarlaAlEntrarRecibeLaSesionYSusCodigos() throws Exception {
		given(mfa.iniciarConDesafio("d")).willReturn(new MfaServicio.Inicio("ABC", "otpauth://totp/x"));
		given(mfa.activarConDesafio(eq("d"), eq("123456"), any()))
				.willReturn(new MfaServicio.Activacion(sesion(), List.of("aaaa-bbbb")));

		mvc.perform(post("/auth/login/mfa/inicio").contentType(JSON).content("{\"desafio\":\"d\"}"))
				.andExpect(jsonPath("$.uri").value("otpauth://totp/x"));
		mvc.perform(post("/auth/login/mfa/activacion").contentType(JSON).content("{\"desafio\":\"d\",\"codigo\":\"123456\"}"))
				.andExpect(jsonPath("$.token").value("eyJ..."))
				.andExpect(jsonPath("$.codigosRecuperacion[0]").value("aaaa-bbbb"));
	}

	@Test
	void laPropiaCuentaLaConfigura() throws Exception {
		given(mfa.estado(7L)).willReturn(new MfaServicio.Estado(true, Instant.parse("2026-10-22T10:00:00Z"), 9, false));
		given(mfa.iniciar(7L)).willReturn(new MfaServicio.Inicio("ABC", "otpauth://totp/x"));
		given(mfa.activar(7L, "123456")).willReturn(List.of("aaaa-bbbb"));
		given(mfa.regenerarCodigos(7L, "clave")).willReturn(List.of("cccc-dddd"));

		mvc.perform(get("/auth/mfa").header("X-Usuario-Id", "7")).andExpect(jsonPath("$.codigosRestantes").value(9));
		mvc.perform(post("/auth/mfa/inicio").header("X-Usuario-Id", "7")).andExpect(jsonPath("$.secreto").value("ABC"));
		mvc.perform(post("/auth/mfa/activacion").header("X-Usuario-Id", "7").contentType(JSON).content("{\"codigo\":\"123456\"}"))
				.andExpect(jsonPath("$.codigos[0]").value("aaaa-bbbb"));
		mvc.perform(post("/auth/mfa/codigos").header("X-Usuario-Id", "7").contentType(JSON).content("{\"clave\":\"clave\"}"))
				.andExpect(jsonPath("$.codigos[0]").value("cccc-dddd"));
		mvc.perform(post("/auth/mfa/desactivacion").header("X-Usuario-Id", "7").contentType(JSON)
				.content("{\"clave\":\"clave\",\"codigo\":\"123456\"}"))
				.andExpect(status().isNoContent());
		then(mfa).should().desactivar(7L, "clave", "123456");
	}

	@Test
	void soloElAdministradorVeQuienLaTieneYLaReinicia() throws Exception {
		given(mfa.conMfa()).willReturn(Set.of(7L));
		mvc.perform(get("/usuarios/mfa").header("X-Usuario-Roles", "ADMIN")).andExpect(jsonPath("$[0]").value(7));
		mvc.perform(post("/usuarios/7/mfa/reinicio").header("X-Usuario-Id", "1").header("X-Usuario-Roles", "ADMIN"))
				.andExpect(status().isNoContent());
		then(mfa).should().reiniciar(1L, 7L);

		mvc.perform(post("/usuarios/8/mfa/reinicio").header("X-Usuario-Id", "1").header("X-Usuario-Roles", "SOCIO"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/usuarios/mfa").header("X-Usuario-Roles", "ABOGADO")).andExpect(status().isForbidden());
		then(mfa).should(never()).reiniciar(1L, 8L);
	}

}
