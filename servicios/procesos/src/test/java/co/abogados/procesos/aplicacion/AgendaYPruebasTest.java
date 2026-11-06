package co.abogados.procesos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.AudienciaVista;
import co.abogados.procesos.dominio.modelo.AvisoAudiencia;
import co.abogados.procesos.dominio.modelo.DiaInhabil;
import co.abogados.procesos.dominio.modelo.FiltroTerminos;
import co.abogados.procesos.dominio.modelo.Prueba;
import co.abogados.procesos.dominio.modelo.Termino;
import co.abogados.procesos.dominio.modelo.TerminoVista;
import co.abogados.procesos.dominio.puertos.AgendaRepositorio;
import co.abogados.procesos.dominio.puertos.PruebaRepositorio;
import co.abogados.procesos.dominio.puertos.TerminoRepositorio;

/**
 * F7: aviso de audiencias el día hábil anterior y pruebas con fecha de entrega controlada por un término.
 * Festivos de prueba: lunes 2 y lunes 16 de noviembre de 2026.
 */
class AgendaYPruebasTest {

	static final CalendarioServicio CALENDARIO = new CalendarioServicio(() -> List.of(
			new DiaInhabil(LocalDate.of(2026, 11, 2), "Todos los Santos", "FESTIVO"),
			new DiaInhabil(LocalDate.of(2026, 11, 16), "Independencia de Cartagena", "FESTIVO")));

	static Clock dia(int mes, int dia) {
		return Clock.fixed(Instant.parse(String.format("2026-%02d-%02dT15:00:00Z", mes, dia)), ZoneOffset.UTC);
	}

	// ----------------------------------------------------------------- audiencias

	static AudienciaVista audiencia(int id, LocalDate fecha, Long asistente) {
		return new AudienciaVista(id, 34, "11001400301520260077700", "11001-40-03-015-2026-00777-00", "Cobro", fecha,
				LocalTime.of(9, 0), "Audiencia inicial", "Sala 3", asistente);
	}

	static final List<AudienciaVista> AGENDA = List.of(
			audiencia(1, LocalDate.of(2026, 10, 19), 52345678L),     // lunes
			audiencia(2, LocalDate.of(2026, 10, 19), null),          // sin quién la atienda
			audiencia(3, LocalDate.of(2026, 10, 20), 52345678L),     // martes
			audiencia(4, LocalDate.of(2026, 11, 3), 79123456L));     // martes después del festivo

	static final AgendaRepositorio REPO_AGENDA = (desde, hasta, asistente) -> AGENDA.stream()
			.filter(a -> !a.fecha().isBefore(desde) && !a.fecha().isAfter(hasta))
			.filter(a -> asistente == null || asistente.equals(a.idAsistente())).toList();

	@Test
	void elViernesAvisaLasAudienciasDelLunes() {
		List<AvisoAudiencia> avisos = new ArrayList<>();
		AgendaServicio agenda = new AgendaServicio(REPO_AGENDA, CALENDARIO, avisos::add, dia(10, 16));

		assertThat(agenda.avisarAudiencias()).isEqualTo(1);
		assertThat(avisos).singleElement().satisfies(a -> {
			assertThat(a.idActuacion()).isEqualTo(1);
			assertThat(a.idAbogado()).isEqualTo(52345678L);
			assertThat(a.hora()).isEqualTo(LocalTime.of(9, 0));
		});
	}

	@Test
	void antesDeUnPuenteFestivoAvisaLaDelDiaHabilSiguiente() {
		List<AvisoAudiencia> avisos = new ArrayList<>();
		new AgendaServicio(REPO_AGENDA, CALENDARIO, avisos::add, dia(10, 30)).avisarAudiencias();   // viernes 30/10
		assertThat(avisos).extracting(AvisoAudiencia::idActuacion).containsExactly(4);              // martes 3/11
	}

	@Test
	void laAgendaPideUnRangoValido() {
		AgendaServicio agenda = new AgendaServicio(REPO_AGENDA, CALENDARIO, a -> { }, dia(10, 16));
		assertThat(agenda.audiencias(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 52345678L)).hasSize(2);
		assertThatThrownBy(() -> agenda.audiencias(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), null))
				.isInstanceOf(ReglaNegocioException.class);
	}

	// ----------------------------------------------------------------- pruebas

	PruebasEnMemoria pruebas;
	TerminosEnMemoria terminos;
	PruebaServicio servicio;

	@BeforeEach
	void preparar() {
		pruebas = new PruebasEnMemoria();
		terminos = new TerminosEnMemoria();
		servicio = new PruebaServicio(new ProvidenciaServicioTest.ProcesoUnico(), pruebas, terminos, CALENDARIO,
				new ExpedienteServicioTest.ClientesFalsos(), dia(10, 16));
	}

	static Prueba pericial(Prueba.Estado estado, LocalDate entrega) {
		return new Prueba(null, 34, Prueba.Tipo.PERICIAL, "Dictamen de valoración psicológica", 3, estado, entrega, null);
	}

	@Test
	void unaPericialConFechaDeEntregaCreaSuTermino() {
		Prueba p = servicio.crear(34, pericial(Prueba.Estado.DECRETADA, LocalDate.of(2026, 10, 30)));

		assertThat(p.idTermino()).isNotNull();
		Termino t = terminos.datos.get(p.idTermino());
		assertThat(t.descripcion()).isEqualTo("Entrega de prueba: Dictamen de valoración psicológica");
		assertThat(t.diasHabiles()).isEqualTo(10);
		assertThat(t.fechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 30));
		assertThat(t.idResponsable()).isEqualTo(79123456L);
	}

	@Test
	void siLaEntregaCaeEnDiaInhabilVenceElDiaHabilAnterior() {
		Prueba p = servicio.crear(34, pericial(Prueba.Estado.DECRETADA, LocalDate.of(2026, 11, 2)));   // festivo
		assertThat(terminos.datos.get(p.idTermino()).fechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 30));
	}

	@Test
	void sinFechaOYaPracticadaNoCreaTermino() {
		assertThat(servicio.crear(34, pericial(Prueba.Estado.DECRETADA, null)).idTermino()).isNull();
		assertThat(servicio.crear(34, pericial(Prueba.Estado.PRACTICADA, LocalDate.of(2026, 10, 30))).idTermino()).isNull();
		assertThat(terminos.datos).isEmpty();
	}

	@Test
	void practicarCumpleElTerminoNegarLoBorraYCambiarLaFechaLoReemplaza() {
		Prueba p = servicio.crear(34, pericial(Prueba.Estado.DECRETADA, LocalDate.of(2026, 10, 30)));
		int primero = p.idTermino();

		Prueba otraFecha = servicio.actualizar(34, p.idPrueba(), pericial(Prueba.Estado.DECRETADA, LocalDate.of(2026, 11, 6)));
		assertThat(terminos.datos).doesNotContainKey(primero);
		assertThat(terminos.datos.get(otraFecha.idTermino()).fechaVencimiento()).isEqualTo(LocalDate.of(2026, 11, 6));

		Prueba practicada = servicio.actualizar(34, p.idPrueba(), pericial(Prueba.Estado.PRACTICADA, LocalDate.of(2026, 11, 6)));
		assertThat(terminos.cumplidos).containsExactly(practicada.idTermino());

		Prueba negada = servicio.crear(34, pericial(Prueba.Estado.DECRETADA, LocalDate.of(2026, 10, 30)));
		int deLaNegada = negada.idTermino();
		assertThat(servicio.actualizar(34, negada.idPrueba(), pericial(Prueba.Estado.NEGADA, LocalDate.of(2026, 10, 30)))
				.idTermino()).isNull();
		assertThat(terminos.datos).doesNotContainKey(deLaNegada);
	}

	@Test
	void eliminarLaPruebaBorraSuTerminoPendiente() {
		Prueba p = servicio.crear(34, pericial(Prueba.Estado.DECRETADA, LocalDate.of(2026, 10, 30)));
		servicio.eliminar(34, p.idPrueba());
		assertThat(pruebas.datos).isEmpty();
		assertThat(terminos.datos).isEmpty();
	}

	@Test
	void reglasDeLasPruebas() {
		assertThatThrownBy(() -> servicio.crear(34, new Prueba(null, 34, Prueba.Tipo.PERICIAL, "Avalúo", 99,
				Prueba.Estado.SOLICITADA, null, null))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("perito");
		assertThatThrownBy(() -> new Prueba(null, 34, Prueba.Tipo.DOCUMENTAL, "Facturas", 3, null, null, null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pericial");
		assertThatThrownBy(() -> servicio.crear(34, pericial(Prueba.Estado.DECRETADA, LocalDate.of(2026, 10, 16))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("posterior a hoy");
		assertThat(new Prueba(null, 34, Prueba.Tipo.TESTIMONIAL, "Testigo", null, null, null, null).estado())
				.isEqualTo(Prueba.Estado.SOLICITADA);
	}

	// ----------------------------------------------------------------- dobles

	static class PruebasEnMemoria implements PruebaRepositorio {
		final Map<Integer, Prueba> datos = new HashMap<>();
		int secuencia;

		public List<Prueba> deProceso(int id) { return datos.values().stream().filter(p -> p.idProceso() == id).toList(); }
		public Optional<Prueba> buscar(int id) { return Optional.ofNullable(datos.get(id)); }
		public int crear(Prueba p) {
			int id = ++secuencia;
			datos.put(id, new Prueba(id, p.idProceso(), p.tipo(), p.descripcion(), p.idPerito(), p.estado(),
					p.fechaEntrega(), p.idTermino()));
			return id;
		}
		public void actualizar(Prueba p) { datos.put(p.idPrueba(), p); }
		public void eliminar(int id) { datos.remove(id); }
	}

	static class TerminosEnMemoria implements TerminoRepositorio {
		final Map<Integer, Termino> datos = new HashMap<>();
		final List<Integer> cumplidos = new ArrayList<>();
		int secuencia;

		public List<TerminoVista> listar(FiltroTerminos f) { return List.of(); }
		public Optional<TerminoVista> buscar(int id) {
			Termino t = datos.get(id);
			return t == null ? Optional.empty() : Optional.of(new TerminoVista(id, t.idProceso(), "", "", "", "",
					t.idProvidencia(), t.descripcion(), t.fechaInicio(), t.diasHabiles(), t.fechaVencimiento(),
					cumplidos.contains(id) ? "CUMPLIDO" : "PENDIENTE", t.idResponsable(), 0, "AL_DIA"));
		}
		public int crear(Termino t) { datos.put(++secuencia, t); return secuencia; }
		public void marcarCumplido(int id) { cumplidos.add(id); }
		public void eliminar(int id) { datos.remove(id); }
		public boolean providenciaDelProceso(int idProvidencia, int idProceso) { return false; }
		public List<TerminoVista> paraAvisar(int a, int b) { return List.of(); }
	}

}
