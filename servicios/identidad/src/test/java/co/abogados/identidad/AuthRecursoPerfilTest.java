package co.abogados.identidad;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.CredencialesInvalidasException;
import co.abogados.identidad.dominio.RecursoNoEncontradoException;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.SesionInvalidaException;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.infraestructura.AuthRecurso;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;
import co.abogados.identidad.infraestructura.LimiteIntentosPorIp;
import jakarta.servlet.http.Cookie;

/** Perfil, cambio de contraseña y cookie sin Secure (solo para probar por HTTP). */
@WebMvcTest(AuthRecurso.class)
@TestPropertySource(properties = "sesiones.cookie-segura=false")
class AuthRecursoPerfilTest {

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

	@BeforeEach
	void sinLimite() {
		given(limite.permitir(any())).willReturn(true);
	}

	@Test
	void yoDevuelveElPerfilSinElHashDeLaClave() throws Exception {
		given(servicio.yo(1032456781L)).willReturn(new Usuario(1032456781L, "cmendez", "Carolina Méndez",
				"carolina@correo.co", "$2a$10$secreto", true, Set.of("CLIENTE"), 2, null, 6));

		mvc.perform(get("/auth/yo").header("X-Usuario-Id", "1032456781"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario").value("cmendez"))
				.andExpect(jsonPath("$.idCliente").value(6))
				.andExpect(jsonPath("$.roles[0]").value("CLIENTE"))
				.andExpect(jsonPath("$.hashClave").doesNotExist())
				.andExpect(jsonPath("$.intentosFallidos").doesNotExist());
	}

	@Test
	void yoDeUnaCuentaQueNoExisteEsUn404() throws Exception {
		given(servicio.yo(5L)).willThrow(new RecursoNoEncontradoException("No existe el usuario 5"));

		mvc.perform(get("/auth/yo").header("X-Usuario-Id", "5")).andExpect(status().isNotFound());
	}

	@Test
	void cambiarLaClaveConservaLaSesionDeLaCookie() throws Exception {
		mvc.perform(post("/auth/clave").header("X-Usuario-Id", "79123456").cookie(new Cookie("gj_refresco", "r-actual"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"actual\":\"Prueba2026*\",\"nueva\":\"Expediente2026\"}"))
				.andExpect(status().isNoContent());

		verify(servicio).cambiarClave(79123456L, "Prueba2026*", "Expediente2026", "r-actual");
	}

	@Test
	void cambiarLaClaveConErroresDevuelveElCodigoDeCadaCaso() throws Exception {
		mvc.perform(post("/auth/clave").header("X-Usuario-Id", "79123456").contentType(MediaType.APPLICATION_JSON)
				.content("{\"actual\":\"\",\"nueva\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.actual").value("Escriba la contraseña actual"))
				.andExpect(jsonPath("$.campos.nueva").value("Escriba la contraseña nueva"));

		willThrow(new CredencialesInvalidasException("La contraseña actual no es correcta")).given(servicio)
				.cambiarClave(79123456L, "mala", "Expediente2026", null);
		mvc.perform(post("/auth/clave").header("X-Usuario-Id", "79123456").contentType(MediaType.APPLICATION_JSON)
				.content("{\"actual\":\"mala\",\"nueva\":\"Expediente2026\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("La contraseña actual no es correcta"));

		willThrow(new ReglaNegocioException("La contraseña debe combinar letras y números")).given(servicio)
				.cambiarClave(79123456L, "Prueba2026*", "soloLetrasAqui", null);
		mvc.perform(post("/auth/clave").header("X-Usuario-Id", "79123456").contentType(MediaType.APPLICATION_JSON)
				.content("{\"actual\":\"Prueba2026*\",\"nueva\":\"soloLetrasAqui\"}"))
				.andExpect(status().isUnprocessableEntity());

		mvc.perform(post("/auth/clave").contentType(MediaType.APPLICATION_JSON)
				.content("{\"actual\":\"Prueba2026*\",\"nueva\":\"Expediente2026\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void sinHttpsLaCookieNoLlevaSecurePeroSigueSiendoHttpOnly() throws Exception {
		given(servicio.iniciarSesion(eq("arodriguez"), eq("Prueba2026*"), eq("127.0.0.1"))).willReturn(new Sesion("eyJ",
				Instant.now().plusSeconds(900), 79123456L, "arodriguez", "Andrés", Set.of("SOCIO"), false, null, "r1",
				Instant.now().plusSeconds(3600)));

		mvc.perform(post("/auth/login").header("X-Forwarded-For", " ").contentType(MediaType.APPLICATION_JSON)
				.content("{\"usuario\":\"arodriguez\",\"clave\":\"Prueba2026*\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string("Set-Cookie", allOf(startsWith("gj_refresco=r1"), containsString("HttpOnly"),
						containsString("SameSite=Strict"), not(containsString("Secure")))));
	}

	@Test
	void unaSesionSinRefreshTokenDejaLaCookieVacia() throws Exception {
		given(servicio.iniciarSesion(any(), any(), any())).willReturn(new Sesion("eyJ", Instant.now().plusSeconds(900),
				79123456L, "arodriguez", "Andrés", Set.of("SOCIO")));

		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"usuario\":\"arodriguez\",\"clave\":\"Prueba2026*\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.debeCambiarClave").value(false))
				.andExpect(header().string("Set-Cookie", allOf(startsWith("gj_refresco=;"), containsString("Max-Age=0"))));
	}

	@Test
	void salirSinLaCabeceraAntiCsrfNoCierraNada() throws Exception {
		mvc.perform(post("/auth/salir").header("X-Requested-With", " ").cookie(new Cookie("gj_refresco", "r1")))
				.andExpect(status().isForbidden());
		verifyNoInteractions(sesiones);

		mvc.perform(post("/auth/salir").header("X-Requested-With", "XMLHttpRequest"))
				.andExpect(status().isNoContent())
				.andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
		verify(sesiones).cerrar(null);
	}

	@Test
	void renovarConCookieVaciaNoLlegaAlServicio() throws Exception {
		mvc.perform(post("/auth/renovar").header("X-Requested-With", "XMLHttpRequest").cookie(new Cookie("gj_refresco", "")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Inicie sesión para continuar"));
		verifyNoInteractions(sesiones);
	}

	@Test
	void unaSesionInvalidaFueraDeRenovarTambienEsUn401() throws Exception {
		given(servicio.iniciarSesion(any(), any(), any())).willThrow(new SesionInvalidaException("La sesión venció"));

		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"usuario\":\"arodriguez\",\"clave\":\"Prueba2026*\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("La sesión venció"));
	}

}
