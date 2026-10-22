package co.abogados.procesos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.procesos.dominio.modelo.CambioRegla;
import co.abogados.procesos.dominio.modelo.Despacho;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.modelo.Rama;
import co.abogados.procesos.dominio.modelo.ReglaDatos;
import co.abogados.procesos.dominio.modelo.ReglaTermino;
import co.abogados.procesos.dominio.modelo.ReglaVista;
import co.abogados.procesos.dominio.modelo.TipoProceso;
import co.abogados.procesos.dominio.puertos.CatalogoRepositorio;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ReglaTerminoRepositorio;

class ReglaServicioTest {

	static final long SOCIO = 79456123L;
	static final short VERBAL = 1, TUTELA = 32;

	ReglasEnMemoria reglas;
	ReglaServicio servicio;
	boolean gabineteCaido;

	@BeforeEach
	void preparar() {
		reglas = new ReglasEnMemoria();
		reglas.agregar(new ReglaDatos("AUTO", null, null, "\\badmite|admisorio", "DEMANDADO", "Contestar la demanda",
				null, "Término de contestación del tipo de proceso", 1, true), true);
		reglas.agregar(new ReglaDatos("AUTO", "CIVIL", null, ".", null, "Recurso de reposición contra el auto (si procede)",
				3, "CGP art. 318", 2, true), false);
		servicio = new ReglaServicio(reglas, new Catalogo(reglas), new Gabinete());
	}

	@Test
	void validarFirmaLaReglaConElSocioYQuedaEnElHistorial() {
		ReglaVista r = servicio.validar(2, SOCIO);

		assertThat(r.validada()).isTrue();
		assertThat(r.validadaPor()).isEqualTo(SOCIO);
		assertThat(r.nombreValidador()).isEqualTo("Carlos Gómez");
		assertThat(servicio.historial(2)).extracting(CambioRegla::accion, CambioRegla::nombre)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("VALIDADA", "Carlos Gómez"));
		assertThatThrownBy(() -> servicio.validar(2, SOCIO)).isInstanceOf(ConflictoException.class);
	}

	@Test
	void cambiarLoQueDiceLaReglaLaDejaSinValidarYExplicaElCambio() {
		ReglaDatos antes = reglas.buscar(1).orElseThrow().datos();
		ReglaDatos cambio = new ReglaDatos(antes.tipoProvidencia(), antes.codigoRama(), antes.idTipo(), antes.patron(),
				antes.lado(), antes.descripcion(), 15, "CGP art. 369", 1, true);

		ReglaVista r = servicio.actualizar(1, cambio, SOCIO);

		assertThat(r.validada()).isFalse();
		assertThat(r.dias()).isEqualTo(15);
		assertThat(servicio.historial(1).get(0).detalle())
				.contains("días: (vacío) → 15").contains("norma:").endsWith("Queda sin validar.");
	}

	@Test
	void desactivarNoQuitaLaValidacionYSoloRegistraElCambioDeEstado() {
		ReglaDatos d = reglas.buscar(1).orElseThrow().datos();
		ReglaDatos inactiva = new ReglaDatos(d.tipoProvidencia(), d.codigoRama(), d.idTipo(), d.patron(), d.lado(),
				d.descripcion(), d.dias(), d.norma(), d.prioridad(), false);

		ReglaVista r = servicio.actualizar(1, inactiva, SOCIO);

		assertThat(r.activa()).isFalse();
		assertThat(r.validada()).isTrue();
		assertThat(servicio.historial(1)).extracting(CambioRegla::accion).containsExactly("DESACTIVADA");
		assertThat(reglas.reglas()).extracting(ReglaTermino::idRegla).containsExactly(2);   // ya no sugiere
		assertThatThrownBy(() -> servicio.validar(1, SOCIO)).isInstanceOf(ConflictoException.class);
	}

	@Test
	void guardarSinCambiosNoTocaNada() {
		ReglaVista antes = reglas.buscar(1).orElseThrow();
		assertThat(servicio.actualizar(1, antes.datos(), SOCIO)).isEqualTo(antes);
		assertThat(reglas.historial(1)).isEmpty();
	}

	@Test
	void unaReglaNuevaNaceSinValidarYTomaLaRamaDelTipoDeProceso() {
		ReglaVista r = servicio.crear(new ReglaDatos("SENTENCIA", null, TUTELA, ".", null, "Impugnar el fallo",
				3, "Decreto 2591 de 1991 art. 31", 1, true), SOCIO);

		assertThat(r.idRegla()).isEqualTo(3);
		assertThat(r.validada()).isFalse();
		assertThat(r.codigoRama()).isEqualTo("CONSTITUCIONAL");
		assertThat(servicio.historial(3)).extracting(CambioRegla::accion).containsExactly("CREADA");
	}

	@Test
	void rechazaReglasMalEscritas() {
		assertThatThrownBy(() -> servicio.crear(datos("AUTO", null, null, "admite(", 3), SOCIO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("expresión regular");
		assertThatThrownBy(() -> servicio.crear(datos("RESOLUCION", null, null, ".", 3), SOCIO))
				.hasMessageContaining("AUTO o a una SENTENCIA");
		assertThatThrownBy(() -> servicio.crear(datos("AUTO", "LABORAL", VERBAL, ".", 3), SOCIO))
				.hasMessageContaining("no es de la rama LABORAL");
		assertThatThrownBy(() -> servicio.crear(datos("AUTO", "MINERA", null, ".", 3), SOCIO))
				.hasMessageContaining("No existe la rama");
		assertThatThrownBy(() -> servicio.crear(datos("AUTO", null, (short) 99, ".", 3), SOCIO))
				.hasMessageContaining("No existe el tipo");
		assertThatThrownBy(() -> servicio.crear(datos("AUTO", null, null, ".", 400), SOCIO))
				.hasMessageContaining("1 a 365");
		assertThatThrownBy(() -> servicio.crear(new ReglaDatos("AUTO", null, null, ".", "TERCERO", "x", 3, "y", 1, true), SOCIO))
				.hasMessageContaining("lado");
		assertThatThrownBy(() -> servicio.crear(new ReglaDatos("AUTO", null, null, ".", null, "x", 3, "y", 3, true), SOCIO))
				.hasMessageContaining("prioridad");
		assertThatThrownBy(() -> servicio.crear(new ReglaDatos("AUTO", null, null, ".", null, " ", 3, "y", 1, true), SOCIO))
				.hasMessageContaining("descripción");
		assertThat(reglas.listar()).hasSize(2);
	}

	@Test
	void cambiarLosDiasDeContestacionDesvalidaLasReglasQueLosUsan() {
		int afectadas = servicio.cambiarContestacion(VERBAL, 15, SOCIO);

		assertThat(afectadas).isEqualTo(1);
		assertThat(reglas.contestacion.get(VERBAL)).isEqualTo(15);
		assertThat(reglas.buscar(1).orElseThrow().validada()).isFalse();
		assertThat(reglas.buscar(2).orElseThrow().validada()).isFalse();      // tiene días propios: no se toca
		assertThat(reglas.historial(2)).isEmpty();
		assertThat(servicio.historial(1).get(0).detalle()).startsWith("Días de contestación de «Verbal»: 20 → 15");
		assertThat(servicio.cambiarContestacion(VERBAL, 15, SOCIO)).isZero();  // igual que antes: nada
		assertThatThrownBy(() -> servicio.cambiarContestacion(VERBAL, 0, SOCIO)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> servicio.cambiarContestacion((short) 99, 5, SOCIO)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void probarUnPatronUsaElTextoNormalizadoComoElSugeridor() {
		assertThat(servicio.probar("\\badmite", "AUTO QUE ADMITE LA DEMANDA").coincide()).isTrue();
		assertThat(servicio.probar("\\badmite", "Inadmite demanda").coincide()).isFalse();
		assertThat(servicio.probar("excepciones", "Traslado de excepciones (art. 370)").claseNormalizada())
				.isEqualTo("traslado de excepciones art 370");
	}

	@Test
	void siGabineteNoRespondeLaListaSaleSinNombres() {
		servicio.validar(2, SOCIO);
		gabineteCaido = true;
		assertThat(servicio.listar()).filteredOn(ReglaVista::validada)
				.extracting(ReglaVista::validadaPor, ReglaVista::nombreValidador)
				.contains(org.assertj.core.groups.Tuple.tuple(SOCIO, null));
	}

	@Test
	void unaReglaQueNoExisteDa404() {
		assertThatThrownBy(() -> servicio.historial(9)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.validar(9, SOCIO)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	private static ReglaDatos datos(String providencia, String rama, Short tipo, String patron, int dias) {
		return new ReglaDatos(providencia, rama, tipo, patron, null, "Término de prueba", dias, "Norma", 1, true);
	}

	// ------------------------------------------------------------------ dobles

	class Gabinete implements DirectorioGabinete {
		@Override
		public Optional<Abogado> abogado(long idPersona) {
			if (gabineteCaido) {
				throw new ServicioNoDisponibleException("gabinete no responde");
			}
			return idPersona == SOCIO ? Optional.of(new Abogado(SOCIO, "Carlos", "Gómez", true)) : Optional.empty();
		}

		@Override
		public Optional<Cliente> cliente(int idCliente) {
			return Optional.empty();
		}

		@Override
		public Optional<Perito> perito(int idPerito) {
			return Optional.empty();
		}

		@Override
		public List<ClienteResumen> buscarClientes(String texto) {
			return List.of();
		}
	}

	/** Los días de contestación salen del mismo doble de reglas, como en la base (tipos_proceso). */
	record Catalogo(ReglasEnMemoria reglas) implements CatalogoRepositorio {
		@Override
		public List<Rama> ramas() {
			return List.of(new Rama("CIVIL", "Civil", "CGP"), new Rama("LABORAL", "Laboral", "CPTSS"),
					new Rama("CONSTITUCIONAL", "Constitucional", "Decreto 2591"));
		}

		@Override
		public List<TipoProceso> tipos(String rama) {
			Integer verbal = reglas.contestacion.get(VERBAL);
			return List.of(new TipoProceso(VERBAL, "CIVIL", "Verbal", verbal == null ? null : verbal.shortValue()),
					new TipoProceso(TUTELA, "CONSTITUCIONAL", "Tutela", null));
		}

		@Override
		public List<Despacho> despachos(String rama) {
			return List.of();
		}

		@Override
		public Optional<TipoProceso> tipo(short idTipo) {
			return tipos(null).stream().filter(t -> t.idTipo() == idTipo).findFirst();
		}

		@Override
		public Optional<Despacho> despacho(short idDespacho) {
			return Optional.empty();
		}

		@Override
		public Optional<Despacho> despachoDeRadicado(Radicado radicado) {
			return Optional.empty();
		}
	}

	static class ReglasEnMemoria implements ReglaTerminoRepositorio {
		final Map<Integer, ReglaVista> filas = new LinkedHashMap<>();
		final List<Object[]> cambios = new ArrayList<>();
		final Map<Short, Integer> contestacion = new java.util.HashMap<>(Map.of(VERBAL, 20));

		void agregar(ReglaDatos d, boolean validada) {
			int id = filas.size() + 1;
			filas.put(id, fila(id, d, validada, validada ? SOCIO : null, null));
		}

		private static ReglaVista fila(int id, ReglaDatos d, boolean validada, Long por, Long modificadaPor) {
			return new ReglaVista(id, d.tipoProvidencia(), d.codigoRama(), d.idTipo(), d.patron(), d.lado(),
					d.descripcion(), d.dias(), d.norma(), d.prioridad(), d.activa(), validada, por, null,
					validada ? OffsetDateTime.now() : null, modificadaPor, null);
		}

		@Override
		public List<ReglaTermino> reglas() {
			return filas.values().stream().filter(ReglaVista::activa)
					.map(r -> new ReglaTermino(r.idRegla(), r.tipoProvidencia(), r.codigoRama(), r.idTipo(), r.patron(),
							r.lado(), r.descripcion(), r.dias(), r.norma(), r.prioridad(), r.validada()))
					.toList();
		}

		@Override
		public Optional<Integer> diasContestacion(short idTipo) {
			return Optional.ofNullable(contestacion.get(idTipo));
		}

		@Override
		public List<ReglaVista> listar() {
			return List.copyOf(filas.values());
		}

		@Override
		public Optional<ReglaVista> buscar(int idRegla) {
			return Optional.ofNullable(filas.get(idRegla));
		}

		@Override
		public int crear(ReglaDatos datos, long usuario) {
			int id = filas.size() + 1;
			filas.put(id, fila(id, datos, false, null, usuario));
			return id;
		}

		@Override
		public void actualizar(int idRegla, ReglaDatos datos, long usuario, boolean desvalidar) {
			ReglaVista r = filas.get(idRegla);
			boolean validada = r.validada() && !desvalidar;
			filas.put(idRegla, fila(idRegla, datos, validada, validada ? r.validadaPor() : null, usuario));
		}

		@Override
		public void validar(int idRegla, long usuario) {
			filas.put(idRegla, fila(idRegla, filas.get(idRegla).datos(), true, usuario, usuario));
		}

		@Override
		public void desvalidar(int idRegla, long usuario) {
			filas.put(idRegla, fila(idRegla, filas.get(idRegla).datos(), false, null, usuario));
		}

		@Override
		public void registrar(int idRegla, long usuario, String accion, String detalle) {
			cambios.add(new Object[] { idRegla, usuario, accion, detalle });
		}

		@Override
		public List<CambioRegla> historial(int idRegla) {
			List<CambioRegla> h = new ArrayList<>();
			for (Object[] c : cambios) {
				if ((int) c[0] == idRegla) {
					h.add(0, new CambioRegla(OffsetDateTime.now(), (long) c[1], null, (String) c[2], (String) c[3]));
				}
			}
			return h;
		}

		@Override
		public void cambiarContestacion(short idTipo, Integer dias) {
			contestacion.put(idTipo, dias);
		}
	}

}
