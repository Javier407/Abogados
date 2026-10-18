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
import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.procesos.dominio.modelo.ClaseCuantia;
import co.abogados.procesos.dominio.modelo.DesgloseRadicado;
import co.abogados.procesos.dominio.modelo.Despacho;
import co.abogados.procesos.dominio.modelo.EstadoProceso;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.FiltroProcesos;
import co.abogados.procesos.dominio.modelo.Proceso;
import co.abogados.procesos.dominio.modelo.ProcesoCreado;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.modelo.Rama;
import co.abogados.procesos.dominio.modelo.RolCliente;
import co.abogados.procesos.dominio.modelo.TipoProceso;
import co.abogados.procesos.dominio.modelo.TipoServicio;
import co.abogados.procesos.dominio.puertos.CatalogoRepositorio;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.PublicadorEventos;

/** Casos de uso con dobles en memoria: no necesita base de datos, RabbitMQ ni el servicio gabinete. */
class ProcesoServicioTest {

	static final long ABOGADA = 52345678L;
	static final long ABOGADO_INACTIVO = 80111222L;
	static final Despacho CIVIL_CTO_32 = new Despacho((short) 2, "Juzgado 032 Civil del Circuito de Bogotá", "Bogotá",
			"11001", "31", "03", "032", "CIVIL");
	static final Despacho LABORAL_CTO_8 = new Despacho((short) 4, "Juzgado 008 Laboral del Circuito de Bogotá",
			"Bogotá", "11001", "31", "05", "008", "LABORAL");

	ProcesosEnMemoria procesos;
	GabineteFalso gabinete;
	List<ProcesoCreado> eventos;
	ProcesoServicio servicio;

	@BeforeEach
	void preparar() {
		procesos = new ProcesosEnMemoria();
		gabinete = new GabineteFalso();
		eventos = new ArrayList<>();
		Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Bogota"));
		PublicadorEventos publicador = new PublicadorEventos() {
			public void procesoCreado(ProcesoCreado e) { eventos.add(e); }
			public void avisoTermino(co.abogados.procesos.dominio.modelo.AvisoTermino a) { }
		};
		servicio = new ProcesoServicio(procesos, new CatalogoFijo(), gabinete, publicador, reloj);
	}

	static Proceso nuevo(String radicado) {
		return new Proceso(null, Radicado.de(radicado), (short) 1, null, 7, RolCliente.DEMANDANTE,
				"Incumplimiento de contrato de obra", new BigDecimal("320000000"), ClaseCuantia.MAYOR,
				TipoServicio.REPRESENTACION, null, EtapaProceso.DEMANDA, LocalDate.of(2026, 9, 1), true, ABOGADA);
	}

	@Test
	void creaElProcesoConSuResponsableYPublicaElEvento() {
		ProcesoVista creado = servicio.crear(nuevo("11001-31-03-032-2026-00501-00"));

		assertThat(creado.radicadoLegible()).isEqualTo("11001-31-03-032-2026-00501-00");
		assertThat(creado.idResponsable()).isEqualTo(ABOGADA);
		assertThat(procesos.nombreCliente.get(creado.idProceso())).isEqualTo("Constructora Altamira SAS");
		assertThat(eventos).singleElement().satisfies(e -> {
			assertThat(e.idProceso()).isEqualTo(creado.idProceso());
			assertThat(e.radicado()).isEqualTo("11001310303220260050100");
			assertThat(e.idAbogadoResponsable()).isEqualTo(ABOGADA);
		});
	}

	@Test
	void noPermiteRadicadosRepetidos() {
		servicio.crear(nuevo("11001310303220260050100"));

		assertThatThrownBy(() -> servicio.crear(nuevo("11001310303220260050100")))
				.isInstanceOf(ConflictoException.class)
				.hasMessageContaining("ya está registrado");
		assertThat(eventos).hasSize(1);
	}

	@Test
	void rechazaUnRadicadoDeUnDespachoNoRegistrado() {
		assertThatThrownBy(() -> servicio.crear(nuevo("99999319999920260050100")))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no está registrado")
				.hasMessageContaining("DANE 99999");
	}

	@Test
	void rechazaUnTipoDeProcesoInexistente() {
		Proceso p = nuevo("11001310303220260050100");
		Proceso conTipoMalo = new Proceso(null, p.radicado(), (short) 999, null, p.idCliente(), p.rolCliente(),
				p.asunto(), p.cuantia(), p.claseCuantia(), p.tipoServicio(), null, p.etapa(), p.fechaRadicacion(),
				true, p.idResponsable());

		assertThatThrownBy(() -> servicio.crear(conTipoMalo))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("tipo de proceso 999");
	}

	@Test
	void rechazaUnaFechaDeRadicacionFutura() {
		Proceso p = nuevo("11001310303220260050100");
		Proceso futuro = new Proceso(null, p.radicado(), p.idTipo(), null, p.idCliente(), p.rolCliente(), p.asunto(),
				p.cuantia(), p.claseCuantia(), p.tipoServicio(), null, p.etapa(), LocalDate.of(2026, 10, 16), true,
				p.idResponsable());

		assertThatThrownBy(() -> servicio.crear(futuro)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("futura");
	}

	@Test
	void enPrimeraInstanciaElDespachoActualDebeSerElDelRadicado() {
		Proceso p = nuevo("11001310303220260050100");
		Proceso otroDespacho = new Proceso(null, p.radicado(), p.idTipo(), (short) 4, p.idCliente(), p.rolCliente(),
				p.asunto(), p.cuantia(), p.claseCuantia(), p.tipoServicio(), null, p.etapa(), p.fechaRadicacion(),
				true, p.idResponsable());

		assertThatThrownBy(() -> servicio.crear(otroDespacho)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("primera instancia");
	}

	@Test
	void elClienteDebeExistirEnGabinete() {
		Proceso p = nuevo("11001310303220260050100");
		Proceso sinCliente = new Proceso(null, p.radicado(), p.idTipo(), null, 404, p.rolCliente(), p.asunto(),
				p.cuantia(), p.claseCuantia(), p.tipoServicio(), null, p.etapa(), p.fechaRadicacion(), true,
				p.idResponsable());

		assertThatThrownBy(() -> servicio.crear(sinCliente)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("cliente 404");
	}

	@Test
	void elResponsableDebeSerUnAbogadoActivo() {
		Proceso p = nuevo("11001310303220260050100");
		Proceso conInactivo = new Proceso(null, p.radicado(), p.idTipo(), null, p.idCliente(), p.rolCliente(),
				p.asunto(), p.cuantia(), p.claseCuantia(), p.tipoServicio(), null, p.etapa(), p.fechaRadicacion(),
				true, ABOGADO_INACTIVO);

		assertThatThrownBy(() -> servicio.crear(conInactivo)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("inactivo");
	}

	@Test
	void siGabineteNoRespondeNoSeGuardaNada() {
		gabinete.caido = true;

		assertThatThrownBy(() -> servicio.crear(nuevo("11001310303220260050100")))
				.isInstanceOf(ServicioNoDisponibleException.class);
		assertThat(procesos.datos).isEmpty();
		assertThat(eventos).isEmpty();
	}

	@Test
	void editarElAsuntoNoConsultaGabinete() {
		int id = servicio.crear(nuevo("11001310303220260050100")).idProceso();
		gabinete.caido = true;
		Proceso p = nuevo("11001310303220260050100");
		Proceso editado = new Proceso(null, p.radicado(), p.idTipo(), null, p.idCliente(), p.rolCliente(),
				"Incumplimiento y perjuicios", p.cuantia(), p.claseCuantia(), p.tipoServicio(), EstadoProceso.ACTIVO,
				EtapaProceso.ADMISION, p.fechaRadicacion(), true, p.idResponsable());

		ProcesoVista vista = servicio.actualizar(id, editado);

		assertThat(vista.asunto()).isEqualTo("Incumplimiento y perjuicios");
		assertThat(vista.etapa()).isEqualTo("ADMISION");
	}

	@Test
	void noSeEliminaUnProcesoConProvidencias() {
		int id = servicio.crear(nuevo("11001310303220260050100")).idProceso();
		procesos.providencias.put(id, 2);

		assertThatThrownBy(() -> servicio.eliminar(id)).isInstanceOf(ConflictoException.class)
				.hasMessageContaining("ARCHIVADO");
	}

	@Test
	void eliminaUnProcesoSinProvidencias() {
		int id = servicio.crear(nuevo("11001310303220260050100")).idProceso();

		servicio.eliminar(id);

		assertThatThrownBy(() -> servicio.obtener(id)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void desglosaUnRadicadoIndicandoDespachoYSiYaExiste() {
		int id = servicio.crear(nuevo("11001310303220260050100")).idProceso();

		DesgloseRadicado registrado = servicio.desglosar("11001-31-03-032-2026-00501-00");
		DesgloseRadicado libre = servicio.desglosar("99999319999920260050100");

		assertThat(registrado.despacho()).isEqualTo(CIVIL_CTO_32);
		assertThat(registrado.idProcesoExistente()).isEqualTo(id);
		assertThat(libre.despacho()).isNull();
		assertThat(libre.idProcesoExistente()).isNull();
	}

	// ----------------------------------------------------------------- dobles

	static class CatalogoFijo implements CatalogoRepositorio {
		final List<Despacho> despachos = List.of(CIVIL_CTO_32, LABORAL_CTO_8);

		public List<Rama> ramas() { return List.of(new Rama("CIVIL", "Civil y comercial", "CGP")); }
		public List<TipoProceso> tipos(String rama) { return List.of(tipo()); }
		public List<Despacho> despachos(String rama) { return despachos; }
		public Optional<TipoProceso> tipo(short id) { return id == 1 ? Optional.of(tipo()) : Optional.empty(); }
		public Optional<Despacho> despacho(short id) {
			return despachos.stream().filter(d -> d.idDespacho() == id).findFirst();
		}
		public Optional<Despacho> despachoDeRadicado(Radicado r) {
			return despachos.stream().filter(d -> d.prefijo().equals(r.prefijoDespacho())).findFirst();
		}
		static TipoProceso tipo() { return new TipoProceso((short) 1, "CIVIL", "Verbal", (short) 20); }
	}

	static class GabineteFalso implements DirectorioGabinete {
		boolean caido;

		public Optional<Cliente> cliente(int id) {
			verificar();
			return id == 7 ? Optional.of(new Cliente(7, "Constructora Altamira SAS")) : Optional.empty();
		}

		public Optional<Abogado> abogado(long id) {
			verificar();
			if (id == ABOGADA) return Optional.of(new Abogado(id, "Catalina", "Gómez Restrepo", true));
			if (id == ABOGADO_INACTIVO) return Optional.of(new Abogado(id, "Pedro", "Rincón", false));
			return Optional.empty();
		}

		public List<ClienteResumen> buscarClientes(String texto) { return List.of(); }
		public Optional<Perito> perito(int id) { return Optional.empty(); }

		void verificar() {
			if (caido) throw new ServicioNoDisponibleException("gabinete caído");
		}
	}

	static class ProcesosEnMemoria implements ProcesoRepositorio {
		final Map<Integer, Proceso> datos = new HashMap<>();
		final Map<Integer, String> nombreCliente = new HashMap<>();
		final Map<Integer, Integer> providencias = new HashMap<>();
		int secuencia = 100;

		public List<ProcesoVista> listar(FiltroProcesos f) {
			return datos.keySet().stream().map(id -> buscar(id).orElseThrow()).toList();
		}

		public Optional<ProcesoVista> buscar(int id) {
			Proceso p = datos.get(id);
			if (p == null) return Optional.empty();
			return Optional.of(new ProcesoVista(id, p.radicado().numero(), p.radicado().legible(), p.idTipo(),
					"Verbal", "CIVIL", "Civil y comercial", "Juzgado", p.idDespachoActual(), "Juzgado",
					p.radicado().instancia(), p.idCliente(), p.rolCliente().name(), p.asunto(), p.cuantia(),
					p.claseCuantia().name(), p.tipoServicio().name(), p.estado().name(), p.etapa().name(),
					p.fechaRadicacion(), p.vigilado(), p.idResponsable()));
		}

		public Optional<Integer> idPorRadicado(Radicado r) {
			return datos.entrySet().stream().filter(e -> e.getValue().radicado().equals(r)).map(Map.Entry::getKey)
					.findFirst();
		}

		public int crear(Proceso p, String nombre) {
			int id = ++secuencia;
			datos.put(id, p.conId(id));
			nombreCliente.put(id, nombre);
			return id;
		}

		public void actualizar(Proceso p) { datos.put(p.idProceso(), p); }
		public int contarProvidencias(int id) { return providencias.getOrDefault(id, 0); }
		public void eliminar(int id) { datos.remove(id); }
	}

}
