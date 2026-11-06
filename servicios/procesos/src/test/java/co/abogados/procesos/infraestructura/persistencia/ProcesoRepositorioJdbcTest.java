package co.abogados.procesos.infraestructura.persistencia;

import static co.abogados.procesos.infraestructura.persistencia.JdbcFalso.fila;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.modelo.ClaseCuantia;
import co.abogados.procesos.dominio.modelo.EstadoProceso;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.FiltroProcesos;
import co.abogados.procesos.dominio.modelo.Proceso;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.modelo.RolCliente;
import co.abogados.procesos.dominio.modelo.TipoServicio;

/** El SQL que arma el repositorio de procesos y la lectura de la vista v_procesos, sin base de datos. */
class ProcesoRepositorioJdbcTest {

	JdbcFalso jdbc = new JdbcFalso();
	ProcesoRepositorioJdbc repo = new ProcesoRepositorioJdbc(jdbc.cliente);

	static Map<String, Object> vista(int id, Object despachoActual, Object responsable) {
		return fila("id_proceso", id, "radicado", "11001310303220260050100",
				"radicado_legible", "11001-31-03-032-2026-00501-00", "id_tipo", 1, "tipo_proceso", "Verbal",
				"codigo_rama", "CIVIL", "rama", "Civil y comercial", "despacho_origen", "Juzgado 032",
				"id_despacho_actual", despachoActual, "despacho_actual", "Juzgado 032", "instancia", 0,
				"id_cliente", 7, "rol_cliente", "DEMANDANTE", "asunto", "Incumplimiento", "cuantia",
				new BigDecimal("320000000"), "clase_cuantia", "MAYOR", "tipo_servicio", "REPRESENTACION",
				"estado", "ACTIVO", "etapa", "DEMANDA", "fecha_radicacion", LocalDate.of(2026, 9, 1), "vigilado", true,
				"id_responsable", responsable);
	}

	static Proceso proceso(Integer id) {
		return new Proceso(id, Radicado.de("11001310303220260050100"), (short) 1, null, 7, RolCliente.DEMANDANTE,
				"Incumplimiento", new BigDecimal("320000000"), ClaseCuantia.MAYOR, TipoServicio.REPRESENTACION,
				EstadoProceso.ACTIVO, EtapaProceso.DEMANDA, LocalDate.of(2026, 9, 1), true, 52345678L);
	}

	@Test
	void sinFiltrosNoAgregaCondiciones() {
		jdbc.responder("v_procesos", vista(31, null, 52345678L));

		List<ProcesoVista> lista = repo.listar(new FiltroProcesos(null, null, null, null, null));

		assertThat(jdbc.ultima().sql()).doesNotContain("WHERE").endsWith("ORDER BY v.fecha_radicacion DESC, v.id_proceso DESC");
		assertThat(lista).singleElement().satisfies(p -> {
			assertThat(p.idProceso()).isEqualTo(31);
			assertThat(p.radicadoLegible()).isEqualTo("11001-31-03-032-2026-00501-00");
			assertThat(p.idDespachoActual()).isNull();
			assertThat(p.idResponsable()).isEqualTo(52345678L);
			assertThat(p.fechaRadicacion()).isEqualTo(LocalDate.of(2026, 9, 1));
		});
	}

	@Test
	void combinaTodosLosFiltrosConAnd() {
		repo.listar(new FiltroProcesos(EstadoProceso.SUSPENDIDO, "laboral", 7, 52345678L, " 2026-005 "));

		JdbcFalso.Sentencia s = jdbc.ultima();
		assertThat(s.sql()).contains("v.estado = :estado AND v.codigo_rama = :rama AND v.id_cliente = :cliente AND EXISTS")
				.contains("v.asunto ILIKE :texto");
		assertThat(s.params()).containsEntry("estado", "SUSPENDIDO").containsEntry("rama", "LABORAL")
				.containsEntry("cliente", 7).containsEntry("abogado", 52345678L)
				.containsEntry("texto", "%2026-005%").containsEntry("digitos", "%2026005%");
	}

	@Test
	void unTextoSinDigitosNoBuscaPorRadicado() {
		repo.listar(new FiltroProcesos(null, "  ", null, null, "Altamira"));

		assertThat(jdbc.ultima().params()).containsEntry("digitos", "-").containsEntry("texto", "%Altamira%")
				.doesNotContainKey("rama");
	}

	@Test
	void buscaPorIdLeyendoElDespachoActualYSinResponsable() {
		jdbc.responder("WHERE v.id_proceso = :id", vista(31, 4, null));

		ProcesoVista p = repo.buscar(31).orElseThrow();

		assertThat(p.idDespachoActual()).isEqualTo((short) 4);
		assertThat(p.idResponsable()).isNull();
		assertThat(jdbc.ultima().params()).containsEntry("id", 31);
		assertThat(repo.idPorRadicado(Radicado.de("11001310303220260050100"))).isEmpty();
	}

	@Test
	void crearGuardaElProcesoSuResponsableYLaParteCliente() {
		jdbc.responderValor("RETURNING id_proceso", 31);

		int id = repo.crear(proceso(null), "Constructora Altamira SAS");

		assertThat(id).isEqualTo(31);
		assertThat(jdbc.ejecutadas).hasSize(3);
		assertThat(jdbc.ejecutadas.get(0).params()).containsEntry("radicado", "11001310303220260050100")
				.containsEntry("clase", "MAYOR").containsEntry("estado", "ACTIVO").containsEntry("vigilado", true);
		assertThat(jdbc.ejecutadas.get(1).sql()).contains("'RESPONSABLE'");
		assertThat(jdbc.ejecutadas.get(1).params()).containsEntry("persona", 52345678L);
		assertThat(jdbc.ejecutadas.get(2).params()).containsEntry("nombre", "Constructora Altamira SAS")
				.containsEntry("rol", "DEMANDANTE");
	}

	@Test
	void actualizarPasaElResponsableAnteriorAApoyo() {
		repo.actualizar(proceso(31));

		assertThat(jdbc.ejecutadas).hasSize(4);
		assertThat(jdbc.ejecutadas.get(0).params()).containsEntry("id", 31).containsEntry("etapa", "DEMANDA");
		assertThat(jdbc.ejecutadas.get(2).sql()).contains("SET rol = 'APOYO'");
		assertThat(jdbc.ejecutadas.get(3).sql()).contains("ON CONFLICT");
	}

	@Test
	void cuentaProvidenciasYElimina() {
		jdbc.responderValor("count(*)", 2);

		assertThat(repo.contarProvidencias(31)).isEqualTo(2);
		repo.eliminar(31);
		assertThat(jdbc.ultima().sql()).startsWith("DELETE FROM procesos.procesos");
	}

}
