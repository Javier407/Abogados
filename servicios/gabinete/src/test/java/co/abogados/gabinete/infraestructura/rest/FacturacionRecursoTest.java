package co.abogados.gabinete.infraestructura.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.gabinete.aplicacion.FacturacionServicio;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.ConfigFacturacion;
import co.abogados.gabinete.dominio.modelo.Factura;
import co.abogados.gabinete.dominio.puertos.FacturacionRepositorio;

/** F14: la API de facturación. */
@WebMvcTest(FacturacionRecurso.class)
class FacturacionRecursoTest {

	static final String JSON = MediaType.APPLICATION_JSON_VALUE;

	@Autowired
	MockMvc mvc;

	@MockBean
	FacturacionServicio servicio;

	static FacturacionServicio.Detalle detalle() {
		List<Factura.Linea> lineas = List.of(Factura.Linea.nueva(1, Factura.TipoLinea.SERVICIO, "Honorarios", BigDecimal.ONE,
				new BigDecimal("1000"), new BigDecimal("19"), null, null));
		Factura f = new Factura(12, "SETP", 990000000, "1876", "PRUEBAS", 1, null, OffsetDateTime.parse("2026-10-22T10:00:00-05:00"),
				LocalDate.of(2026, 11, 21), Factura.FormaPago.CREDITO, Factura.Totales.de(lineas, BigDecimal.ZERO), null,
				"a".repeat(96), Factura.Estado.ACEPTADA, "ok", 5L,
				new Factura.Adquirente("Andina", "NIT", "900456789", "Calle 80", "Bogotá", "a@b.co"), lineas, null);
		return new FacturacionServicio.Detalle(f, new FacturacionRepositorio.Emisor("901234567-8", "Firma", "Cra 7", "Bogotá",
				null, null), null, "NumFac: SETP990000000", "https://catalogo-vpfe-hab.dian.gov.co/document/searchqr?documentkey=a");
	}

	@Test
	void emitirDevuelve201ConLaFacturaYQuienLaEmitio() throws Exception {
		given(servicio.emitir(any(), eq(79876543L))).willReturn(detalle());

		mvc.perform(post("/facturas").header("X-Usuario-Id", "79876543").contentType(JSON).content("""
				{ "idCliente": 1, "formaPago": "CREDITO",
				  "lineas": [ { "tipo": "SERVICIO", "descripcion": "Honorarios", "valorUnitario": 1000 },
				              { "tipo": "REEMBOLSO", "idGasto": 11 } ] }
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/facturas/12"))
				.andExpect(jsonPath("$.factura.totales.total").value(1190.0))
				.andExpect(jsonPath("$.textoQr").value("NumFac: SETP990000000"));

		ArgumentCaptor<FacturacionServicio.Solicitud> s = ArgumentCaptor.forClass(FacturacionServicio.Solicitud.class);
		then(servicio).should().emitir(s.capture(), eq(79876543L));
		org.assertj.core.api.Assertions.assertThat(s.getValue().lineas().get(0).cantidad()).isEqualByComparingTo("1");
	}

	@Test
	void faltanDatosEs400YUnaReglaEs422() throws Exception {
		mvc.perform(post("/facturas").header("X-Usuario-Id", "1").contentType(JSON).content("{ \"lineas\": [] }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.idCliente").value("Elija el cliente"));
		mvc.perform(post("/facturas").header("X-Usuario-Id", "1").contentType(JSON)
				.content("{ \"idCliente\": 1, \"retencionPct\": 150, \"lineas\": [ { \"tipo\": \"SERVICIO\" } ] }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.retencionPct").exists());
		given(servicio.anular(eq(12), any(), eq(1L))).willThrow(new ReglaNegocioException("Solo se anula una factura aceptada"));
		mvc.perform(post("/facturas/12/anulacion").header("X-Usuario-Id", "1").contentType(JSON).content("{ \"motivo\": \"x\" }"))
				.andExpect(status().isUnprocessableEntity());
		mvc.perform(post("/facturas/12/anulacion").header("X-Usuario-Id", "1").contentType(JSON).content("{ }"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void consultas() throws Exception {
		given(servicio.listar(1, Factura.Estado.ACEPTADA)).willReturn(List.of(detalle().factura()));
		given(servicio.detalle(12)).willReturn(detalle());
		given(servicio.pendientes(1)).willReturn(List.of(new FacturacionServicio.Pendiente(Factura.TipoLinea.REEMBOLSO,
				"Reembolso: copias", new BigDecimal("85000"), null, 11, 7)));
		given(servicio.vistaPrevia(any())).willReturn(new FacturacionServicio.VistaPrevia(List.of(), null, new BigDecimal("11")));

		mvc.perform(get("/facturas").param("idCliente", "1").param("estado", "ACEPTADA"))
				.andExpect(jsonPath("$[0].prefijo").value("SETP"));
		mvc.perform(get("/facturas/12")).andExpect(jsonPath("$.emisor.nit").value("901234567-8"));
		mvc.perform(get("/facturas/pendientes").param("idCliente", "1")).andExpect(jsonPath("$[0].idGasto").value(11));
		mvc.perform(post("/facturas/vista-previa").contentType(JSON)
				.content("{ \"idCliente\": 1, \"lineas\": [ { \"tipo\": \"SERVICIO\", \"descripcion\": \"x\", \"valorUnitario\": 5 } ] }"))
				.andExpect(jsonPath("$.retencionSugerida").value(11));
	}

	@Test
	void configuracion() throws Exception {
		ConfigFacturacion c = new ConfigFacturacion(true, new BigDecimal("19"), new BigDecimal("11"), BigDecimal.ZERO, 30,
				"1876", "SETP", 1, 100, 1, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "NC", 1, "PRUEBAS", "SIMULADO",
				null, null);
		FacturacionServicio.EstadoConfig estado = new FacturacionServicio.EstadoConfig(c, 100, null, "SIMULADO");
		given(servicio.estadoConfig()).willReturn(estado);
		given(servicio.guardarConfig(any(), eq(52345678L))).willReturn(estado);
		given(servicio.validarConfig(52345678L)).willReturn(estado);

		mvc.perform(get("/facturacion/config")).andExpect(jsonPath("$.restantes").value(100));
		mvc.perform(put("/facturacion/config").header("X-Usuario-Id", "52345678").contentType(JSON).content("""
				{ "responsableIva": true, "tarifaIva": 19, "retencionJuridica": 11, "retencionNatural": 0, "diasCredito": 30,
				  "resolucion": "1876", "prefijo": "setp", "rangoDesde": 1, "rangoHasta": 100, "siguiente": 1,
				  "vigenteDesde": "2026-01-01", "vigenteHasta": "2027-01-01", "prefijoNota": "nc", "siguienteNota": 1,
				  "ambiente": "pruebas", "proveedor": "simulado" }
				""")).andExpect(status().isOk());
		ArgumentCaptor<ConfigFacturacion> g = ArgumentCaptor.forClass(ConfigFacturacion.class);
		then(servicio).should().guardarConfig(g.capture(), eq(52345678L));
		org.assertj.core.api.Assertions.assertThat(g.getValue().prefijo()).isEqualTo("SETP");
		org.assertj.core.api.Assertions.assertThat(g.getValue().ambiente()).isEqualTo("PRUEBAS");
		mvc.perform(post("/facturacion/config/validacion").header("X-Usuario-Id", "52345678")).andExpect(status().isOk());
	}

}
