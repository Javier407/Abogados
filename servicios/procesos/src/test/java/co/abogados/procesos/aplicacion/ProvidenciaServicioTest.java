package co.abogados.procesos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.NuevaProvidencia;
import co.abogados.procesos.dominio.modelo.ActuacionDetectada;
import co.abogados.procesos.dominio.modelo.FiltroProcesos;
import co.abogados.procesos.dominio.modelo.Proceso;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.ProvidenciaVista;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.ProvidenciaRepositorio;
import co.abogados.procesos.infraestructura.eventos.ConsumidorActuacionesPrueba;

/** Actuaciones detectadas por la vigilancia: registro idempotente en el expediente. */
class ProvidenciaServicioTest {

	static final String RADICADO = "11001400301520260077700";

	static final Clock HOY = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);

	ProvidenciasEnMemoria providencias;
	ExpedienteServicioTest.ExpedienteEnMemoria expediente;
	ProvidenciaServicio servicio;

	@BeforeEach
	void preparar() {
		providencias = new ProvidenciasEnMemoria();
		expediente = new ExpedienteServicioTest.ExpedienteEnMemoria();
		servicio = new ProvidenciaServicio(providencias, new ProcesoUnico(), expediente, HOY);
	}

	static ActuacionDetectada actuacion(String referencia, int idProceso, String radicado) {
		return new ActuacionDetectada(radicado, idProceso, referencia, LocalDate.of(2026, 10, 15), "AUTO",
				"Auto requiere al demandante", "Allegue el poder en 5 días", "SIMULADA");
	}

	@Test
	void registraLaActuacionComoProvidenciaDeVigilancia() {
		Optional<Integer> id = servicio.registrarDetectada(actuacion("SIM:1", 34, RADICADO));

		assertThat(id).isPresent();
		assertThat(servicio.deProceso(34)).singleElement().satisfies(p -> {
			assertThat(p.fuente()).isEqualTo("VIGILANCIA");
			assertThat(p.clase()).isEqualTo("Auto requiere al demandante");
			assertThat(p.decision()).isEqualTo("Allegue el poder en 5 días");
		});
	}

	@Test
	void unEventoRepetidoNoDuplicaLaProvidencia() {
		servicio.registrarDetectada(actuacion("SIM:1", 34, RADICADO));

		assertThat(servicio.registrarDetectada(actuacion("SIM:1", 34, RADICADO))).isEmpty();
		assertThat(servicio.deProceso(34)).hasSize(1);
	}

	@Test
	void ignoraUnaActuacionCuyoRadicadoNoEsElDelProceso() {
		assertThat(servicio.registrarDetectada(actuacion("SIM:2", 34, "11001310303220240045800"))).isEmpty();
		assertThat(servicio.registrarDetectada(actuacion("SIM:3", 99, RADICADO))).isEmpty();
		assertThat(providencias.datos).isEmpty();
	}

	@Test
	void consultarProvidenciasDeUnProcesoInexistenteEsUn404() {
		assertThatThrownBy(() -> servicio.deProceso(99)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void unMensajeQueNoCumpleElContratoSeDescartaSinReintentar() {
		Map<String, Object> incompleto = Map.of("radicado", RADICADO, "id_proceso", 34);
		assertThatThrownBy(() -> ConsumidorActuacionesPrueba.leer(incompleto))
				.isInstanceOf(AmqpRejectAndDontRequeueException.class);

		Map<String, Object> completo = new HashMap<>(Map.of("radicado", RADICADO, "id_proceso", 34,
				"referencia", "PORTAL:9", "fecha", "2026-10-15", "tipo", "AUTO", "actuacion", "Fijación estado",
				"fuente", "PORTAL_HTTP"));
		completo.put("anotacion", null);
		assertThat(ConsumidorActuacionesPrueba.leer(completo).fecha()).isEqualTo(LocalDate.of(2026, 10, 15));
	}

	// ----------------------------------------------------------------- registro manual (F6)

	@Test
	void unaSentenciaManualQuedaConSuSentidoYLlevaElProcesoASentencia() {
		ProvidenciaVista p = servicio.registrarManual(34, new NuevaProvidencia("SENTENCIA", "Sentencia de primera instancia",
				LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15), "Accede a las pretensiones", "FAVORABLE"), 79123456L);

		assertThat(p.fuente()).isEqualTo("MANUAL");
		assertThat(p.sentido()).isEqualTo("FAVORABLE");
		assertThat(expediente.etapas).containsExactly(EtapaProceso.SENTENCIA);
	}

	@Test
	void unAutoManualNoCambiaLaEtapa() {
		servicio.registrarManual(34, new NuevaProvidencia("AUTO", "Fija fecha de audiencia",
				LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15), null, null), null);

		assertThat(expediente.etapas).isEmpty();
		assertThat(servicio.deProceso(34)).hasSize(1);
	}

	@Test
	void reglasDeLaProvidenciaManual() {
		LocalDate dia = LocalDate.of(2026, 10, 14);
		assertThatThrownBy(() -> new NuevaProvidencia("SENTENCIA", "Fallo", dia, dia, null, null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("favorable");
		assertThatThrownBy(() -> new NuevaProvidencia("AUTO", "Admite", dia, dia, null, "FAVORABLE"))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> new NuevaProvidencia("AUTO", "Admite", dia, dia.minusDays(1), null, null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("estado");
		assertThatThrownBy(() -> servicio.registrarManual(34, new NuevaProvidencia("AUTO", "Admite",
				LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20), null, null), null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("futura");
		assertThatThrownBy(() -> servicio.registrarManual(34, new NuevaProvidencia("AUTO", "Admite",
				LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null, null), null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("radicación");
	}

	// ----------------------------------------------------------------- dobles

	static class ProcesoUnico implements ProcesoRepositorio {
		public List<ProcesoVista> listar(FiltroProcesos f) { return List.of(); }
		public Optional<ProcesoVista> buscar(int id) {
			return id != 34 ? Optional.empty() : Optional.of(new ProcesoVista(34, RADICADO,
					"11001-40-03-015-2026-00777-00", (short) 2, "Verbal sumario", "CIVIL", "Civil y comercial",
					"Juzgado 015", null, "Juzgado 015", 0, 30, "DEMANDANTE", "Cobro", BigDecimal.TEN, "MENOR",
					"REPRESENTACION", "ACTIVO", "ADMISION", LocalDate.of(2026, 10, 1), true, 79123456L));
		}
		public Optional<Integer> idPorRadicado(Radicado r) { return Optional.empty(); }
		public int crear(Proceso p, String n) { throw new UnsupportedOperationException(); }
		public void actualizar(Proceso p) { throw new UnsupportedOperationException(); }
		public int contarProvidencias(int id) { return 0; }
		public void eliminar(int id) { }
	}

	static class ProvidenciasEnMemoria implements ProvidenciaRepositorio {
		final List<ProvidenciaVista> datos = new ArrayList<>();
		final List<String> referencias = new ArrayList<>();

		public List<ProvidenciaVista> deProceso(int idProceso) {
			return datos.stream().filter(p -> p.idProceso() == idProceso).toList();
		}
		public boolean existeReferencia(String r) { return referencias.contains(r); }
		public int registrarDetectada(int idProceso, ActuacionDetectada a) {
			int id = datos.size() + 1;
			datos.add(new ProvidenciaVista(id, idProceso, a.tipo(), a.actuacion(), a.fecha(), a.fecha(), a.anotacion(),
					null, "VIGILANCIA"));
			referencias.add(a.referencia());
			return id;
		}
		public int registrarManual(int idProceso, NuevaProvidencia p, Long quien) {
			int id = datos.size() + 1;
			datos.add(new ProvidenciaVista(id, idProceso, p.tipo(), p.clase(), p.fechaProvidencia(), p.fechaEstado(),
					p.decision(), p.sentido(), "MANUAL"));
			return id;
		}
	}

}
