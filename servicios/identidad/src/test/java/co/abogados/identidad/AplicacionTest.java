package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;

/**
 * La aplicación completa con el filtro de tokens, sin base de datos: solo se usan rutas que no la consultan.
 * Comprueba que el filtro está registrado y que la identidad sale del token y no de las cabeceras.
 */
@SpringBootTest(properties = "servicios.claves=procesos=clave-de-procesos")
@AutoConfigureMockMvc
class AplicacionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	EmisorJwtRsa emisor;

	@Autowired
	Clock reloj;

	@MockBean
	SesionesServicio sesiones;   // evita que la limpieza programada intente conectarse a la base

	@MockBean
	co.abogados.identidad.dominio.puertos.SesionesRepositorio repoSesiones;   // F13: el filtro consulta los revocados

	String token(Set<String> roles, boolean temporal) {
		return emisor.acceso(new Usuario(52345678L, "cgomez", "Catalina", "cgomez@firma.co", "h", true, roles, 0, null),
				temporal, Instant.now()).token();
	}

	@Test
	void elRelojEsUtc() {
		assertThat(reloj.getZone().getId()).isEqualTo("Z");
	}

	@Test
	void sinTokenNingunaRutaPrivadaResponde() throws Exception {
		mvc.perform(get("/usuarios").header("X-Usuario-Roles", "ADMIN").header("X-Usuario-Id", "79123456"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(jsonPath("$.error").value("Inicie sesión para continuar"));
		mvc.perform(get("/auth/yo").header("Authorization", "Bearer basura")).andExpect(status().isUnauthorized());
		mvc.perform(get("/interno/clientes/6/cuentas").header("X-Usuario-Roles", "SERVICIO"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void lasCabecerasDeIdentidadFalsificadasNoDanPermisoDeAdministrador() throws Exception {
		mvc.perform(get("/usuarios").header("Authorization", "Bearer " + token(Set.of("SOCIO"), false))
				.header("X-Usuario-Roles", "ADMIN"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Solo el administrador puede gestionar las cuentas"));
	}

	@Test
	void unTokenRevocadoNoSirveEnIdentidad() throws Exception {
		org.mockito.BDDMockito.given(repoSesiones.revocado(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any())).willReturn(true);
		mvc.perform(get("/auth/yo").header("Authorization", "Bearer " + token(Set.of("SOCIO"), false)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void conClaveTemporalSoloPuedeCambiarLaClave() throws Exception {
		mvc.perform(get("/usuarios").header("Authorization", "Bearer " + token(Set.of("ADMIN"), true)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Cambie su contraseña temporal antes de continuar"));
	}

	@Test
	void unServicioObtieneSuTokenPeroSoloPuedeConsultar() throws Exception {
		String respuesta = mvc.perform(post("/interno/token").contentType(MediaType.APPLICATION_JSON)
				.content("{\"servicio\":\"procesos\",\"clave\":\"clave-de-procesos\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(respuesta, "$.token");

		mvc.perform(post("/usuarios").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Un servicio interno solo puede consultar"));
		// Un servicio no tiene X-Usuario-Id: /auth/yo no sabe de quién es el perfil
		mvc.perform(get("/auth/yo").header("Authorization", "Bearer " + token).header("X-Usuario-Id", "79123456"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Falta la identidad del usuario"));
	}

	@Test
	void elJwksEsPublicoYNoExponeLaParteePrivada() throws Exception {
		mvc.perform(get("/.well-known/jwks.json"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
				.andExpect(jsonPath("$.keys[0].kid").value(emisor.publica().getKeyID()))
				.andExpect(jsonPath("$.keys[0].d").doesNotExist());
	}

}
