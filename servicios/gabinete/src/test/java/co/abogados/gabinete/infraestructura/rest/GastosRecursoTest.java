package co.abogados.gabinete.infraestructura.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import co.abogados.gabinete.aplicacion.GastoServicio;
import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.gabinete.dominio.modelo.CarteraCliente;
import co.abogados.gabinete.dominio.modelo.Gasto;

@WebMvcTest(GastosRecurso.class)
class GastosRecursoTest {

	static final Gasto COPIAS = new Gasto(8, 34, 12, LocalDate.of(2026, 10, 5), "Copias del expediente",
			new BigDecimal("45000"), Gasto.PagadoPor.FIRMA, true, null, null, 79123456L);

	@Autowired
	MockMvc mvc;

	@MockBean
	GastoServicio servicio;

	@Test
	void registrarUnGastoDevuelve201YDejaElClienteAlServicio() throws Exception {
		given(servicio.registrar(any(), eq(79123456L))).willReturn(COPIAS);

		mvc.perform(post("/gastos").header("X-Usuario-Id", "79123456").contentType(MediaType.APPLICATION_JSON).content("""
				{ "idProceso": 34, "fecha": "2026-10-05", "concepto": "Copias del expediente", "valor": 45000,
				  "pagadoPor": "FIRMA", "reembolsable": true }
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/gastos/8"))
				.andExpect(jsonPath("$.idCliente").value(12));

		verify(servicio).registrar(argThat(g -> g.idCliente() == 0 && g.registradoPor() == 79123456L && g.porReembolsar()),
				eq(79123456L));
	}

	@Test
	void sinQuienLoPagoEsUn400() throws Exception {
		mvc.perform(post("/gastos").contentType(MediaType.APPLICATION_JSON).content("""
				{ "idProceso": 34, "fecha": "2026-10-05", "concepto": "Copias", "valor": 45000 }
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.pagadoPor").value("Indique quién lo pagó"));
		verifyNoInteractions(servicio);
	}

	@Test
	void unGastoDelClienteNoPuedeSerReembolsable() throws Exception {
		mvc.perform(post("/gastos").contentType(MediaType.APPLICATION_JSON).content("""
				{ "idProceso": 34, "fecha": "2026-10-05", "concepto": "Póliza", "valor": 300000,
				  "pagadoPor": "CLIENTE", "reembolsable": true }
				"""))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("Solo se reembolsa lo que adelantó la firma"));
		verifyNoInteractions(servicio);
	}

	@Test
	void siProcesosNoRespondeEsUn503() throws Exception {
		given(servicio.registrar(any(), any())).willThrow(new ServicioNoDisponibleException("procesos no responde"));

		mvc.perform(post("/gastos").contentType(MediaType.APPLICATION_JSON).content("""
				{ "idProceso": 34, "fecha": "2026-10-05", "concepto": "Copias", "valor": 45000, "pagadoPor": "FIRMA" }
				"""))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void reembolsarConYSinFecha() throws Exception {
		given(servicio.marcarReembolsado(eq(8), any())).willReturn(COPIAS);

		mvc.perform(post("/gastos/8/reembolso")).andExpect(status().isOk());
		verify(servicio).marcarReembolsado(8, null);

		mvc.perform(post("/gastos/8/reembolso").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"fecha\": \"2026-10-20\" }"))
				.andExpect(status().isOk());
		verify(servicio).marcarReembolsado(8, LocalDate.of(2026, 10, 20));
	}

	@Test
	void eliminarDevuelve204O404O409() throws Exception {
		mvc.perform(delete("/gastos/8")).andExpect(status().isNoContent());
		verify(servicio).eliminar(8);

		willThrow(new RecursoNoEncontradoException("No existe el gasto 9")).given(servicio).eliminar(9);
		mvc.perform(delete("/gastos/9")).andExpect(status().isNotFound());

		willThrow(new ConflictoException("Un gasto reembolsado no se borra")).given(servicio).eliminar(10);
		mvc.perform(delete("/gastos/10")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Un gasto reembolsado no se borra"));
	}

	@Test
	void listarYCartera() throws Exception {
		given(servicio.listar(34, null)).willReturn(List.of(COPIAS));
		given(servicio.cartera()).willReturn(List.of(new CarteraCliente(12, "María Fernanda López", 1,
				new BigDecimal("8000000"), new BigDecimal("3000000"), new BigDecimal("5000000"), new BigDecimal("45000"),
				new BigDecimal("5045000"), LocalDate.of(2026, 10, 2), 18L)));

		mvc.perform(get("/gastos").param("idProceso", "34"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].concepto").value("Copias del expediente"));
		mvc.perform(get("/honorarios/cartera"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].totalPorCobrar").value(5045000))
				.andExpect(jsonPath("$[0].diasSinPagar").value(18));
	}

}
