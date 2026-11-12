package co.abogados.procesos.infraestructura.persistencia;

import static co.abogados.procesos.infraestructura.persistencia.JdbcFalso.fila;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.modelo.Cierre;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.Relevo;

/**
 * El SQL del relevo y del cierre. Que el disparador anota el historial lo prueba db/pruebas/019-relevo-y-cierre.sql
 * sobre PostgreSQL de verdad.
 */
class RelevoYCierreJdbcTest {

	static final long CATALINA = 52345678L;
	static final long PEDRO = 79123456L;

	JdbcFalso jdbc = new JdbcFalso();

	List<String> sentencias() {
		return jdbc.ejecutadas.stream().map(s -> s.sql().strip().replaceAll("\\s+", " ")).toList();
	}

	@Test
	void elRelevoDiceElMotivoALaBaseSacaAlAnteriorPoneAlNuevoYTrasladaLoPendiente() {
		RelevoRepositorioJdbc repo = new RelevoRepositorioJdbc(jdbc.cliente);

		Relevo.Resultado r = repo.relevar(34, CATALINA,
				new Relevo.Solicitud(PEDRO, Relevo.Motivo.RENUNCIA, "Renunció al poder", false, true), 19000111L);

		List<String> sql = sentencias();
		assertThat(sql).hasSize(5);
		assertThat(sql.get(0)).startsWith("SELECT set_config('procesos.motivo_relevo', :motivo, true)");
		assertThat(jdbc.ejecutadas.get(0).params()).containsEntry("motivo", "RENUNCIA")
				.containsEntry("nota", "Renunció al poder").containsEntry("autor", "19000111");
		assertThat(sql.get(1)).isEqualTo("DELETE FROM procesos.asignaciones WHERE id_proceso = :p AND id_persona = :anterior");
		assertThat(sql.get(2)).contains("VALUES (:p, :nuevo, 'RESPONSABLE', CURRENT_DATE)")
				.contains("ON CONFLICT (id_proceso, id_persona) DO UPDATE SET rol = 'RESPONSABLE'");
		assertThat(sql.get(3)).startsWith("UPDATE procesos.terminos SET id_responsable = :nuevo")
				.contains("estado = 'PENDIENTE' AND id_responsable = :anterior");
		assertThat(sql.get(4)).startsWith("UPDATE procesos.actuaciones SET id_persona = :nuevo")
				.contains("tipo = 'AUDIENCIA' AND fecha >= CURRENT_DATE");
		assertThat(jdbc.ultima().params()).containsEntry("p", 34).containsEntry("nuevo", PEDRO).containsEntry("anterior", CATALINA);
		assertThat(r).isEqualTo(new Relevo.Resultado(CATALINA, PEDRO, 1, 1));
	}

	@Test
	void elAnteriorPuedeQuedarDeApoyoYLoPendientePuedeNoTrasladarse() {
		RelevoRepositorioJdbc repo = new RelevoRepositorioJdbc(jdbc.cliente);

		Relevo.Resultado r = repo.relevar(34, CATALINA,
				new Relevo.Solicitud(PEDRO, Relevo.Motivo.REORGANIZACION, null, true, false), 19000111L);

		List<String> sql = sentencias();
		assertThat(sql).hasSize(3);
		assertThat(jdbc.ejecutadas.get(0).params()).containsEntry("nota", "");      // sin nota: vacío, no null
		assertThat(sql.get(1)).isEqualTo("UPDATE procesos.asignaciones SET rol = 'APOYO' WHERE id_proceso = :p AND id_persona = :anterior");
		assertThat(r.terminosTrasladados()).isZero();
		assertThat(r.audienciasTrasladadas()).isZero();
	}

	@Test
	void sinResponsableAnteriorSoloEntraElNuevo() {
		RelevoRepositorioJdbc repo = new RelevoRepositorioJdbc(jdbc.cliente);

		Relevo.Resultado r = repo.relevar(34, null,
				new Relevo.Solicitud(PEDRO, Relevo.Motivo.SUCESION, null, false, true), 19000111L);

		assertThat(sentencias()).hasSize(2);
		assertThat(r.idAnterior()).isNull();
		assertThat(r.terminosTrasladados()).isZero();
	}

	@Test
	void elHistorialTraePrimeroLosVigentes() {
		jdbc.responder("FROM procesos.asignaciones_historial", fila("id_cambio", 2L, "id_persona", PEDRO, "rol",
				"RESPONSABLE", "desde", LocalDate.of(2026, 11, 11), "hasta", null, "motivo", null, "nota", null,
				"registrado_por", 19000111L, "cerrado_por", null),
				fila("id_cambio", 1L, "id_persona", CATALINA, "rol", "RESPONSABLE", "desde", LocalDate.of(2026, 3, 1),
						"hasta", LocalDate.of(2026, 11, 11), "motivo", "RENUNCIA", "nota", "Renunció al poder",
						"registrado_por", null, "cerrado_por", 19000111L));
		RelevoRepositorioJdbc repo = new RelevoRepositorioJdbc(jdbc.cliente);

		List<Relevo.Periodo> historial = repo.historial(34);

		assertThat(historial).extracting(Relevo.Periodo::idPersona).containsExactly(PEDRO, CATALINA);
		assertThat(historial.get(0).hasta()).isNull();
		assertThat(historial.get(0).registradoPor()).isEqualTo(19000111L);
		assertThat(historial.get(1).motivo()).isEqualTo("RENUNCIA");
		assertThat(historial.get(1).cerradoPor()).isEqualTo(19000111L);
		assertThat(historial.get(1).nombre()).isNull();                             // el nombre lo pone el servicio
		assertThat(jdbc.ultima().sql()).contains("ORDER BY hasta IS NOT NULL");
	}

	@Test
	void cerrarGuardaElMotivoLaEtapaQueTeniaYApagaLaVigilancia() {
		jdbc.responderValor("FROM procesos.terminos", 2).responderValor("FROM procesos.actuaciones", 1)
				.responderValor("SELECT vigilado", true);
		CierreRepositorioJdbc repo = new CierreRepositorioJdbc(jdbc.cliente);

		Cierre.Efectos efectos = repo.cerrar(34,
				new Cierre(Cierre.Motivo.ARCHIVO, LocalDate.of(2026, 11, 11), "Desistimiento tácito"), CATALINA);

		assertThat(efectos).isEqualTo(new Cierre.Efectos(2, 1, true));
		assertThat(jdbc.ultima().sql()).contains("etapa_antes_cierre = etapa").contains("etapa = 'CERRADO'")
				.contains("vigilado = false");
		assertThat(jdbc.ultima().params()).containsEntry("estado", "ARCHIVADO").containsEntry("motivo", "ARCHIVO")
				.containsEntry("fecha", LocalDate.of(2026, 11, 11)).containsEntry("nota", "Desistimiento tácito")
				.containsEntry("autor", CATALINA);
	}

	@Test
	void reabrirLoDejaActivoEnLaEtapaIndicadaYBorraElCierre() {
		CierreRepositorioJdbc repo = new CierreRepositorioJdbc(jdbc.cliente);

		repo.reabrir(34, EtapaProceso.PRUEBAS);

		assertThat(jdbc.ultima().sql()).contains("estado = 'ACTIVO'").contains("motivo_cierre = NULL")
				.contains("etapa_antes_cierre = NULL");
		assertThat(jdbc.ultima().params()).containsEntry("etapa", "PRUEBAS").containsEntry("p", 34);
	}

}
