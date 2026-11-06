package co.abogados.identidad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.identidad.aplicacion.CuentasServicio;
import co.abogados.identidad.aplicacion.CuentasServicio.CambioCuenta;
import co.abogados.identidad.aplicacion.CuentasServicio.NuevaCuenta;
import co.abogados.identidad.dominio.ConflictoException;
import co.abogados.identidad.dominio.Cuenta;
import co.abogados.identidad.dominio.RecursoNoEncontradoException;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.infraestructura.UsuariosRecurso;

/** F8: solo un ADMIN gestiona cuentas, aunque alguien llegue al servicio por la red interna. */
@WebMvcTest(UsuariosRecurso.class)
class UsuariosRecursoTest {

	static final String NUEVA = """
			{"idUsuario":1020304050,"usuario":"lnueva","nombre":"Laura Nueva","email":"laura@firma.co",
			 "roles":["ABOGADO"],"claveTemporal":"Temporal2026x"}
			""";
	static final String CAMBIO = """
			{"usuario":"cgomez","nombre":"Catalina Gómez","email":"cgomez@firma.co","roles":["SOCIO"],"activo":false}
			""";

	@Autowired
	MockMvc mvc;

	@MockBean
	CuentasServicio servicio;

	static Cuenta cuenta(long id, String usuario) {
		return new Cuenta(id, usuario, "Nombre", usuario + "@firma.co", true, Set.of("ABOGADO"), null, null, true);
	}

	@Test
	void listarExigeElRolAdmin() throws Exception {
		given(servicio.listar()).willReturn(List.of(cuenta(52345678L, "cgomez")));

		mvc.perform(get("/usuarios").header("X-Usuario-Roles", "SOCIO, ABOGADO"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Solo el administrador puede gestionar las cuentas"));
		mvc.perform(get("/usuarios").header("X-Usuario-Roles", "SOCIO, ADMIN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].usuario").value("cgomez"));
	}

	@Test
	void unRolQueSoloContieneAdminEnElNombreNoSirve() throws Exception {
		mvc.perform(get("/usuarios").header("X-Usuario-Roles", "ADMINISTRATIVO")).andExpect(status().isForbidden());
		verifyNoInteractions(servicio);
	}

	@Test
	void sinCabeceraDeRolesEsUn401() throws Exception {
		mvc.perform(get("/usuarios")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Falta la identidad del usuario"));
	}

	@Test
	void crearDevuelve201ConLaUbicacionYPasaQuienLaCrea() throws Exception {
		given(servicio.crear(any(), eq(79123456L))).willReturn(cuenta(1020304050L, "lnueva"));

		mvc.perform(post("/usuarios").header("X-Usuario-Id", "79123456").header("X-Usuario-Roles", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(NUEVA))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/usuarios/1020304050"))
				.andExpect(jsonPath("$.usuario").value("lnueva"))
				.andExpect(jsonPath("$.debeCambiarClave").value(true));
		verify(servicio).crear(new NuevaCuenta(1020304050L, "lnueva", "Laura Nueva", "laura@firma.co", Set.of("ABOGADO"),
				"Temporal2026x", null), 79123456L);
	}

	@Test
	void crearSinPermisoNoLlegaAlServicio() throws Exception {
		mvc.perform(post("/usuarios").header("X-Usuario-Id", "52345678").header("X-Usuario-Roles", "SOCIO")
				.contentType(MediaType.APPLICATION_JSON).content(NUEVA))
				.andExpect(status().isForbidden());
		verify(servicio, never()).crear(any(), anyLong());
	}

	@Test
	void crearConDatosFaltantesEsUn400ConLosCampos() throws Exception {
		mvc.perform(post("/usuarios").header("X-Usuario-Id", "79123456").header("X-Usuario-Roles", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idUsuario\":-5,\"usuario\":\"\",\"nombre\":\"x\",\"email\":\"x@y.co\",\"roles\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Hay datos inválidos en la petición"))
				.andExpect(jsonPath("$.campos.idUsuario").exists())
				.andExpect(jsonPath("$.campos.usuario").value("El usuario es obligatorio"))
				.andExpect(jsonPath("$.campos.roles").value("Asigne al menos un rol"))
				.andExpect(jsonPath("$.campos.claveTemporal").value("Escriba la contraseña temporal"));
	}

	@Test
	void unJsonMalFormadoEsUn400SinDetallesInternos() throws Exception {
		mvc.perform(post("/usuarios").header("X-Usuario-Id", "79123456").header("X-Usuario-Roles", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content("{\"idUsuario\": "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("El cuerpo de la petición no es válido"));
	}

	@Test
	void lasReglasDelServicioSeTraducenACodigosHttp() throws Exception {
		given(servicio.crear(any(), anyLong())).willThrow(new ConflictoException("Ya existe una cuenta"));
		mvc.perform(post("/usuarios").header("X-Usuario-Id", "79123456").header("X-Usuario-Roles", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(NUEVA))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Ya existe una cuenta"));

		given(servicio.actualizar(eq(52345678L), any(), anyLong()))
				.willThrow(new ReglaNegocioException("Debe quedar al menos un administrador activo"));
		mvc.perform(put("/usuarios/52345678").header("X-Usuario-Id", "79123456").header("X-Usuario-Roles", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(CAMBIO))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("Debe quedar al menos un administrador activo"));

		given(servicio.desbloquear(eq(9L), anyLong())).willThrow(new RecursoNoEncontradoException("No existe la cuenta 9"));
		mvc.perform(post("/usuarios/9/desbloqueo").header("X-Usuario-Id", "79123456").header("X-Usuario-Roles", "ADMIN"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("No existe la cuenta 9"));
	}

	@Test
	void actualizarPasaElCambioCompleto() throws Exception {
		given(servicio.actualizar(eq(52345678L), any(), eq(79123456L))).willReturn(cuenta(52345678L, "cgomez"));

		mvc.perform(put("/usuarios/52345678").header("X-Usuario-Id", "79123456").header("X-Usuario-Roles", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(CAMBIO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idUsuario").value(52345678L));
		verify(servicio).actualizar(52345678L,
				new CambioCuenta("cgomez", "Catalina Gómez", "cgomez@firma.co", Set.of("SOCIO"), false, null), 79123456L);
	}

	@Test
	void restablecerLaClaveYDesbloquear() throws Exception {
		given(servicio.restablecerClave(52345678L, "OtraTemporal2026", 79123456L)).willReturn(cuenta(52345678L, "cgomez"));
		given(servicio.desbloquear(52345678L, 79123456L)).willReturn(cuenta(52345678L, "cgomez"));

		mvc.perform(post("/usuarios/52345678/clave").header("X-Usuario-Id", "79123456").header("X-Usuario-Roles", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content("{\"claveTemporal\":\"OtraTemporal2026\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario").value("cgomez"));
		mvc.perform(post("/usuarios/52345678/clave").header("X-Usuario-Id", "79123456").header("X-Usuario-Roles", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content("{\"claveTemporal\":\" \"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/usuarios/52345678/desbloqueo").header("X-Usuario-Id", "79123456")
				.header("X-Usuario-Roles", "ADMIN"))
				.andExpect(status().isOk());
		mvc.perform(post("/usuarios/52345678/desbloqueo").header("X-Usuario-Id", "79123456")
				.header("X-Usuario-Roles", "ABOGADO"))
				.andExpect(status().isForbidden());
	}

}
