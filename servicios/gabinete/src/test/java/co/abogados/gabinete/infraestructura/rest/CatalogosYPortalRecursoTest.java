package co.abogados.gabinete.infraestructura.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import co.abogados.gabinete.aplicacion.CatalogoServicio;
import co.abogados.gabinete.aplicacion.PortalFinanzasServicio;
import co.abogados.gabinete.aplicacion.TarifaServicio;
import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Sede;
import co.abogados.gabinete.dominio.modelo.TarifaVista;

@WebMvcTest({ CatalogosRecurso.class, PortalRecurso.class })
class CatalogosYPortalRecursoTest {

	static final Sede CALI = new Sede((short) 3, "Sede Cali", "Cali", "Avenida 6N", null, false);
	static final Cargo PRACTICANTE = new Cargo((short) 7, "PRACTICANTE", "Practicante", false);
	static final AreaPractica TRIBUTARIO = new AreaPractica((short) 4, "TRIBUTARIO", "Tributario", 52345678L);

	@Autowired
	MockMvc mvc;

	@MockBean
	CatalogoServicio servicio;

	@MockBean
	TarifaServicio tarifas;

	@MockBean
	PortalFinanzasServicio portal;

	@Test
	void tarifasDelAnioPedido() throws Exception {
		given(tarifas.listar(2026)).willReturn(List.of(new TarifaVista((short) 1, "CONALBOS", "FAMILIA",
				"Divorcio contencioso", "CUALQUIERA", "FIJO", new BigDecimal("5.00"), null, null, null,
				LocalDate.of(2026, 1, 1), null, 2026, new BigDecimal("8754525"), null)));

		mvc.perform(get("/gabinete/tarifas").param("anio", "2026"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].desdePesos").value(8754525));
	}

	@Test
	void sedesCrearCambiarYBorrar() throws Exception {
		given(servicio.sedes()).willReturn(List.of(CALI));
		given(servicio.guardarSede(isNull(), any(), eq(1L))).willReturn(CALI);
		given(servicio.guardarSede(eq((short) 3), any(), eq(1L))).willReturn(CALI);
		String cuerpo = "{ \"idSede\": 99, \"nombre\": \"Sede Cali\", \"ciudad\": \"Cali\", \"direccion\": \"Avenida 6N\" }";

		mvc.perform(get("/gabinete/sedes")).andExpect(status().isOk()).andExpect(jsonPath("$[0].ciudad").value("Cali"));
		mvc.perform(post("/gabinete/sedes").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.idSede").value(3));
		mvc.perform(put("/gabinete/sedes/3").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isOk());
		mvc.perform(delete("/gabinete/sedes/3").header("X-Usuario-Id", "1")).andExpect(status().isNoContent());
		verify(servicio).eliminarSede((short) 3, 1L);
	}

	@Test
	void borrarLaSedePrincipalEsUn409YSinUsuarioUn400() throws Exception {
		willThrow(new ConflictoException("La sede principal no se borra")).given(servicio).eliminarSede((short) 1, 1L);

		mvc.perform(delete("/gabinete/sedes/1").header("X-Usuario-Id", "1"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("La sede principal no se borra"));
		mvc.perform(delete("/gabinete/sedes/2")).andExpect(status().isBadRequest());
	}

	@Test
	void cargosCrearCambiarYBorrar() throws Exception {
		given(servicio.cargos()).willReturn(List.of(PRACTICANTE));
		given(servicio.guardarCargo(isNull(), any(), eq(1L))).willReturn(PRACTICANTE);
		given(servicio.guardarCargo(eq((short) 7), any(), eq(1L))).willReturn(PRACTICANTE);
		String cuerpo = "{ \"codigo\": \"practicante\", \"nombre\": \"Practicante\", \"esAbogado\": false }";

		mvc.perform(get("/gabinete/cargos")).andExpect(status().isOk()).andExpect(jsonPath("$[0].codigo").value("PRACTICANTE"));
		mvc.perform(post("/gabinete/cargos").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isCreated());
		mvc.perform(put("/gabinete/cargos/7").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isOk());
		verify(servicio).guardarCargo(eq((short) 7), argThat(c -> c.codigo().equals("practicante")), eq(1L));
		mvc.perform(delete("/gabinete/cargos/7").header("X-Usuario-Id", "1")).andExpect(status().isNoContent());
		verify(servicio).eliminarCargo((short) 7, 1L);
	}

	@Test
	void areasCrearCambiarYBorrar() throws Exception {
		given(servicio.areas()).willReturn(List.of(TRIBUTARIO));
		given(servicio.guardarArea(isNull(), any(), eq(1L))).willReturn(TRIBUTARIO);
		given(servicio.guardarArea(eq((short) 4), any(), eq(1L))).willReturn(TRIBUTARIO);
		String cuerpo = "{ \"codigoRama\": \"TRIBUTARIO\", \"nombre\": \"Tributario\", \"idDirector\": 52345678 }";

		mvc.perform(get("/gabinete/areas")).andExpect(status().isOk()).andExpect(jsonPath("$[0].idDirector").value(52345678));
		mvc.perform(post("/gabinete/areas").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isCreated());
		mvc.perform(put("/gabinete/areas/4").header("X-Usuario-Id", "1").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isOk());
		mvc.perform(delete("/gabinete/areas/4").header("X-Usuario-Id", "1")).andExpect(status().isNoContent());
		verify(servicio).eliminarArea((short) 4, 1L);
	}

	@Test
	void elPortalEsSoloParaClientes() throws Exception {
		mvc.perform(get("/portal/finanzas").header("X-Usuario-Roles", "CLIENTE"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("El portal es solo para clientes"));
		mvc.perform(get("/portal/finanzas").header("X-Cliente-Id", "12"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/portal/finanzas").header("X-Cliente-Id", "12").header("X-Usuario-Roles", "ABOGADO,SOCIO"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(portal);
	}

	@Test
	void elClienteVeSusFinanzas() throws Exception {
		given(portal.deCliente(12)).willReturn(new PortalFinanzasServicio.Resumen(List.of(), List.of(),
				new BigDecimal("5000000"), BigDecimal.ZERO));

		mvc.perform(get("/portal/finanzas").header("X-Cliente-Id", "12").header("X-Usuario-Roles", "CLIENTE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.saldoHonorarios").value(5000000));
		verify(portal).deCliente(12);
	}

}
