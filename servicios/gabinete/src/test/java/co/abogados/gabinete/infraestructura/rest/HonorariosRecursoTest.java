package co.abogados.gabinete.infraestructura.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import co.abogados.gabinete.aplicacion.HonorarioServicio;
import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.EstadoHonorario;
import co.abogados.gabinete.dominio.modelo.FormaCobro;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.MedioPago;
import co.abogados.gabinete.dominio.modelo.Pago;

@WebMvcTest(HonorariosRecurso.class)
class HonorariosRecursoTest {

	static final HonorarioVista ACUERDO = new HonorarioVista(5, 34, 12, "María Fernanda López", null, "FIJO",
			new BigDecimal("8000000"), null, null, LocalDate.of(2026, 10, 1), "VIGENTE", new BigDecimal("3000000"),
			new BigDecimal("5000000"), 1);

	@Autowired
	MockMvc mvc;

	@MockBean
	HonorarioServicio servicio;

	@Test
	void crearUnAcuerdoDevuelve201ConSuUbicacion() throws Exception {
		given(servicio.crear(any())).willReturn(ACUERDO);

		mvc.perform(post("/honorarios").contentType(MediaType.APPLICATION_JSON).content("""
				{ "idProceso": 34, "idCliente": 12, "formaCobro": "FIJO", "valorFijo": 8000000,
				  "fechaPacto": "2026-10-01" }
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/honorarios/5"))
				.andExpect(jsonPath("$.saldo").value(5000000));

		verify(servicio).crear(argThat(h -> h.formaCobro() == FormaCobro.FIJO && h.estado() == EstadoHonorario.VIGENTE
				&& h.idCliente() == 12));
	}

	@Test
	void sinFormaDeCobroNiFechaEsUn400ConLosCampos() throws Exception {
		mvc.perform(post("/honorarios").contentType(MediaType.APPLICATION_JSON).content("""
				{ "idProceso": 34, "idCliente": 12 }
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.formaCobro").value("La forma de cobro es obligatoria"))
				.andExpect(jsonPath("$.campos.fechaPacto").value("La fecha del acuerdo es obligatoria"));
		verifyNoInteractions(servicio);
	}

	@Test
	void unaFechaMalEscritaEsUn400() throws Exception {
		mvc.perform(post("/honorarios").contentType(MediaType.APPLICATION_JSON).content("""
				{ "idProceso": 34, "idCliente": 12, "formaCobro": "FIJO", "fechaPacto": "01/10/2026" }
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("AAAA-MM-DD")));
	}

	@Test
	void listarFiltraPorClienteYEstado() throws Exception {
		given(servicio.listar(12, null, EstadoHonorario.VIGENTE)).willReturn(List.of(ACUERDO));

		mvc.perform(get("/honorarios").param("idCliente", "12").param("estado", "VIGENTE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].idHonorario").value(5));
	}

	@Test
	void unEstadoDesconocidoEnElFiltroEsUn400() throws Exception {
		mvc.perform(get("/honorarios").param("estado", "PAGADO"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Valor inválido para 'estado': PAGADO"));
	}

	@Test
	void registrarUnPagoDevuelve201YElAcuerdoActualizado() throws Exception {
		given(servicio.registrarPago(eq(5), any())).willReturn(ACUERDO);

		mvc.perform(post("/honorarios/5/pagos").contentType(MediaType.APPLICATION_JSON).content("""
				{ "fecha": "2026-10-09", "valor": 3000000, "medio": "TRANSFERENCIA", "referencia": " TRX-1 " }
				"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pagado").value(3000000));

		verify(servicio).registrarPago(eq(5), argThat(p -> p.idHonorario() == 5 && "TRX-1".equals(p.referencia())
				&& p.medio() == MedioPago.TRANSFERENCIA));
	}

	@Test
	void unPagoEnCeroEsUn400() throws Exception {
		mvc.perform(post("/honorarios/5/pagos").contentType(MediaType.APPLICATION_JSON).content("""
				{ "fecha": "2026-10-09", "valor": 0, "medio": "EFECTIVO" }
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.valor").value("El valor debe ser mayor que cero"));
	}

	@Test
	void unPagoQueSuperaElSaldoEsUn422() throws Exception {
		given(servicio.registrarPago(eq(5), any()))
				.willThrow(new ReglaNegocioException("El pago supera el saldo del acuerdo"));

		mvc.perform(post("/honorarios/5/pagos").contentType(MediaType.APPLICATION_JSON).content("""
				{ "fecha": "2026-10-09", "valor": 99000000, "medio": "EFECTIVO" }
				"""))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("El pago supera el saldo del acuerdo"));
	}

	@Test
	void anularUnPagoTomaElUsuarioDeLaCabecera() throws Exception {
		given(servicio.anularPago(5, 9, "Pago duplicado", 79123456L)).willReturn(ACUERDO);

		mvc.perform(post("/honorarios/5/pagos/9/anulacion").header("X-Usuario-Id", "79123456")
				.contentType(MediaType.APPLICATION_JSON).content("{ \"motivo\": \"Pago duplicado\" }"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idHonorario").value(5));
	}

	@Test
	void anularSinMotivoEsUn400YUnaAnulacionRepetidaUn409() throws Exception {
		mvc.perform(post("/honorarios/5/pagos/9/anulacion").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"motivo\": \"  \" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.motivo").value("Escriba el motivo de la anulación"));

		given(servicio.anularPago(5, 9, "Otra vez", null)).willThrow(new ConflictoException("El pago ya está anulado"));
		mvc.perform(post("/honorarios/5/pagos/9/anulacion").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"motivo\": \"Otra vez\" }"))
				.andExpect(status().isConflict());
	}

	@Test
	void liquidarYCambiarElEstado() throws Exception {
		given(servicio.liquidar(5, new BigDecimal("100000000"), LocalDate.of(2026, 10, 20))).willReturn(ACUERDO);
		given(servicio.cambiarEstado(5, EstadoHonorario.CUMPLIDO)).willReturn(ACUERDO);

		mvc.perform(post("/honorarios/5/liquidacion").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"valorBase\": 100000000, \"fecha\": \"2026-10-20\" }"))
				.andExpect(status().isOk());
		mvc.perform(post("/honorarios/5/liquidacion").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"valorBase\": -1 }"))
				.andExpect(status().isBadRequest());
		mvc.perform(put("/honorarios/5/estado").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"estado\": \"CUMPLIDO\" }"))
				.andExpect(status().isOk());
		mvc.perform(put("/honorarios/5/estado").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.estado").value("El estado es obligatorio"));
	}

	@Test
	void obtenerElAcuerdoYSusPagos() throws Exception {
		given(servicio.obtener(5)).willReturn(ACUERDO);
		given(servicio.pagos(5)).willReturn(List.of(new Pago(1, 5, LocalDate.of(2026, 10, 2),
				new BigDecimal("3000000"), MedioPago.TRANSFERENCIA, "TRX-1")));

		mvc.perform(get("/honorarios/5")).andExpect(status().isOk()).andExpect(jsonPath("$.cliente").value("María Fernanda López"));
		mvc.perform(get("/honorarios/5/pagos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].referencia").value("TRX-1"))
				.andExpect(jsonPath("$[0].anuladoEn").doesNotExist());
	}

}
