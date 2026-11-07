package co.abogados.identidad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import co.abogados.identidad.aplicacion.AccesoExternoServicio;
import co.abogados.identidad.dominio.AccesoExterno.Modo;
import co.abogados.identidad.dominio.AccesoExterno.Proveedor;
import co.abogados.identidad.dominio.AccesoExterno.Resultado;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.infraestructura.AccesoExternoRecurso;
import co.abogados.identidad.infraestructura.LimiteIntentosPorIp;

/** La ida devuelve la URL con la cookie del flujo; la vuelta siempre redirige dentro de la app. */
@WebMvcTest(AccesoExternoRecurso.class)
@TestPropertySource(properties = "externo.url-publica=https://lexora.test/")
class AccesoExternoRecursoTest {

	static final String VUELTA = "https://lexora.test/api/auth/externo/google/vuelta";

	@Autowired
	MockMvc mvc;

	@MockBean
	AccesoExternoServicio servicio;

	@MockBean
	LimiteIntentosPorIp limite;

	@Test
	void laIdaDevuelveLaUrlYDejaLaCookieDelFlujoLaxDeDiezMinutos() throws Exception {
		given(limite.permitir(anyString())).willReturn(true);
		given(servicio.iniciar(Proveedor.GOOGLE, Modo.ENTRAR, null, VUELTA))
				.willReturn(new AccesoExternoServicio.Ida("https://accounts.google.com/x", "comp-1"));

		mvc.perform(post("/auth/externo/google/entrar").header("X-Requested-With", "XMLHttpRequest"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("https://accounts.google.com/x"))
				.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
						org.hamcrest.Matchers.containsString("gj_oauth=comp-1"), org.hamcrest.Matchers.containsString("HttpOnly"),
						org.hamcrest.Matchers.containsString("SameSite=Lax"), org.hamcrest.Matchers.containsString("Max-Age=600"),
						org.hamcrest.Matchers.containsString("Path=/api/auth/externo"))));
	}

	@Test
	void sinCabeceraAjaxOConProveedorDesconocidoNoHayIda() throws Exception {
		mvc.perform(post("/auth/externo/google/entrar")).andExpect(status().isForbidden());
		given(limite.permitir(anyString())).willReturn(true);
		mvc.perform(post("/auth/externo/facebook/entrar").header("X-Requested-With", "x")).andExpect(status().isNotFound());
	}

	@Test
	void conSesionAbiertaVuelveAlLoginConLaCookieDeLaSesion() throws Exception {
		Sesion s = new Sesion("tok", Instant.now().plusSeconds(900), 1019087654L, "crojas", "Carolina", Set.of("ABOGADO"),
				false, null, "refresco-1", Instant.now().plusSeconds(3600));
		given(servicio.volver(eq(Proveedor.GOOGLE), eq("c"), eq("e"), eq("comp-1"), isNull(), eq(VUELTA), any()))
				.willReturn(new Resultado.SesionAbierta(s));

		mvc.perform(get("/auth/externo/google/vuelta").param("code", "c").param("state", "e").cookie(new Cookie("gj_oauth", "comp-1")))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "/login?externo=ok"))
				.andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItems(
						org.hamcrest.Matchers.startsWith("gj_oauth=;"), org.hamcrest.Matchers.startsWith("gj_refresco=refresco-1"))))
				.andExpect(header().string("Cache-Control", "no-store"));
	}

	@Test
	void elSegundoPasoViajaEnElFragmentoYLosFallosConUnMotivo() throws Exception {
		given(servicio.volver(eq(Proveedor.GOOGLE), any(), eq("e1"), any(), any(), any(), any()))
				.willReturn(new Resultado.SegundoPaso("des.af+io", false));
		given(servicio.volver(eq(Proveedor.GOOGLE), any(), eq("e2"), any(), any(), any(), any()))
				.willReturn(new Resultado.Fallo(Modo.ENTRAR, "sin-enlace"));
		given(servicio.volver(eq(Proveedor.GOOGLE), any(), eq("e3"), any(), any(), any(), any()))
				.willReturn(new Resultado.Enlazada(Proveedor.GOOGLE));
		given(servicio.volver(eq(Proveedor.GOOGLE), any(), eq("e4"), any(), any(), any(), any()))
				.willReturn(new Resultado.Fallo(Modo.ENLAZAR, "de-otra-persona"));

		mvc.perform(get("/auth/externo/google/vuelta").param("state", "e1"))
				.andExpect(header().string("Location", "/login#desafio=des.af%2Bio&configurar=false"));
		mvc.perform(get("/auth/externo/google/vuelta").param("state", "e2"))
				.andExpect(header().string("Location", "/login?externo=error&motivo=sin-enlace"));
		mvc.perform(get("/auth/externo/google/vuelta").param("state", "e3"))
				.andExpect(header().string("Location", "/cuenta?enlazado=GOOGLE"));
		mvc.perform(get("/auth/externo/google/vuelta").param("state", "e4"))
				.andExpect(header().string("Location", "/cuenta?enlazado=error&motivo=de-otra-persona"));
	}

	@Test
	void losProveedoresOfrecidosSonLosConfigurados() throws Exception {
		given(servicio.disponibles()).willReturn(List.of(Proveedor.GOOGLE, Proveedor.GITHUB));
		mvc.perform(get("/auth/externo/proveedores"))
				.andExpect(jsonPath("$[0].codigo").value("GOOGLE"))
				.andExpect(jsonPath("$[1].nombre").value("GitHub"));
	}

}
