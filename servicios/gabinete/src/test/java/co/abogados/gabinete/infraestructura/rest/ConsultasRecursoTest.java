package co.abogados.gabinete.infraestructura.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.gabinete.aplicacion.ConsultaServicio;
import co.abogados.gabinete.aplicacion.ConsultaServicio.ConsultaConAvisos;
import co.abogados.gabinete.dominio.modelo.Consulta;

@WebMvcTest(ConsultasRecurso.class)
class ConsultasRecursoTest {

	static final Consulta ABIERTA = new Consulta(7, LocalDate.of(2026, 10, 20), "Pedro Gómez", null, null, null, null,
			"Constructora Altamira SAS", null, "LABORAL", "Despido sin justa causa", null, null, null, null, null, 1L);

	@Autowired
	MockMvc mvc;

	@MockBean
	ConsultaServicio servicio;

	@Test
	void registrarDevuelve201ConLosAvisosDeConflicto() throws Exception {
		given(servicio.registrar(any(), eq(1L)))
				.willReturn(new ConsultaConAvisos(ABIERTA, List.of("La contraparte es cliente de la firma")));

		mvc.perform(post("/consultas").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON).content("""
				{ "nombre": "Pedro Gómez", "asunto": "Despido sin justa causa",
				  "contraparte": "Constructora Altamira SAS", "codigoRama": "LABORAL" }
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/consultas/7"))
				.andExpect(jsonPath("$.consulta.estado").value("ABIERTA"))
				.andExpect(jsonPath("$.avisos[0]").value("La contraparte es cliente de la firma"));

		verify(servicio).registrar(argThat(c -> c.registradaPor() == 1L && c.idConsulta() == null), eq(1L));
	}

	@Test
	void sinAsuntoEsUn400YSinUsuarioTambien() throws Exception {
		mvc.perform(post("/consultas").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"nombre\": \"Pedro Gómez\" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.asunto").value("Describa el asunto"));
		mvc.perform(post("/consultas").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"nombre\": \"Pedro Gómez\", \"asunto\": \"Despido\" }"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(servicio);
	}

	@Test
	void unDocumentoSinTipoEsUn422() throws Exception {
		mvc.perform(post("/consultas").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON).content("""
				{ "nombre": "Pedro Gómez", "asunto": "Despido", "numeroDocumento": "80123456" }
				"""))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void contratarYCerrar() throws Exception {
		given(servicio.contratar(eq(7), any(), eq(1L))).willReturn(new ConsultaConAvisos(ABIERTA, List.of()));
		given(servicio.cerrar(7, Consulta.Estado.REMITIDA, "Fuera de nuestras áreas", 1L))
				.willReturn(new ConsultaConAvisos(ABIERTA, List.of()));

		mvc.perform(post("/consultas/7/contratacion").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"ciudad\": \"Bogotá\", \"idSede\": 1 }"))
				.andExpect(status().isOk());
		verify(servicio).contratar(eq(7), argThat(d -> d.idSede() == 1 && d.ciudad().equals("Bogotá")), eq(1L));

		mvc.perform(post("/consultas/7/contratacion").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"ciudad\": \"Bogotá\" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.idSede").value("Elija la sede"));

		mvc.perform(post("/consultas/7/cierre").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"estado\": \"REMITIDA\", \"motivo\": \"Fuera de nuestras áreas\" }"))
				.andExpect(status().isOk());
	}

	@Test
	void listarYObtener() throws Exception {
		given(servicio.listar(Consulta.Estado.ABIERTA)).willReturn(List.of(ABIERTA));
		given(servicio.obtener(7)).willReturn(new ConsultaConAvisos(ABIERTA, List.of()));

		mvc.perform(get("/consultas").param("estado", "ABIERTA"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nombre").value("Pedro Gómez"));
		mvc.perform(get("/consultas/7")).andExpect(status().isOk()).andExpect(jsonPath("$.consulta.idConsulta").value(7));
		mvc.perform(get("/consultas/siete")).andExpect(status().isBadRequest());
	}

}
