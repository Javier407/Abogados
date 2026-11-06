package co.abogados.procesos.infraestructura.persistencia;

import static co.abogados.procesos.infraestructura.persistencia.JdbcFalso.fila;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.modelo.Actuacion;
import co.abogados.procesos.dominio.modelo.ActuacionDetectada;
import co.abogados.procesos.dominio.modelo.Asignacion;
import co.abogados.procesos.dominio.modelo.Documento;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.NuevaProvidencia;
import co.abogados.procesos.dominio.modelo.Parte;
import co.abogados.procesos.dominio.modelo.Prueba;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.modelo.RolParte;
import co.abogados.procesos.dominio.modelo.TipoActuacion;

/** Lectura de columnas y parámetros de los repositorios JDBC más simples, con el doble JdbcFalso. */
class RepositoriosJdbcTest {

	JdbcFalso jdbc = new JdbcFalso();

	@Test
	void pruebas() {
		jdbc.responder("FROM procesos.pruebas", fila("id_prueba", 4, "id_proceso", 31, "tipo", "PERICIAL",
				"descripcion", "Dictamen", "id_perito", 3, "estado", "DECRETADA", "fecha_entrega",
				LocalDate.of(2026, 10, 30), "id_termino", null)).responderValor("RETURNING id_prueba", 5);
		PruebaRepositorioJdbc repo = new PruebaRepositorioJdbc(jdbc.cliente);

		assertThat(repo.deProceso(31)).singleElement().satisfies(p -> {
			assertThat(p.tipo()).isEqualTo(Prueba.Tipo.PERICIAL);
			assertThat(p.estado()).isEqualTo(Prueba.Estado.DECRETADA);
			assertThat(p.idTermino()).isNull();
			assertThat(p.idPerito()).isEqualTo(3);
		});
		assertThat(repo.buscar(4)).isPresent();
		Prueba nueva = new Prueba(null, 31, Prueba.Tipo.TESTIMONIAL, "Testigo", null, null, null, null);
		assertThat(repo.crear(nueva)).isEqualTo(5);
		assertThat(jdbc.ultima().params()).containsEntry("tipo", "TESTIMONIAL").containsEntry("estado", "SOLICITADA");
		repo.actualizar(new Prueba(5, 31, Prueba.Tipo.TESTIMONIAL, "Testigo", null, Prueba.Estado.PRACTICADA, null, 8));
		assertThat(jdbc.ultima().params()).containsEntry("id", 5).containsEntry("termino", 8);
		repo.eliminar(5);
		assertThat(jdbc.ultima().sql()).startsWith("DELETE FROM procesos.pruebas");
	}

	@Test
	void providencias() {
		jdbc.responder("ORDER BY fecha_estado DESC", fila("id_providencia", 7, "id_proceso", 31, "tipo", "AUTO",
				"clase", "Admite demanda", "fecha_providencia", LocalDate.of(2026, 9, 10), "fecha_estado",
				LocalDate.of(2026, 9, 11), "decision", null, "sentido", null, "fuente", "VIGILANCIA"))
				.responderValor("referencia_externa = :r", 1).responderValor("RETURNING id_providencia", 8);
		ProvidenciaRepositorioJdbc repo = new ProvidenciaRepositorioJdbc(jdbc.cliente);

		assertThat(repo.deProceso(31)).singleElement().satisfies(p -> {
			assertThat(p.clase()).isEqualTo("Admite demanda");
			assertThat(p.fechaEstado()).isEqualTo(LocalDate.of(2026, 9, 11));
		});
		assertThat(repo.existeReferencia("rama-123")).isTrue();
		assertThat(repo.registrarManual(31, new NuevaProvidencia("SENTENCIA", "Sentencia de primera instancia",
				LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), null, "FAVORABLE"), 52345678L)).isEqualTo(8);
		assertThat(jdbc.ultima().params()).containsEntry("sentido", "FAVORABLE").containsEntry("quien", 52345678L);
	}

	@Test
	void unaProvidenciaDetectadaSeRecortaYSoloLaSentenciaConservaSuTipo() {
		jdbc.responderValor("RETURNING id_providencia", 8);
		ProvidenciaRepositorioJdbc repo = new ProvidenciaRepositorioJdbc(jdbc.cliente);

		repo.registrarDetectada(31, new ActuacionDetectada("11001310303220260050100", 31, "rama-1",
				LocalDate.of(2026, 9, 10), "AUTO_INTERLOCUTORIO", "x".repeat(250), "  ", "RAMA"));
		assertThat(jdbc.ultima().params()).containsEntry("tipo", "AUTO").containsEntry("decision", null)
				.containsEntry("clase", "x".repeat(200));

		repo.registrarDetectada(31, new ActuacionDetectada("11001310303220260050100", 31, "rama-2",
				LocalDate.of(2026, 9, 10), "SENTENCIA", "Fallo", "y".repeat(1200), "RAMA"));
		assertThat(jdbc.ultima().params()).containsEntry("tipo", "SENTENCIA").containsEntry("clase", "Fallo")
				.containsEntry("decision", "y".repeat(1000));
	}

	@Test
	void partes() {
		jdbc.responder("FROM procesos.partes", fila("id_parte", 2, "id_proceso", 31, "rol", "DEMANDADO",
				"tipo_documento", "NIT", "numero_documento", "900123456", "nombre", "Inversiones Delta SAS",
				"es_cliente", false, "apoderado", null)).responderValor("RETURNING id_parte", 3);
		ParteRepositorioJdbc repo = new ParteRepositorioJdbc(jdbc.cliente);

		assertThat(repo.deProceso(31)).singleElement().satisfies(p -> {
			assertThat(p.rol()).isEqualTo(RolParte.DEMANDADO);
			assertThat(p.esCliente()).isFalse();
			assertThat(p.numeroDocumento()).isEqualTo("900123456");
		});
		assertThat(repo.buscar(2)).isPresent();
		Parte p = new Parte(null, 31, RolParte.TERCERO, null, null, "Aseguradora", false, "Ana Ruiz");
		assertThat(repo.crear(p)).isEqualTo(3);
		assertThat(jdbc.ultima().params()).containsEntry("rol", "TERCERO").containsEntry("cliente", false);
		repo.actualizar(new Parte(3, 31, RolParte.LLAMADO_GARANTIA, null, null, "Aseguradora", false, null));
		assertThat(jdbc.ultima().params()).containsEntry("id", 3).containsEntry("rol", "LLAMADO_GARANTIA");
		repo.eliminar(3);
		assertThat(jdbc.ultima().params()).containsEntry("id", 3);
	}

	@Test
	void catalogos() {
		jdbc.responder("procesos.ramas", fila("codigo", "CIVIL", "nombre", "Civil y comercial", "norma", "CGP"))
				.responder("procesos.tipos_proceso", fila("id_tipo", 1, "codigo_rama", "CIVIL", "nombre", "Verbal",
						"termino_contestacion_dias", null))
				.responder("procesos.despachos", fila("id_despacho", 2, "nombre", "Juzgado 032 Civil", "ciudad",
						"Bogotá", "cod_dane", "11001", "cod_entidad", "31", "cod_especialidad", "03", "cod_despacho",
						"032", "codigo_rama", "CIVIL"));
		CatalogoRepositorioJdbc repo = new CatalogoRepositorioJdbc(jdbc.cliente);

		assertThat(repo.ramas()).singleElement().satisfies(r -> assertThat(r.norma()).isEqualTo("CGP"));
		assertThat(repo.tipos(null)).singleElement()
				.satisfies(t -> assertThat(t.terminoContestacionDias()).isNull());
		assertThat(jdbc.ultima().params()).isEmpty();
		assertThat(repo.tipos("civil")).hasSize(1);
		assertThat(jdbc.ultima().params()).containsEntry("rama", "CIVIL");
		assertThat(repo.despachos(" ")).singleElement().satisfies(d -> assertThat(d.codDespacho()).isEqualTo("032"));
		assertThat(repo.despachos("laboral")).hasSize(1);
		assertThat(jdbc.ultima().params()).containsEntry("rama", "LABORAL");
		assertThat(repo.tipo((short) 1)).isPresent();
		assertThat(repo.despacho((short) 2)).isPresent();
		assertThat(repo.despachoDeRadicado(Radicado.de("11001310303220260050100"))).isPresent();
		assertThat(jdbc.ultima().params()).containsEntry("dane", "11001").containsEntry("entidad", "31")
				.containsEntry("especialidad", "03").containsEntry("despacho", "032");
	}

	@Test
	void documentos() {
		UUID archivo = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
		Instant subido = Instant.parse("2026-10-15T15:00:00Z");
		jdbc.responder("procesos.documentos", fila("id_documento", 6, "id_proceso", 31, "archivo", archivo,
				"nombre", "poder.pdf", "tipo_mime", "application/pdf", "tamano", 2048L, "sha256", "abc",
				"categoria", "PODER", "descripcion", null, "visible_cliente", true, "subido_por", 52345678L,
				"subido", subido));
		DocumentoRepositorioJdbc repo = new DocumentoRepositorioJdbc(jdbc.cliente);

		Documento guardado = repo.guardar(new Documento(null, 31, archivo, "poder.pdf", "application/pdf", 2048L, "abc",
				Documento.Categoria.PODER, null, true, 52345678L, subido));
		assertThat(guardado.idDocumento()).isEqualTo(6);
		assertThat(guardado.subido()).isEqualTo(subido);
		assertThat(jdbc.ultima().params()).containsEntry("categoria", "PODER").containsEntry("visible", true);

		assertThat(repo.listar(31, true)).hasSize(1);
		assertThat(jdbc.ultima().sql()).contains("AND visible_cliente");
		repo.listar(31, false);
		assertThat(jdbc.ultima().sql()).doesNotContain("AND visible_cliente");
		assertThat(repo.buscar(31, 6)).isPresent();
		repo.actualizar(6, Documento.Categoria.OTRO, "Copia", false);
		assertThat(jdbc.ultima().params()).containsEntry("c", "OTRO").containsEntry("v", false);
		repo.eliminar(6);
		assertThat(jdbc.ultima().params()).containsEntry("id", 6);
	}

	@Test
	void actuaciones() {
		jdbc.responder("FROM procesos.actuaciones", fila("id_actuacion", 8, "id_proceso", 31, "fecha",
				LocalDate.of(2026, 10, 20), "tipo", "AUDIENCIA", "descripcion", "Audiencia inicial", "id_persona", null,
				"id_providencia", null, "documento_url", null, "registrado_por", 52345678L, "hora", LocalTime.of(9, 0),
				"lugar", "Sala 3")).responderValor("RETURNING id_actuacion", 9);
		ActuacionRepositorioJdbc repo = new ActuacionRepositorioJdbc(jdbc.cliente);

		assertThat(repo.deProceso(31)).singleElement().satisfies(a -> {
			assertThat(a.tipo()).isEqualTo(TipoActuacion.AUDIENCIA);
			assertThat(a.hora()).isEqualTo(LocalTime.of(9, 0));
			assertThat(a.idPersona()).isNull();
		});
		assertThat(repo.buscar(8)).isPresent();
		assertThat(repo.crear(new Actuacion(null, 31, LocalDate.of(2026, 10, 1), TipoActuacion.MEMORIAL, "Memorial",
				null, null, "https://drive.example/x", 52345678L, null, null))).isEqualTo(9);
		assertThat(jdbc.ultima().params()).containsEntry("tipo", "MEMORIAL").containsEntry("documento",
				"https://drive.example/x");
		repo.eliminar(9);
		assertThat(jdbc.ultima().sql()).startsWith("DELETE FROM procesos.actuaciones");
	}

	@Test
	void reportes() {
		jdbc.responder("WITH personas", fila("id_persona", 52345678L, "activos", 4, "suspendidos", 1,
				"pendientes", 6, "vencidos", 2, "audiencias", 3))
				.responder("GROUP BY id_responsable", fila("id_responsable", 52345678L, "total", 10, "a_tiempo", 6,
						"tarde", 2, "sin_fecha", 0, "vencidos", 1, "pendientes", 1))
				.responder("FROM procesos.ramas r", fila("codigo", "CIVIL", "nombre", "Civil", "favorables", 3,
						"parciales", 1, "desfavorables", 2, "sin_sentido", 0));
		ReporteRepositorioJdbc repo = new ReporteRepositorioJdbc(jdbc.cliente);
		LocalDate hoy = LocalDate.of(2026, 10, 15);

		assertThat(repo.carga(hoy)).singleElement().satisfies(c -> {
			assertThat(c.procesosActivos()).isEqualTo(4);
			assertThat(c.audienciasProximas()).isEqualTo(3);
		});
		assertThat(repo.cumplimiento(hoy.minusYears(1), hoy, hoy)).singleElement()
				.satisfies(c -> assertThat(c.aTiempo()).isEqualTo(6));
		assertThat(jdbc.ultima().params()).containsEntry("hoy", hoy);
		assertThat(repo.resultados(hoy.minusYears(1), hoy)).singleElement()
				.satisfies(r -> assertThat(r.total()).isEqualTo(6));
	}

	@Test
	void expediente() {
		jdbc.responder("UNION ALL", fila("fecha", LocalDate.of(2026, 9, 11), "categoria", "PROVIDENCIA", "tipo", "AUTO",
				"id", 7, "titulo", "Admite demanda", "detalle", null, "origen", "VIGILANCIA"))
				.responder("v.id_proceso <> :id", fila("id_proceso", 40, "radicado_legible",
						"11001-31-03-032-2026-00501-01", "instancia", 1, "despacho_actual", "Tribunal", "estado", "ACTIVO"));
		ExpedienteRepositorioJdbc repo = new ExpedienteRepositorioJdbc(jdbc.cliente);

		assertThat(repo.lineaDeTiempo(31)).singleElement().satisfies(e -> {
			assertThat(e.fecha()).isEqualTo(LocalDate.of(2026, 9, 11));
			assertThat(e.titulo()).isEqualTo("Admite demanda");
		});
		assertThat(repo.otrasInstancias(31)).singleElement().satisfies(i -> assertThat(i.instancia()).isEqualTo(1));
		repo.avanzarEtapa(31, EtapaProceso.SENTENCIA);
		assertThat(jdbc.ultima().sql()).contains("etapa <> 'CERRADO'");
		assertThat(jdbc.ultima().params()).containsEntry("etapa", "SENTENCIA");
	}

	@Test
	void asignaciones() {
		jdbc.responder("FROM procesos.asignaciones", fila("id_proceso", 31, "id_persona", 52345678L, "rol",
				"RESPONSABLE", "desde", LocalDate.of(2026, 9, 1)));
		AsignacionRepositorioJdbc repo = new AsignacionRepositorioJdbc(jdbc.cliente);

		assertThat(repo.listar(31)).singleElement().satisfies(a -> {
			assertThat(a.rol()).isEqualTo(Asignacion.Rol.RESPONSABLE);
			assertThat(a.desde()).isEqualTo(LocalDate.of(2026, 9, 1));
		});
		assertThat(repo.buscar(31, 52345678L)).isPresent();
		repo.agregar(31, 79123456L, Asignacion.Rol.APOYO);
		assertThat(jdbc.ultima().params()).containsEntry("rol", "APOYO").containsEntry("q", 79123456L);
		repo.quitar(31, 79123456L);
		assertThat(jdbc.ultima().sql()).contains("rol <> 'RESPONSABLE'");
	}

	@Test
	void agendaResumenesYCalendario() {
		Instant publicado = Instant.parse("2026-10-15T15:00:00Z");
		jdbc.responder("a.tipo = 'AUDIENCIA'", fila("id_actuacion", 8, "id_proceso", 31, "radicado", "1100131",
				"radicado_legible", "11001-31", "asunto", "Cobro", "fecha", LocalDate.of(2026, 10, 20), "hora", null,
				"descripcion", "Audiencia inicial", "lugar", "Sala 3", "id_asistente", 52345678L))
				.responder("resumenes_cliente", fila("id_resumen", 2, "id_proceso", 31, "texto", "Vamos bien",
						"publicado_por", 52345678L, "publicado_en", publicado))
				.responder("procesos.festivos", fila("fecha", LocalDate.of(2026, 11, 2), "descripcion", "Todos los Santos",
						"tipo", "FESTIVO"));

		var audiencias = new AgendaRepositorioJdbc(jdbc.cliente).audiencias(LocalDate.of(2026, 10, 1),
				LocalDate.of(2026, 10, 31), null);
		assertThat(audiencias).singleElement().satisfies(a -> {
			assertThat(a.hora()).isNull();
			assertThat(a.idAsistente()).isEqualTo(52345678L);
		});
		assertThat(jdbc.ultima().params()).containsEntry("asistente", null);

		ResumenRepositorioJdbc resumenes = new ResumenRepositorioJdbc(jdbc.cliente);
		assertThat(resumenes.guardar(31, "Vamos bien", 52345678L).publicadoEn()).isEqualTo(publicado);
		assertThat(resumenes.ultimo(31)).isPresent();

		assertThat(new CalendarioRepositorioJdbc(jdbc.cliente).diasInhabiles()).singleElement()
				.satisfies(d -> assertThat(d.tipo()).isEqualTo("FESTIVO"));
	}

}
