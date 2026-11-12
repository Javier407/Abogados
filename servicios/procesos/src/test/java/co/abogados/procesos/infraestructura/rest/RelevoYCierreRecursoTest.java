package co.abogados.procesos.infraestructura.rest;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import co.abogados.procesos.aplicacion.CierreServicio;
import co.abogados.procesos.aplicacion.RelevoServicio;
import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.Cierre;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Relevo;

/** La API del relevo del abogado a cargo y del cierre del proceso. */
@WebMvcTest(RelevoYCierreRecurso.class)
class RelevoYCierreRecursoTest {

	static final long CATALINA = 52345678L;
	static final long PEDRO = 79123456L;

	@Autowired
	MockMvc mvc;

	@MockBean RelevoServicio relevos;
	@MockBean CierreServicio cierres;

	static String json(String comillasSimples) {
		return comillasSimples.replace('\'', '"');
	}

	static ProcesoVista cerrado() {
		return new ProcesoVista(34, "11001310303220260050100", "11001-31-03-032-2026-00501-00", (short) 1, "Verbal",
				"CIVIL", "Civil y comercial", "Juzgado 32", null, "Juzgado 32", 0, 7, "DEMANDANTE", "Asunto",
				BigDecimal.ONE, "MAYOR", "REPRESENTACION", "TERMINADO", "CERRADO", LocalDate.of(2026, 3, 1), false,
				CATALINA, LocalDate.of(2026, 11, 11), "DESISTIMIENTO", "El cliente desistió", "PRUEBAS");
	}

	@Test
	void relevaAlResponsableYPorDefectoTrasladaLoPendienteYSacaAlAnterior() throws Exception {
		Relevo.Solicitud porDefecto = new Relevo.Solicitud(PEDRO, Relevo.Motivo.RENUNCIA, null, false, true);
		given(relevos.relevar(34, porDefecto, CATALINA)).willReturn(new Relevo.Resultado(CATALINA, PEDRO, 3, 1));

		mvc.perform(post("/procesos/34/relevo").header("X-Usuario-Id", CATALINA).contentType(MediaType.APPLICATION_JSON)
				.content(json("{'idResponsable':79123456,'motivo':'RENUNCIA'}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idNuevo").value(PEDRO))
				.andExpect(jsonPath("$.terminosTrasladados").value(3))
				.andExpect(jsonPath("$.audienciasTrasladadas").value(1));

		mvc.perform(post("/procesos/34/relevo").header("X-Usuario-Id", CATALINA).contentType(MediaType.APPLICATION_JSON)
				.content(json("{'idResponsable':79123456,'motivo':'REORGANIZACION','nota':'Carga','anteriorQuedaDeApoyo':true,"
						+ "'trasladarPendientes':false}")))
				.andExpect(status().isOk());
		verify(relevos).relevar(34, new Relevo.Solicitud(PEDRO, Relevo.Motivo.REORGANIZACION, "Carga", true, false), CATALINA);
	}

	@Test
	void elRelevoExigeAbogadoMotivoYQuienLoHace() throws Exception {
		mvc.perform(post("/procesos/34/relevo").header("X-Usuario-Id", CATALINA).contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.idResponsable").value("Elija el abogado que recibe el proceso"))
				.andExpect(jsonPath("$.campos.motivo").value("Indique el motivo del cambio"));
		mvc.perform(post("/procesos/34/relevo").header("X-Usuario-Id", CATALINA).contentType(MediaType.APPLICATION_JSON)
				.content(json("{'idResponsable':79123456,'motivo':'PORQUE_SI'}")))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/procesos/34/relevo").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'idResponsable':79123456,'motivo':'RENUNCIA'}")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void lasReglasDelRelevoLleganComo422() throws Exception {
		given(relevos.relevar(34, new Relevo.Solicitud(CATALINA, Relevo.Motivo.RENUNCIA, null, false, true), CATALINA))
				.willThrow(new ReglaNegocioException("Ese abogado ya está a cargo del proceso"));

		mvc.perform(post("/procesos/34/relevo").header("X-Usuario-Id", CATALINA).contentType(MediaType.APPLICATION_JSON)
				.content(json("{'idResponsable':52345678,'motivo':'RENUNCIA'}")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value(containsString("ya está a cargo")));
	}

	@Test
	void elHistorialDelEquipoTraeLosPeriodosConSuMotivo() throws Exception {
		given(relevos.historial(34)).willReturn(List.of(
				new Relevo.Periodo(2, PEDRO, "RESPONSABLE", LocalDate.of(2026, 11, 11), null, null, null, 19000111L, null,
						"Pedro Rincón"),
				new Relevo.Periodo(1, CATALINA, "RESPONSABLE", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 11),
						"RENUNCIA", "Renunció al poder", null, 19000111L, "Catalina Gómez")));

		mvc.perform(get("/procesos/34/asignaciones/historial"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nombre").value("Pedro Rincón"))
				.andExpect(jsonPath("$[0].hasta").doesNotExist())
				.andExpect(jsonPath("$[1].motivo").value("RENUNCIA"))
				.andExpect(jsonPath("$[1].hasta").value("2026-11-11"));
	}

	@Test
	void cierraElProcesoYDiceLoQueQuedoSinEfecto() throws Exception {
		given(cierres.cerrar(34, Cierre.Motivo.DESISTIMIENTO, null, "El cliente desistió", CATALINA))
				.willReturn(new Cierre.Resultado(cerrado(), new Cierre.Efectos(2, 1, true)));

		mvc.perform(post("/procesos/34/cierre").header("X-Usuario-Id", CATALINA).contentType(MediaType.APPLICATION_JSON)
				.content(json("{'motivo':'DESISTIMIENTO','nota':'El cliente desistió'}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.proceso.estado").value("TERMINADO"))
				.andExpect(jsonPath("$.proceso.motivoCierre").value("DESISTIMIENTO"))
				.andExpect(jsonPath("$.proceso.fechaCierre").value("2026-11-11"))
				.andExpect(jsonPath("$.efectos.terminosPendientes").value(2))
				.andExpect(jsonPath("$.efectos.seApagoLaVigilancia").value(true));

		mvc.perform(post("/procesos/34/cierre").header("X-Usuario-Id", CATALINA).contentType(MediaType.APPLICATION_JSON)
				.content(json("{'motivo':'SENTENCIA','fecha':'2026-11-10'}")))
				.andExpect(status().isOk());
		verify(cierres).cerrar(34, Cierre.Motivo.SENTENCIA, LocalDate.of(2026, 11, 10), null, CATALINA);
	}

	@Test
	void elCierreExigeMotivoYNoSeRepite() throws Exception {
		mvc.perform(post("/procesos/34/cierre").header("X-Usuario-Id", CATALINA).contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.motivo").value("Indique por qué se cierra el proceso"));

		given(cierres.cerrar(34, Cierre.Motivo.PAGO, null, null, CATALINA))
				.willThrow(new ConflictoException("El proceso ya está cerrado"));
		mvc.perform(post("/procesos/34/cierre").header("X-Usuario-Id", CATALINA).contentType(MediaType.APPLICATION_JSON)
				.content(json("{'motivo':'PAGO'}")))
				.andExpect(status().isConflict());
	}

	@Test
	void reabreSinCuerpoOIndicandoLaEtapa() throws Exception {
		mvc.perform(post("/procesos/34/reapertura").header("X-Usuario-Id", CATALINA)).andExpect(status().isOk());
		verify(cierres).reabrir(34, null, CATALINA);

		mvc.perform(post("/procesos/34/reapertura").header("X-Usuario-Id", CATALINA).contentType(MediaType.APPLICATION_JSON)
				.content(json("{'etapa':'RECURSO'}")))
				.andExpect(status().isOk());
		verify(cierres).reabrir(34, EtapaProceso.RECURSO, CATALINA);
	}

}
