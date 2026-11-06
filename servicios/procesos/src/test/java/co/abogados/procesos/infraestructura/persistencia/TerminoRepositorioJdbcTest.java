package co.abogados.procesos.infraestructura.persistencia;

import static co.abogados.procesos.infraestructura.persistencia.JdbcFalso.fila;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.modelo.EstadoTermino;
import co.abogados.procesos.dominio.modelo.FiltroTerminos;
import co.abogados.procesos.dominio.modelo.SituacionTermino;
import co.abogados.procesos.dominio.modelo.Termino;
import co.abogados.procesos.dominio.modelo.TerminoVista;

class TerminoRepositorioJdbcTest {

	JdbcFalso jdbc = new JdbcFalso();
	TerminoRepositorioJdbc repo = new TerminoRepositorioJdbc(jdbc.cliente);

	static final java.util.Map<String, Object> FILA = fila("id_termino", 9, "id_proceso", 31,
			"radicado", "11001310303220260050100", "radicado_legible", "11001-31-03-032-2026-00501-00",
			"rama", "Civil", "asunto", "Incumplimiento", "id_providencia", null, "descripcion", "Contestar demanda",
			"fecha_inicio", LocalDate.of(2026, 10, 1), "dias_habiles", 20, "fecha_vencimiento", LocalDate.of(2026, 10, 30),
			"estado", "PENDIENTE", "id_responsable", 52345678L, "dias_habiles_restantes", 3, "situacion", "POR_VENCER");

	@Test
	void listarArmaElFiltroYLeeLaVista() {
		jdbc.responder("v_terminos", FILA);

		var lista = repo.listar(new FiltroTerminos(SituacionTermino.POR_VENCER, 52345678L, 31,
				LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

		assertThat(jdbc.ultima().sql()).contains("WHERE t.situacion = :situacion AND t.id_responsable = :responsable")
				.contains("t.fecha_vencimiento <= :hasta").endsWith("ORDER BY t.fecha_vencimiento, t.id_termino");
		assertThat(jdbc.ultima().params()).containsEntry("situacion", "POR_VENCER").containsEntry("proceso", 31)
				.containsEntry("desde", LocalDate.of(2026, 10, 1));
		assertThat(lista).singleElement().satisfies(t -> {
			assertThat(t.idTermino()).isEqualTo(9);
			assertThat(t.idProvidencia()).isNull();
			assertThat(t.fechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 30));
			assertThat(t.situacion()).isEqualTo("POR_VENCER");
			assertThat(t.diasHabilesRestantes()).isEqualTo(3);
		});
	}

	@Test
	void sinFiltrosNoHayWhere() {
		repo.listar(new FiltroTerminos(null, null, null, null, null));
		assertThat(jdbc.ultima().sql()).doesNotContain("WHERE");
		assertThat(jdbc.ultima().params()).isEmpty();
	}

	@Test
	void buscarCrearCumplirYEliminar() {
		jdbc.responder("WHERE t.id_termino = :id", FILA).responderValor("RETURNING id_termino", 12);

		TerminoVista t = repo.buscar(9).orElseThrow();
		int id = repo.crear(new Termino(null, 31, 5, "Contestar demanda", LocalDate.of(2026, 10, 1), 20,
				LocalDate.of(2026, 10, 30), EstadoTermino.PENDIENTE, 52345678L));

		assertThat(t.descripcion()).isEqualTo("Contestar demanda");
		assertThat(id).isEqualTo(12);
		assertThat(jdbc.ultima().params()).containsEntry("providencia", 5).containsEntry("estado", "PENDIENTE")
				.containsEntry("dias", 20);

		repo.marcarCumplido(12);
		assertThat(jdbc.ultima().sql()).contains("estado = 'CUMPLIDO'");
		repo.eliminar(12);
		assertThat(jdbc.ultima().sql()).startsWith("DELETE FROM procesos.terminos");
	}

	@Test
	void laProvidenciaEsDelProcesoSiLaCuentaEsPositiva() {
		jdbc.responderValor("FROM procesos.providencias", 1);
		assertThat(repo.providenciaDelProceso(5, 31)).isTrue();

		JdbcFalso otro = new JdbcFalso().responderValor("FROM procesos.providencias", 0);
		assertThat(new TerminoRepositorioJdbc(otro.cliente).providenciaDelProceso(5, 32)).isFalse();
	}

	@Test
	void paraAvisarUsaElRangoDeDiasHabiles() {
		jdbc.responder("t.estado = 'PENDIENTE'", FILA);

		assertThat(repo.paraAvisar(5, 3)).hasSize(1);
		assertThat(jdbc.ultima().params()).containsEntry("minimo", -3).containsEntry("maximo", 5);
	}

}
