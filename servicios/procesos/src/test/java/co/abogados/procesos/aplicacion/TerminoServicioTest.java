package co.abogados.procesos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.AvisoTermino;
import co.abogados.procesos.dominio.modelo.CalendarioJudicial;
import co.abogados.procesos.dominio.modelo.DiaInhabil;
import co.abogados.procesos.dominio.modelo.FiltroProcesos;
import co.abogados.procesos.dominio.modelo.FiltroTerminos;
import co.abogados.procesos.dominio.modelo.Proceso;
import co.abogados.procesos.dominio.modelo.ProcesoCreado;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.modelo.Termino;
import co.abogados.procesos.dominio.modelo.TerminoVista;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.PublicadorEventos;
import co.abogados.procesos.dominio.puertos.TerminoRepositorio;

/** Términos con dobles en memoria y un reloj fijo en el jueves 15 de octubre de 2026. */
class TerminoServicioTest {

	static final LocalDate HOY = LocalDate.of(2026, 10, 15);
	static final long RESPONSABLE = 52345678L;
	static final long OTRA_ABOGADA = 1020456789L;

	TerminosEnMemoria terminos;
	List<AvisoTermino> avisos;
	TerminoServicio servicio;
	Map<Integer, ProcesoVista> procesos;

	@BeforeEach
	void preparar() {
		Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Bogota"));
		CalendarioServicio calendario = new CalendarioServicio(
				() -> List.of(new DiaInhabil(LocalDate.of(2026, 10, 12), "Día de la Raza", "FESTIVO"),
						new DiaInhabil(LocalDate.of(2026, 11, 2), "Todos los Santos", "FESTIVO")));
		terminos = new TerminosEnMemoria(calendario.calendario());
		avisos = new ArrayList<>();
		procesos = new HashMap<>();
		procesos.put(2, proceso(2, "ACTIVO"));
		procesos.put(9, proceso(9, "TERMINADO"));
		PublicadorEventos publicador = new PublicadorEventos() {
			public void procesoCreado(ProcesoCreado e) { }
			public void avisoTermino(AvisoTermino a) { avisos.add(a); }
		};
		servicio = new TerminoServicio(terminos, new ProcesosFijos(), calendario, new GabineteFijo(), publicador,
				reloj, 5, 3);
	}

	static ProcesoVista proceso(int id, String estado) {
		return new ProcesoVista(id, "11001310303220240045800", "11001-31-03-032-2024-00458-00", (short) 1, "Verbal",
				"CIVIL", "Civil y comercial", "Juzgado 032", null, "Juzgado 032", 0, 2, "DEMANDANTE", "Obra",
				BigDecimal.ONE, "MAYOR", "REPRESENTACION", estado, estado.equals("ACTIVO") ? "PRUEBAS" : "CERRADO",
				LocalDate.of(2024, 8, 1), true, RESPONSABLE);
	}

	@Test
	void calculaElVencimientoEnDiasHabilesYAsignaAlResponsableDelProceso() {
		TerminoVista t = servicio.crear(2, new TerminoServicio.NuevoTermino("Contestar la demanda", HOY, 3, null, null));

		assertThat(t.fechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(t.idResponsable()).isEqualTo(RESPONSABLE);
		assertThat(t.estado()).isEqualTo("PENDIENTE");
	}

	@Test
	void puedeAsignarseAOtroAbogadoActivo() {
		TerminoVista t = servicio.crear(2,
				new TerminoServicio.NuevoTermino("Alegatos", HOY, 5, null, OTRA_ABOGADA));

		assertThat(t.idResponsable()).isEqualTo(OTRA_ABOGADA);
	}

	@Test
	void enUnProcesoTerminadoNoCorrenTerminos() {
		assertThatThrownBy(() -> servicio.crear(9, new TerminoServicio.NuevoTermino("X", HOY, 3, null, null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("TERMINADO");
	}

	@Test
	void laNotificacionNoPuedeSerFutura() {
		assertThatThrownBy(() -> servicio.crear(2,
				new TerminoServicio.NuevoTermino("X", HOY.plusDays(1), 3, null, null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("futura");
	}

	@Test
	void laProvidenciaDebeSerDelMismoProceso() {
		assertThatThrownBy(() -> servicio.crear(2, new TerminoServicio.NuevoTermino("X", HOY, 3, 999, null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("providencia 999");
	}

	@Test
	void unTerminoCumplidoNoSeCumpleDosVeces() {
		int id = servicio.crear(2, new TerminoServicio.NuevoTermino("X", HOY, 3, null, null)).idTermino();

		assertThat(servicio.cumplir(id).estado()).isEqualTo("CUMPLIDO");
		assertThatThrownBy(() -> servicio.cumplir(id)).isInstanceOf(ConflictoException.class);
	}

	@Test
	void laRevisionAvisaLosPorVencerYLosVencidosRecientes() {
		servicio.crear(2, new TerminoServicio.NuevoTermino("Vence en 3 días", HOY, 3, null, null));
		servicio.crear(2, new TerminoServicio.NuevoTermino("Vence en 20 días", HOY, 20, null, null));
		servicio.crear(2, new TerminoServicio.NuevoTermino("Venció ayer", LocalDate.of(2026, 10, 13), 1, null, null));
		servicio.crear(2, new TerminoServicio.NuevoTermino("Venció hace un mes", LocalDate.of(2026, 9, 10), 1, null, null));
		int cumplido = servicio.crear(2, new TerminoServicio.NuevoTermino("Ya cumplido", HOY, 2, null, null)).idTermino();
		servicio.cumplir(cumplido);

		TerminoServicio.Revision r = servicio.revisarVencimientos();

		assertThat(r.porVencer()).isEqualTo(1);
		assertThat(r.vencidos()).isEqualTo(1);
		assertThat(avisos).extracting(AvisoTermino::descripcion).containsExactlyInAnyOrder("Vence en 3 días", "Venció ayer");
		assertThat(avisos).filteredOn(a -> a.tipo() == AvisoTermino.Tipo.POR_VENCER).singleElement()
				.satisfies(a -> {
					assertThat(a.diasHabilesRestantes()).isEqualTo(3);
					assertThat(a.idAbogado()).isEqualTo(RESPONSABLE);
					assertThat(a.vence()).isEqualTo(LocalDate.of(2026, 10, 20));
				});
	}

	// ----------------------------------------------------------------- dobles

	class ProcesosFijos implements ProcesoRepositorio {
		public List<ProcesoVista> listar(FiltroProcesos f) { return List.copyOf(procesos.values()); }
		public Optional<ProcesoVista> buscar(int id) { return Optional.ofNullable(procesos.get(id)); }
		public Optional<Integer> idPorRadicado(Radicado r) { return Optional.empty(); }
		public int crear(Proceso p, String n) { throw new UnsupportedOperationException(); }
		public void actualizar(Proceso p) { throw new UnsupportedOperationException(); }
		public int contarProvidencias(int id) { return 0; }
		public void eliminar(int id) { }
	}

	static class GabineteFijo implements DirectorioGabinete {
		public Optional<Cliente> cliente(int id) { return Optional.empty(); }
		public List<ClienteResumen> buscarClientes(String texto) { return List.of(); }
		public Optional<Abogado> abogado(long id) {
			return id == OTRA_ABOGADA ? Optional.of(new Abogado(id, "Laura", "Pineda", true)) : Optional.empty();
		}
	}

	/** Calcula los días restantes y la situación como lo hace la vista v_terminos. */
	static class TerminosEnMemoria implements TerminoRepositorio {
		final Map<Integer, Termino> datos = new HashMap<>();
		final CalendarioJudicial calendario;
		int secuencia;

		TerminosEnMemoria(CalendarioJudicial calendario) { this.calendario = calendario; }

		TerminoVista vista(Termino t) {
			int restantes = calendario.diasHabilesEntre(HOY, t.fechaVencimiento());
			String situacion = t.estado().name().equals("CUMPLIDO") ? "CUMPLIDO"
					: t.fechaVencimiento().isBefore(HOY) ? "VENCIDO" : restantes <= 5 ? "POR_VENCER" : "AL_DIA";
			return new TerminoVista(t.idTermino(), t.idProceso(), "11001310303220240045800",
					"11001-31-03-032-2024-00458-00", "Civil y comercial", "Obra", t.idProvidencia(), t.descripcion(),
					t.fechaInicio(), t.diasHabiles(), t.fechaVencimiento(), t.estado().name(), t.idResponsable(),
					restantes, situacion);
		}

		public List<TerminoVista> listar(FiltroTerminos f) { return datos.values().stream().map(this::vista).toList(); }
		public Optional<TerminoVista> buscar(int id) { return Optional.ofNullable(datos.get(id)).map(this::vista); }
		public int crear(Termino t) {
			int id = ++secuencia;
			datos.put(id, new Termino(id, t.idProceso(), t.idProvidencia(), t.descripcion(), t.fechaInicio(),
					t.diasHabiles(), t.fechaVencimiento(), t.estado(), t.idResponsable()));
			return id;
		}
		public void marcarCumplido(int id) {
			Termino t = datos.get(id);
			datos.put(id, new Termino(id, t.idProceso(), t.idProvidencia(), t.descripcion(), t.fechaInicio(),
					t.diasHabiles(), t.fechaVencimiento(), co.abogados.procesos.dominio.modelo.EstadoTermino.CUMPLIDO,
					t.idResponsable()));
		}
		public void eliminar(int id) { datos.remove(id); }
		public boolean providenciaDelProceso(int idProvidencia, int idProceso) { return idProvidencia == 1; }
		public List<TerminoVista> paraAvisar(int diasAviso, int recordar) {
			return datos.values().stream().map(this::vista)
					.filter(v -> v.estado().equals("PENDIENTE"))
					.filter(v -> v.diasHabilesRestantes() >= -recordar && v.diasHabilesRestantes() <= diasAviso)
					.toList();
		}
	}

}
