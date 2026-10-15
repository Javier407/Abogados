package co.abogados.procesos.infraestructura.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.procesos.aplicacion.ProcesoServicio;
import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.procesos.dominio.modelo.FiltroProcesos;
import co.abogados.procesos.dominio.modelo.ProcesoVista;

/** La API: códigos HTTP, validación de la petición y formato de los errores. */
@WebMvcTest(ProcesosRecurso.class)
class ProcesosRecursoTest {

	static final String VALIDO = """
			{ "radicado": "11001-31-03-032-2026-00501-00", "idTipo": 1, "idCliente": 7,
			  "rolCliente": "DEMANDANTE", "asunto": "Incumplimiento de contrato", "cuantia": 320000000,
			  "claseCuantia": "MAYOR", "tipoServicio": "REPRESENTACION", "etapa": "DEMANDA",
			  "fechaRadicacion": "2026-09-01", "idResponsable": 52345678 }
			""";

	@Autowired
	MockMvc mvc;

	@MockBean
	ProcesoServicio servicio;

	static ProcesoVista vista(int id) {
		return new ProcesoVista(id, "11001310303220260050100", "11001-31-03-032-2026-00501-00", (short) 1, "Verbal",
				"CIVIL", "Civil y comercial", "Juzgado 032 Civil del Circuito de Bogotá", null,
				"Juzgado 032 Civil del Circuito de Bogotá", 0, 7, "DEMANDANTE", "Incumplimiento de contrato",
				new BigDecimal("320000000"), "MAYOR", "REPRESENTACION", "ACTIVO", "DEMANDA", LocalDate.of(2026, 9, 1),
				true, 52345678L);
	}

	@Test
	void crearDevuelve201ConLocation() throws Exception {
		given(servicio.crear(any())).willReturn(vista(31));

		mvc.perform(post("/procesos").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/procesos/31"))
				.andExpect(jsonPath("$.radicadoLegible").value("11001-31-03-032-2026-00501-00"))
				.andExpect(jsonPath("$.idDespachoActual").doesNotExist());
	}

	@Test
	void unRadicadoMalFormadoEsUn422() throws Exception {
		String malo = VALIDO.replace("11001-31-03-032-2026-00501-00", "11001-31-03");

		mvc.perform(post("/procesos").contentType(MediaType.APPLICATION_JSON).content(malo))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("23 dígitos")));
	}

	@Test
	void faltanCamposObligatoriosEsUn400ConElDetalle() throws Exception {
		mvc.perform(post("/procesos").contentType(MediaType.APPLICATION_JSON).content("{\"asunto\": \"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.radicado").value("El radicado es obligatorio"))
				.andExpect(jsonPath("$.campos.idResponsable").exists());
	}

	@Test
	void unValorDeListaInexistenteEsUn400() throws Exception {
		mvc.perform(post("/procesos").contentType(MediaType.APPLICATION_JSON)
				.content(VALIDO.replace("\"MAYOR\"", "\"GIGANTE\"")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void radicadoRepetidoEsUn409() throws Exception {
		given(servicio.crear(any())).willThrow(new ConflictoException("ya está registrado"));

		mvc.perform(post("/procesos").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("ya está registrado"));
	}

	@Test
	void gabineteCaidoEsUn503() throws Exception {
		given(servicio.crear(any())).willThrow(new ServicioNoDisponibleException("gabinete no responde"));

		mvc.perform(post("/procesos").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void listarPasaLosFiltros() throws Exception {
		given(servicio.listar(eq(new FiltroProcesos(co.abogados.procesos.dominio.modelo.EstadoProceso.ACTIVO,
				"LABORAL", null, 52345678L, null)))).willReturn(List.of(vista(12)));

		mvc.perform(get("/procesos").param("estado", "ACTIVO").param("rama", "LABORAL").param("idAbogado", "52345678"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].idProceso").value(12));
	}

	@Test
	void unEstadoInexistenteEnElFiltroEsUn400() throws Exception {
		mvc.perform(get("/procesos").param("estado", "CERRADITO"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("estado")));
	}

}
