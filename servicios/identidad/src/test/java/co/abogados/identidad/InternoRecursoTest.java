package co.abogados.identidad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.identidad.aplicacion.CuentasServicio;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.Cuenta;
import co.abogados.identidad.dominio.TokenAcceso;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;
import co.abogados.identidad.infraestructura.InternoRecurso;

/** Rutas internas: token de servicio con su clave, lista de revocados y cuentas de un cliente. */
@WebMvcTest(InternoRecurso.class)
@TestPropertySource(properties = "servicios.claves= procesos = clave-de-procesos ,gabinete=, roto, notificaciones=avisos=2026")
class InternoRecursoTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	InternoRecurso recurso;

	@MockBean
	EmisorJwtRsa emisor;

	@MockBean
	SesionesServicio sesiones;

	@MockBean
	CuentasServicio cuentas;

	void pedirToken(String cuerpo, int estado) throws Exception {
		mvc.perform(post("/interno/token").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().is(estado));
	}

	@Test
	void unServicioConSuClaveRecibeUnToken() throws Exception {
		given(emisor.servicio(eq("procesos"), any()))
				.willReturn(new TokenAcceso("eyJ.servicio", UUID.randomUUID(), Instant.parse("2026-10-16T15:15:00Z")));

		mvc.perform(post("/interno/token").contentType(MediaType.APPLICATION_JSON)
				.content("{\"servicio\":\"procesos\",\"clave\":\"clave-de-procesos\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("eyJ.servicio"))
				.andExpect(jsonPath("$.expira").exists());
	}

	@Test
	void laClaveSePartePorElPrimerIgualYSeRecorta() throws Exception {
		given(emisor.servicio(eq("notificaciones"), any()))
				.willReturn(new TokenAcceso("eyJ.n", UUID.randomUUID(), Instant.parse("2026-10-16T15:15:00Z")));

		pedirToken("{\"servicio\":\"notificaciones\",\"clave\":\"avisos=2026\"}", 200);
	}

	@Test
	void claveIncorrectaServicioDesconocidoOSinClaveEsUn401() throws Exception {
		pedirToken("{\"servicio\":\"procesos\",\"clave\":\"otra\"}", 401);
		pedirToken("{\"servicio\":\"procesos\",\"clave\":\"clave-de-procesos \"}", 401);
		pedirToken("{\"servicio\":\"procesos\"}", 401);
		pedirToken("{\"servicio\":\"desconocido\",\"clave\":\"clave-de-procesos\"}", 401);
		pedirToken("{\"clave\":\"clave-de-procesos\"}", 401);
		// Un servicio configurado sin clave no puede pedir token (ni con la clave vacía)
		pedirToken("{\"servicio\":\"gabinete\",\"clave\":\"\"}", 401);
		pedirToken("{\"servicio\":\"roto\",\"clave\":\"\"}", 401);
		verify(emisor, never()).servicio(anyString(), any());

		mvc.perform(post("/interno/token").contentType(MediaType.APPLICATION_JSON)
				.content("{\"servicio\":\"procesos\",\"clave\":\"otra\"}"))
				.andExpect(jsonPath("$.error").value("Servicio o clave incorrectos"))
				.andExpect(jsonPath("$.token").doesNotExist());
	}

	@Test
	void listaDeRevocados() throws Exception {
		given(sesiones.revocados()).willReturn(List.of("jti-1", "jti-2"));

		mvc.perform(get("/interno/revocados")).andExpect(status().isOk())
				.andExpect(jsonPath("$[1]").value("jti-2"));
	}

	@Test
	void lasCuentasDeUnClienteSoloLasVeUnServicio() throws Exception {
		given(cuentas.deCliente(6)).willReturn(List.of(new Cuenta(1032456781L, "cmendez", "Carolina Méndez",
				"carolina@correo.co", true, Set.of("CLIENTE"), null, null, false, 6)));

		mvc.perform(get("/interno/clientes/6/cuentas")).andExpect(status().isForbidden());
		mvc.perform(get("/interno/clientes/6/cuentas").header("X-Usuario-Roles", "ADMIN,SOCIO"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Solo para servicios internos"));
		mvc.perform(get("/interno/clientes/6/cuentas").header("X-Usuario-Roles", "SERVICIO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].idUsuario").value(1032456781L))
				.andExpect(jsonPath("$[0].email").value("carolina@correo.co"))
				// Solo lo necesario para avisar: nada de roles ni usuario
				.andExpect(jsonPath("$[0].usuario").doesNotExist())
				.andExpect(jsonPath("$[0].roles").doesNotExist());
	}

	@Test
	void laLimpiezaPeriodicaDelegaEnLasSesiones() {
		recurso.limpiar();
		verify(sesiones).limpiar();
	}

}
