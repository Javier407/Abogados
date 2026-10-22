package co.abogados.procesos.infraestructura.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.procesos.aplicacion.AgendaServicio;
import co.abogados.procesos.aplicacion.CalendarioServicio;
import co.abogados.procesos.aplicacion.CatalogoServicio;
import co.abogados.procesos.aplicacion.EquipoServicio;
import co.abogados.procesos.aplicacion.ExpedienteServicio;
import co.abogados.procesos.aplicacion.PortalServicio;
import co.abogados.procesos.aplicacion.ProvidenciaServicio;
import co.abogados.procesos.aplicacion.PruebaServicio;
import co.abogados.procesos.aplicacion.SugerenciaServicio;
import co.abogados.procesos.aplicacion.TerminoServicio;
import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.Actuacion;
import co.abogados.procesos.dominio.modelo.Asignacion;
import co.abogados.procesos.dominio.modelo.AudienciaVista;
import co.abogados.procesos.dominio.modelo.ConflictoInteres;
import co.abogados.procesos.dominio.modelo.Despacho;
import co.abogados.procesos.dominio.modelo.DiaInhabil;
import co.abogados.procesos.dominio.modelo.EventoExpediente;
import co.abogados.procesos.dominio.modelo.FiltroTerminos;
import co.abogados.procesos.dominio.modelo.Instancia;
import co.abogados.procesos.dominio.modelo.NuevaProvidencia;
import co.abogados.procesos.dominio.modelo.Parte;
import co.abogados.procesos.dominio.modelo.ParteRegistrada;
import co.abogados.procesos.dominio.modelo.Portal;
import co.abogados.procesos.dominio.modelo.ProvidenciaVista;
import co.abogados.procesos.dominio.modelo.Prueba;
import co.abogados.procesos.dominio.modelo.Rama;
import co.abogados.procesos.dominio.modelo.Reportes;
import co.abogados.procesos.dominio.modelo.RolParte;
import co.abogados.procesos.dominio.modelo.SituacionTermino;
import co.abogados.procesos.dominio.modelo.TerminoVista;
import co.abogados.procesos.dominio.modelo.TipoActuacion;
import co.abogados.procesos.dominio.modelo.TipoProceso;
import co.abogados.procesos.dominio.puertos.ReporteRepositorio;

/** Los recursos REST del servicio (salvo procesos y documentos): rutas, códigos HTTP, validación y cabeceras. */
@WebMvcTest({ ExpedienteRecurso.class, TerminosRecurso.class, AgendaRecurso.class, CatalogosRecurso.class,
		CalendarioRecurso.class, EquipoRecurso.class, PruebasRecurso.class, ProvidenciasRecurso.class,
		PortalRecurso.class, ReportesRecurso.class })
class RecursosTest {

	static final LocalDate HOY = LocalDate.of(2026, 10, 15);

	@TestConfiguration
	static class RelojFijo {
		@Bean
		@Primary
		Clock relojDePrueba() {
			return Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Bogota"));
		}
	}

	@Autowired
	MockMvc mvc;

	@MockBean ExpedienteServicio expediente;
	@MockBean TerminoServicio terminos;
	@MockBean AgendaServicio agenda;
	@MockBean CatalogoServicio catalogos;
	@MockBean CalendarioServicio calendario;
	@MockBean EquipoServicio equipo;
	@MockBean PruebaServicio pruebas;
	@MockBean ProvidenciaServicio providencias;
	@MockBean SugerenciaServicio sugerencias;
	@MockBean PortalServicio portal;
	@MockBean ReporteRepositorio reportes;

	static String json(String comillasSimples) {
		return comillasSimples.replace('\'', '"');
	}

	// ----------------------------------------------------------------- expediente

	static Parte contraparte(Integer id) {
		return new Parte(id, 31, RolParte.DEMANDADO, "NIT", "900123456", "Inversiones Delta SAS", false, null);
	}

	@Test
	void elExpedienteTraeLineaDeTiempoYOtrasInstancias() throws Exception {
		given(expediente.lineaDeTiempo(31)).willReturn(List.of(new EventoExpediente(LocalDate.of(2026, 9, 11),
				"PROVIDENCIA", "AUTO", 7, "Admite demanda", null, "VIGILANCIA")));
		given(expediente.otrasInstancias(31)).willReturn(List.of(new Instancia(40, "11001-31-03-032-2026-00501-01", 1,
				"Tribunal Superior", "ACTIVO")));

		mvc.perform(get("/procesos/31/expediente"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lineaDeTiempo[0].fecha").value("2026-09-11"))
				.andExpect(jsonPath("$.lineaDeTiempo[0].detalle").doesNotExist())
				.andExpect(jsonPath("$.otrasInstancias[0].instancia").value(1));
	}

	@Test
	void agregarUnaParteDevuelve201ConLosConflictos() throws Exception {
		given(expediente.agregarParte(eq(31), any())).willReturn(new ParteRegistrada(contraparte(5),
				List.of(new ConflictoInteres(6, "Inversiones Delta SAS", "900123456", "Tiene el mismo documento")), true));

		mvc.perform(post("/procesos/31/partes").contentType(MediaType.APPLICATION_JSON).content(json(
				"{'rol':'DEMANDADO','tipoDocumento':'NIT','numeroDocumento':'900123456','nombre':'Inversiones Delta SAS'}")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/procesos/31/partes/5"))
				.andExpect(jsonPath("$.conflictos[0].idCliente").value(6))
				.andExpect(jsonPath("$.conflictosVerificados").value(true));

		ArgumentCaptor<Parte> parte = ArgumentCaptor.forClass(Parte.class);
		verify(expediente).agregarParte(eq(31), parte.capture());
		assertThat(parte.getValue().idProceso()).isEqualTo(31);
		assertThat(parte.getValue().esCliente()).isFalse();
	}

	@Test
	void unaParteSinNombreNiRolEsUn400() throws Exception {
		mvc.perform(post("/procesos/31/partes").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.nombre").value("El nombre es obligatorio"))
				.andExpect(jsonPath("$.campos.rol").value("Indique el rol de la parte"));
		verifyNoInteractions(expediente);
	}

	@Test
	void unDocumentoMalFormadoEsUn422() throws Exception {
		mvc.perform(post("/procesos/31/partes").contentType(MediaType.APPLICATION_JSON).content(json(
				"{'rol':'DEMANDADO','tipoDocumento':'CC','numeroDocumento':'12.345','nombre':'Pedro'}")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value(containsString("sin puntos ni guiones")));
	}

	@Test
	void editarYQuitarPartes() throws Exception {
		given(expediente.editarParte(eq(31), eq(5), any())).willReturn(new ParteRegistrada(contraparte(5), List.of(), false));
		willThrow(new ReglaNegocioException("No se puede quitar al cliente del proceso")).given(expediente).eliminarParte(31, 1);
		given(expediente.partes(31)).willReturn(List.of(contraparte(5)));

		mvc.perform(put("/procesos/31/partes/5").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'rol':'DEMANDADO','nombre':'Inversiones Delta'}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.conflictosVerificados").value(false));
		mvc.perform(delete("/procesos/31/partes/5")).andExpect(status().isNoContent());
		mvc.perform(delete("/procesos/31/partes/1"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("No se puede quitar al cliente del proceso"));
		mvc.perform(get("/procesos/31/partes")).andExpect(jsonPath("$[0].nombre").value("Inversiones Delta SAS"));
	}

	@Test
	void registrarUnaActuacionTomaElUsuarioDeLaCabecera() throws Exception {
		Actuacion creada = new Actuacion(8, 31, LocalDate.of(2026, 10, 14), TipoActuacion.CONTESTACION,
				"Contestación de la demanda", null, null, null, 52345678L, null, null);
		given(expediente.registrarActuacion(eq(31), any(), eq(9), eq(52345678L))).willReturn(creada);

		mvc.perform(post("/procesos/31/actuaciones").header("X-Usuario-Id", "52345678")
				.contentType(MediaType.APPLICATION_JSON).content(json(
						"{'tipo':'CONTESTACION','fecha':'2026-10-14','descripcion':'Contestación de la demanda','idTerminoCumplido':9}")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/procesos/31/actuaciones/8"))
				.andExpect(jsonPath("$.registradoPor").value(52345678));

		ArgumentCaptor<Actuacion> datos = ArgumentCaptor.forClass(Actuacion.class);
		verify(expediente).registrarActuacion(eq(31), datos.capture(), eq(9), eq(52345678L));
		assertThat(datos.getValue().registradoPor()).isEqualTo(52345678L);
		assertThat(datos.getValue().tipo()).isEqualTo(TipoActuacion.CONTESTACION);
	}

	@Test
	void validacionYErroresDeLasActuaciones() throws Exception {
		mvc.perform(post("/procesos/31/actuaciones").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'tipo':'MEMORIAL','descripcion':'Memorial'}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.fecha").value("La fecha es obligatoria"));
		mvc.perform(post("/procesos/31/actuaciones").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'tipo':'MEMORIAL','fecha':'14/10/2026','descripcion':'Memorial'}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(containsString("AAAA-MM-DD")));

		willThrow(new RecursoNoEncontradoException("No existe la actuación 99 en este proceso")).given(expediente)
				.eliminarActuacion(31, 99);
		mvc.perform(delete("/procesos/31/actuaciones/99")).andExpect(status().isNotFound());
		mvc.perform(delete("/procesos/31/actuaciones/8")).andExpect(status().isNoContent());
		given(expediente.actuaciones(31)).willReturn(List.of());
		mvc.perform(get("/procesos/31/actuaciones")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
		mvc.perform(get("/procesos/abc/actuaciones"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Valor inválido para 'idProceso': abc"));
	}

	// ----------------------------------------------------------------- términos

	static TerminoVista termino(int id, String estado) {
		return new TerminoVista(id, 31, "11001310303220260050100", "11001-31-03-032-2026-00501-00", "Civil", "Cobro",
				null, "Contestar demanda", LocalDate.of(2026, 10, 1), 20, LocalDate.of(2026, 10, 29), estado, 52345678L, 10,
				"AL_DIA");
	}

	@Test
	void listarTerminosPasaLosFiltros() throws Exception {
		given(terminos.listar(new FiltroTerminos(SituacionTermino.VENCIDO, 52345678L, 31, LocalDate.of(2026, 10, 1),
				null))).willReturn(List.of(termino(9, "PENDIENTE")));

		mvc.perform(get("/terminos").param("situacion", "VENCIDO").param("idResponsable", "52345678")
				.param("idProceso", "31").param("desde", "2026-10-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].idTermino").value(9))
				.andExpect(jsonPath("$[0].idProvidencia").doesNotExist());
		mvc.perform(get("/terminos").param("situacion", "OLVIDADO"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(containsString("situacion")));
	}

	@Test
	void crearUnTerminoDevuelve201YValidaLosDias() throws Exception {
		given(terminos.crear(eq(31), any())).willReturn(termino(9, "PENDIENTE"));

		mvc.perform(post("/procesos/31/terminos").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'descripcion':'Contestar demanda','fechaInicio':'2026-10-01','diasHabiles':20}")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/terminos/9"));
		verify(terminos).crear(31, new TerminoServicio.NuevoTermino("Contestar demanda", LocalDate.of(2026, 10, 1), 20,
				null, null));

		mvc.perform(post("/procesos/31/terminos").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'descripcion':'Contestar','fechaInicio':'2026-10-01','diasHabiles':0}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.diasHabiles").value("Mínimo 1 día hábil"));
		mvc.perform(post("/procesos/31/terminos").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'descripcion':'Contestar','fechaInicio':'2026-10-01','diasHabiles':400}")))
				.andExpect(jsonPath("$.campos.diasHabiles").value("Máximo 365 días hábiles"));
	}

	@Test
	void obtenerCumplirEliminarYRevisarTerminos() throws Exception {
		given(terminos.obtener(9)).willReturn(termino(9, "PENDIENTE"));
		given(terminos.obtener(99)).willThrow(new RecursoNoEncontradoException("No existe el término 99"));
		given(terminos.cumplir(9)).willReturn(termino(9, "CUMPLIDO"));
		given(terminos.cumplir(10)).willThrow(new ConflictoException("El término ya estaba cumplido"));
		given(terminos.revisarVencimientos()).willReturn(new TerminoServicio.Revision(HOY, 2, 1));

		mvc.perform(get("/terminos/9")).andExpect(jsonPath("$.descripcion").value("Contestar demanda"));
		mvc.perform(get("/terminos/99")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("No existe el término 99"));
		mvc.perform(post("/terminos/9/cumplir")).andExpect(jsonPath("$.estado").value("CUMPLIDO"));
		mvc.perform(post("/terminos/10/cumplir")).andExpect(status().isConflict());
		mvc.perform(delete("/terminos/9")).andExpect(status().isNoContent());
		verify(terminos).eliminar(9);
		mvc.perform(post("/terminos/revision"))
				.andExpect(jsonPath("$.porVencer").value(2))
				.andExpect(jsonPath("$.fecha").value("2026-10-15"));
	}

	// ----------------------------------------------------------------- agenda, catálogos y calendario

	@Test
	void agendaDeAudiencias() throws Exception {
		given(agenda.audiencias(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 52345678L)).willReturn(List.of(
				new AudienciaVista(8, 31, "R", "R-legible", "Cobro", LocalDate.of(2026, 10, 20), LocalTime.of(9, 0),
						"Audiencia inicial", "Sala 3", 52345678L)));
		given(agenda.avisarAudiencias()).willReturn(2);

		mvc.perform(get("/audiencias").param("desde", "2026-10-01").param("hasta", "2026-10-31")
				.param("idAsistente", "52345678"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].hora").value(org.hamcrest.Matchers.startsWith("09:00")));
		mvc.perform(get("/audiencias").param("desde", "2026-10-01")).andExpect(status().isBadRequest());
		mvc.perform(get("/audiencias").param("desde", "ayer").param("hasta", "2026-10-31"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Valor inválido para 'desde': ayer"));
		mvc.perform(post("/audiencias/avisos")).andExpect(jsonPath("$.avisadas").value(2));
	}

	@Test
	void catalogos() throws Exception {
		given(catalogos.ramas()).willReturn(List.of(new Rama("CIVIL", "Civil y comercial", "CGP")));
		given(catalogos.tipos("CIVIL")).willReturn(List.of(new TipoProceso((short) 1, "CIVIL", "Verbal", (short) 20)));
		given(catalogos.despachos(null)).willReturn(List.of(new Despacho((short) 2, "Juzgado 032 Civil", "Bogotá",
				"11001", "31", "03", "032", "CIVIL")));

		mvc.perform(get("/catalogos/ramas")).andExpect(jsonPath("$[0].norma").value("CGP"));
		mvc.perform(get("/catalogos/tipos").param("rama", "CIVIL"))
				.andExpect(jsonPath("$[0].terminoContestacionDias").value(20));
		mvc.perform(get("/catalogos/despachos")).andExpect(jsonPath("$[0].codDespacho").value("032"));
	}

	@Test
	void calendarioYVencimiento() throws Exception {
		given(calendario.diasInhabiles(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).willReturn(List.of(
				new DiaInhabil(LocalDate.of(2026, 11, 2), "Todos los Santos", "FESTIVO")));
		given(calendario.vencimiento(LocalDate.of(2026, 10, 30), 1)).willReturn(LocalDate.of(2026, 11, 3));
		given(calendario.diasInhabiles(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1)))
				.willThrow(new ReglaNegocioException("La fecha final es anterior a la inicial"));

		mvc.perform(get("/calendario").param("desde", "2026-11-01").param("hasta", "2026-11-30"))
				.andExpect(jsonPath("$[0].descripcion").value("Todos los Santos"));
		mvc.perform(get("/calendario/vencimiento").param("desde", "2026-10-30").param("dias", "1"))
				.andExpect(jsonPath("$.fechaInicio").value("2026-10-30"))
				.andExpect(jsonPath("$.diasHabiles").value(1))
				.andExpect(jsonPath("$.fechaVencimiento").value("2026-11-03"));
		mvc.perform(get("/calendario").param("desde", "2026-11-30").param("hasta", "2026-11-01"))
				.andExpect(status().isUnprocessableEntity());
	}

	// ----------------------------------------------------------------- equipo

	@Test
	void equipoDelProceso() throws Exception {
		given(equipo.equipo(31)).willReturn(List.of(new Asignacion(31, 52345678L, Asignacion.Rol.RESPONSABLE,
				LocalDate.of(2026, 9, 1), "Catalina Gómez")));
		given(equipo.agregar(31, 79123456L, Asignacion.Rol.APOYO, 52345678L)).willReturn(new Asignacion(31, 79123456L,
				Asignacion.Rol.APOYO, HOY, "Pedro Rincón"));

		mvc.perform(get("/procesos/31/asignaciones")).andExpect(jsonPath("$[0].rol").value("RESPONSABLE"));
		mvc.perform(post("/procesos/31/asignaciones").header("X-Usuario-Id", "52345678")
				.contentType(MediaType.APPLICATION_JSON).content(json("{'idPersona':79123456,'rol':'APOYO'}")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/procesos/31/asignaciones/79123456"))
				.andExpect(jsonPath("$.nombre").value("Pedro Rincón"));
		mvc.perform(post("/procesos/31/asignaciones").header("X-Usuario-Id", "52345678")
				.contentType(MediaType.APPLICATION_JSON).content(json("{'idPersona':-4}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.rol").value("Elija el rol"))
				.andExpect(jsonPath("$.campos.idPersona").exists());
		mvc.perform(delete("/procesos/31/asignaciones/79123456").header("X-Usuario-Id", "52345678"))
				.andExpect(status().isNoContent());
		verify(equipo).quitar(31, 79123456L, 52345678L);
		mvc.perform(delete("/procesos/31/asignaciones/79123456")).andExpect(status().isBadRequest());
	}

	// ----------------------------------------------------------------- pruebas

	@Test
	void pruebasDelProceso() throws Exception {
		Prueba creada = new Prueba(4, 31, Prueba.Tipo.PERICIAL, "Dictamen", 3, Prueba.Estado.DECRETADA,
				LocalDate.of(2026, 10, 30), 12);
		given(pruebas.crear(eq(31), any())).willReturn(creada);
		given(pruebas.actualizar(eq(31), eq(4), any())).willReturn(creada);
		given(pruebas.pruebas(31)).willReturn(List.of(creada));

		mvc.perform(post("/procesos/31/pruebas").contentType(MediaType.APPLICATION_JSON).content(json(
				"{'tipo':'PERICIAL','descripcion':'Dictamen','idPerito':3,'estado':'DECRETADA','fechaEntrega':'2026-10-30'}")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/procesos/31/pruebas/4"))
				.andExpect(jsonPath("$.idTermino").value(12));
		mvc.perform(put("/procesos/31/pruebas/4").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'tipo':'PERICIAL','descripcion':'Dictamen','idPerito':3}")))
				.andExpect(status().isOk());
		mvc.perform(get("/procesos/31/pruebas")).andExpect(jsonPath("$[0].tipo").value("PERICIAL"));
		mvc.perform(delete("/procesos/31/pruebas/4")).andExpect(status().isNoContent());
		verify(pruebas).eliminar(31, 4);
	}

	@Test
	void unaPruebaInvalidaNoLlegaAlServicio() throws Exception {
		mvc.perform(post("/procesos/31/pruebas").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'descripcion':'Dictamen'}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.tipo").value("Indique el tipo de prueba"));
		mvc.perform(post("/procesos/31/pruebas").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'tipo':'DOCUMENTAL','descripcion':'Facturas','idPerito':3}")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value(containsString("pericial")));
		verifyNoInteractions(pruebas);
	}

	// ----------------------------------------------------------------- providencias

	@Test
	void providenciasDelProceso() throws Exception {
		ProvidenciaVista sentencia = new ProvidenciaVista(7, 31, "SENTENCIA", "Sentencia de primera instancia",
				LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), null, "FAVORABLE", "MANUAL");
		given(providencias.deProceso(31)).willReturn(List.of(sentencia));
		given(providencias.registrarManual(eq(31), any(), eq(52345678L))).willReturn(sentencia);

		mvc.perform(get("/procesos/31/providencias")).andExpect(jsonPath("$[0].sentido").value("FAVORABLE"));
		mvc.perform(post("/procesos/31/providencias").header("X-Usuario-Id", "52345678")
				.contentType(MediaType.APPLICATION_JSON).content(json("{'tipo':'SENTENCIA',"
						+ "'clase':'Sentencia de primera instancia','fechaProvidencia':'2026-10-01',"
						+ "'fechaEstado':'2026-10-02','sentido':'FAVORABLE'}")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/procesos/31/providencias/7"));
		verify(providencias).registrarManual(31, new NuevaProvidencia("SENTENCIA", "Sentencia de primera instancia",
				LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), null, "FAVORABLE"), 52345678L);
	}

	@Test
	void unaProvidenciaManualInvalida() throws Exception {
		mvc.perform(post("/procesos/31/providencias").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'clase':'Admite'}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.tipo").value("Indique si es AUTO o SENTENCIA"))
				.andExpect(jsonPath("$.campos.fechaEstado").value("La fecha del estado es obligatoria"));
		mvc.perform(post("/procesos/31/providencias").contentType(MediaType.APPLICATION_JSON).content(json(
				"{'tipo':'AUTO','clase':'Admite','fechaProvidencia':'2026-10-02','fechaEstado':'2026-10-01'}")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value(containsString("anterior a la providencia")));
		verifyNoInteractions(providencias);
	}

	// ----------------------------------------------------------------- portal y resúmenes

	static Portal.ProcesoCliente procesoCliente() {
		return new Portal.ProcesoCliente(31, "11001-31-03-032-2026-00501-00", "Verbal", "Civil", "Juzgado 032",
				"Cobro", "DEMANDANTE", "DEMANDA", "ACTIVO", LocalDate.of(2026, 9, 1), "Catalina Gómez", null, null);
	}

	@Test
	void elPortalEsSoloParaClientes() throws Exception {
		mvc.perform(get("/portal/procesos")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("El portal es solo para clientes"));
		mvc.perform(get("/portal/procesos").header("X-Cliente-Id", "6").header("X-Usuario-Roles", "ABOGADO,SOCIO"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/portal/procesos/31").header("X-Usuario-Roles", "CLIENTE")).andExpect(status().isForbidden());
		verifyNoInteractions(portal);
	}

	@Test
	void elClienteVeSusProcesosYElDetalle() throws Exception {
		given(portal.procesosDe(6)).willReturn(List.of(procesoCliente()));
		given(portal.detalle(6, 31)).willReturn(new Portal.Detalle(procesoCliente(), List.of(), List.of(), null, List.of()));
		given(portal.detalle(6, 32)).willThrow(new RecursoNoEncontradoException("No existe el proceso 32"));

		mvc.perform(get("/portal/procesos").header("X-Cliente-Id", "6").header("X-Usuario-Roles", "CLIENTE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].abogado").value("Catalina Gómez"))
				.andExpect(jsonPath("$[0].proximaAudiencia").doesNotExist());
		mvc.perform(get("/portal/procesos/31").header("X-Cliente-Id", "6").header("X-Usuario-Roles", "CLIENTE"))
				.andExpect(jsonPath("$.proceso.idProceso").value(31));
		mvc.perform(get("/portal/procesos/32").header("X-Cliente-Id", "6").header("X-Usuario-Roles", "CLIENTE"))
				.andExpect(status().isNotFound());
	}

	@Test
	void resumenParaElCliente() throws Exception {
		Portal.ResumenPublicado r = new Portal.ResumenPublicado(2, 31, "El juzgado admitió la demanda.", 52345678L,
				Instant.parse("2026-10-15T15:00:00Z"));
		given(portal.ultimoResumen(31)).willReturn(Optional.empty());
		given(portal.ultimoResumen(32)).willReturn(Optional.of(r));
		given(portal.publicarResumen(31, "El juzgado admitió la demanda.", 52345678L)).willReturn(r);

		mvc.perform(get("/procesos/31/resumen-cliente")).andExpect(status().isNoContent());
		mvc.perform(get("/procesos/32/resumen-cliente")).andExpect(jsonPath("$.idResumen").value(2));
		mvc.perform(post("/procesos/31/resumen-cliente").header("X-Usuario-Id", "52345678")
				.contentType(MediaType.APPLICATION_JSON).content(json("{'texto':'El juzgado admitió la demanda.'}")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/procesos/31/resumen-cliente"));
		mvc.perform(post("/procesos/31/resumen-cliente").header("X-Usuario-Id", "52345678")
				.contentType(MediaType.APPLICATION_JSON).content(json("{'texto':' '}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.texto").value("Escriba el resumen"));
	}

	// ----------------------------------------------------------------- reportes

	@Test
	void sinFechasLosReportesCubrenElUltimoAnio() throws Exception {
		given(reportes.carga(HOY)).willReturn(List.of(new Reportes.CargaAbogado(52345678L, 4, 1, 6, 2, 3)));
		given(reportes.cumplimiento(HOY.minusYears(1), HOY, HOY)).willReturn(List.of(
				new Reportes.CumplimientoTerminos(52345678L, 10, 6, 2, 0, 1, 1)));
		given(reportes.resultados(HOY.minusYears(1), HOY)).willReturn(List.of(
				new Reportes.ResultadoRama("CIVIL", "Civil", 3, 1, 2, 0)));

		mvc.perform(get("/reportes/carga")).andExpect(jsonPath("$[0].procesosActivos").value(4));
		mvc.perform(get("/reportes/terminos")).andExpect(jsonPath("$[0].aTiempo").value(6));
		mvc.perform(get("/reportes/resultados")).andExpect(jsonPath("$[0].favorables").value(3));
	}

	@Test
	void losRangosDeLosReportesSeValidan() throws Exception {
		mvc.perform(get("/reportes/resultados").param("desde", "2026-01-01").param("hasta", "2026-06-30"))
				.andExpect(status().isOk());
		verify(reportes).resultados(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
		mvc.perform(get("/reportes/terminos").param("hasta", "2026-03-31")).andExpect(status().isOk());
		verify(reportes).cumplimiento(LocalDate.of(2025, 3, 31), LocalDate.of(2026, 3, 31), HOY);

		mvc.perform(get("/reportes/terminos").param("desde", "2026-06-30").param("hasta", "2026-01-01"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("La fecha final es anterior a la inicial"));
		mvc.perform(get("/reportes/resultados").param("desde", "2015-01-01"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("Consulte máximo cinco años"));
		verify(reportes, org.mockito.Mockito.never()).resultados(eq(LocalDate.of(2015, 1, 1)), any());
	}

}
