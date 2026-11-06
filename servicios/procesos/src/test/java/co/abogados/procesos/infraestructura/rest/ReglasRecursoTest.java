package co.abogados.procesos.infraestructura.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.procesos.aplicacion.ReglaServicio;
import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.modelo.ReglaDatos;
import co.abogados.procesos.dominio.modelo.ReglaVista;

/** F12: la API de reglas de términos. Solo un socio las cambia, aunque el gateway ya lo filtre. */
@WebMvcTest(ReglasRecurso.class)
class ReglasRecursoTest {

	static final String REGLA = """
			{ "tipoProvidencia": "auto", "codigoRama": " ", "patron": " inadmite ", "lado": "demandante",
			  "descripcion": "Subsanar la demanda", "dias": 5, "norma": "CGP art. 90" }
			""";

	@Autowired
	MockMvc mvc;

	@MockBean
	ReglaServicio servicio;

	static ReglaVista vista(int id) {
		return new ReglaVista(id, "AUTO", null, null, "inadmite", "DEMANDANTE", "Subsanar la demanda", 5, "CGP art. 90",
				1, true, false, null, null, null, 79456123L, null);
	}

	@Test
	void unSocioCreaLaReglaYLosDatosLleganLimpios() throws Exception {
		given(servicio.crear(any(), eq(79456123L))).willReturn(vista(10));

		mvc.perform(post("/reglas-termino").contentType(MediaType.APPLICATION_JSON).content(REGLA)
				.header("X-Usuario-Id", "79456123").header("X-Usuario-Roles", "ABOGADO,SOCIO"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/reglas-termino/10"))
				.andExpect(jsonPath("$.validada").value(false));

		ArgumentCaptor<ReglaDatos> datos = ArgumentCaptor.forClass(ReglaDatos.class);
		then(servicio).should().crear(datos.capture(), eq(79456123L));
		ReglaDatos d = datos.getValue();
		org.assertj.core.api.Assertions.assertThat(d)
				.isEqualTo(new ReglaDatos("AUTO", null, null, "inadmite", "DEMANDANTE", "Subsanar la demanda", 5,
						"CGP art. 90", 1, true));
	}

	@Test
	void quienNoEsSocioRecibe403AunqueLlegueSinPasarPorElGateway() throws Exception {
		mvc.perform(post("/reglas-termino/4/validacion").header("X-Usuario-Id", "1").header("X-Usuario-Roles", "ADMIN"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Solo un socio cambia o valida las reglas de términos"));
		mvc.perform(put("/reglas-termino/4").contentType(MediaType.APPLICATION_JSON).content(REGLA)
				.header("X-Usuario-Id", "1"))
				.andExpect(status().isForbidden());
		mvc.perform(put("/catalogos/tipos/1/contestacion").contentType(MediaType.APPLICATION_JSON).content("{\"dias\": 15}")
				.header("X-Usuario-Id", "1").header("X-Usuario-Roles", "ABOGADO"))
				.andExpect(status().isForbidden());
		then(servicio).should(never()).validar(any(Integer.class), anyLong());
	}

	@Test
	void validarDosVecesEsUn409YFaltanCamposEsUn400() throws Exception {
		given(servicio.validar(4, 79456123L)).willThrow(new ConflictoException("La regla ya está validada"));
		mvc.perform(post("/reglas-termino/4/validacion").header("X-Usuario-Id", "79456123").header("X-Usuario-Roles", "SOCIO"))
				.andExpect(status().isConflict());

		mvc.perform(post("/reglas-termino").contentType(MediaType.APPLICATION_JSON).content("{\"dias\": 400}")
				.header("X-Usuario-Id", "79456123").header("X-Usuario-Roles", "SOCIO"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.dias").value("Máximo 365 días"))
				.andExpect(jsonPath("$.campos.patron").exists());
	}

	@Test
	void faltaLaIdentidadOUnParametroEsUn400ConElFormatoDeSiempre() throws Exception {
		mvc.perform(post("/reglas-termino/4/validacion").header("X-Usuario-Roles", "SOCIO"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Falta la cabecera X-Usuario-Id"));
		mvc.perform(get("/reglas-termino/prueba").param("patron", "x"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Falta el parámetro 'clase'"));
	}

	@Test
	void contestacionDiceCuantasReglasQuedaronSinValidar() throws Exception {
		given(servicio.cambiarContestacion((short) 1, 15, 79456123L)).willReturn(1);
		mvc.perform(put("/catalogos/tipos/1/contestacion").contentType(MediaType.APPLICATION_JSON).content("{\"dias\": 15}")
				.header("X-Usuario-Id", "79456123").header("X-Usuario-Roles", "SOCIO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reglasSinValidar").value(1));
	}

	@Test
	void verYProbarNoPidenRol() throws Exception {
		given(servicio.listar()).willReturn(java.util.List.of(vista(2)));
		given(servicio.probar("admite", "Admite demanda")).willReturn(new ReglaServicio.Prueba(true, "admite demanda"));
		given(servicio.historial(2)).willReturn(java.util.List.of());

		mvc.perform(get("/reglas-termino")).andExpect(jsonPath("$[0].norma").value("CGP art. 90"));
		mvc.perform(get("/reglas-termino/prueba").param("patron", "admite").param("clase", "Admite demanda"))
				.andExpect(jsonPath("$.coincide").value(true));
		mvc.perform(get("/reglas-termino/2/historial")).andExpect(status().isOk());
	}

}
