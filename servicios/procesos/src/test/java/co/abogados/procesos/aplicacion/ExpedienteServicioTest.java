package co.abogados.procesos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
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
import co.abogados.procesos.dominio.modelo.Actuacion;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.EventoExpediente;
import co.abogados.procesos.dominio.modelo.FiltroTerminos;
import co.abogados.procesos.dominio.modelo.Instancia;
import co.abogados.procesos.dominio.modelo.Parte;
import co.abogados.procesos.dominio.modelo.ParteRegistrada;
import co.abogados.procesos.dominio.modelo.RolParte;
import co.abogados.procesos.dominio.modelo.Termino;
import co.abogados.procesos.dominio.modelo.TerminoVista;
import co.abogados.procesos.dominio.modelo.TipoActuacion;
import co.abogados.procesos.dominio.puertos.ActuacionRepositorio;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ExpedienteRepositorio;
import co.abogados.procesos.dominio.puertos.ParteRepositorio;
import co.abogados.procesos.dominio.puertos.TerminoRepositorio;

/**
 * Expediente (F6): partes con aviso de conflicto de intereses, actuaciones que cumplen términos y hacen avanzar
 * la etapa. El proceso de prueba es el 34 (cliente 30, etapa ADMISION, activo); hoy es 16/10/2026.
 */
class ExpedienteServicioTest {

	PartesEnMemoria partes;
	ActuacionesEnMemoria actuaciones;
	ExpedienteEnMemoria expediente;
	TerminosFijos terminos;
	ClientesFalsos gabinete;
	ExpedienteServicio servicio;

	@BeforeEach
	void preparar() {
		partes = new PartesEnMemoria();
		partes.crear(new Parte(null, 34, RolParte.DEMANDANTE, null, null, "Cliente Prueba F1", true, null));
		actuaciones = new ActuacionesEnMemoria();
		expediente = new ExpedienteEnMemoria();
		terminos = new TerminosFijos();
		gabinete = new ClientesFalsos();
		servicio = new ExpedienteServicio(new ProvidenciaServicioTest.ProcesoUnico(), partes, actuaciones, expediente,
				terminos, gabinete, ProvidenciaServicioTest.HOY);
	}

	static Parte contraparte(String tipo, String numero, String nombre) {
		return new Parte(null, 34, RolParte.DEMANDADO, tipo, numero, nombre, false, "Dr. Ruiz");
	}

	// ----------------------------------------------------------------- partes y conflicto de intereses

	@Test
	void unaContraparteConElDocumentoDeUnClienteDeLaFirmaGeneraUnAviso() {
		ParteRegistrada r = servicio.agregarParte(34, contraparte("NIT", "900123456", "Ferretería El Tornillo"));

		assertThat(r.conflictosVerificados()).isTrue();
		assertThat(r.conflictos()).singleElement().satisfies(c -> {
			assertThat(c.idCliente()).isEqualTo(12);
			assertThat(c.motivo()).contains("mismo documento");
		});
		assertThat(r.parte().esCliente()).isFalse();
		assertThat(servicio.partes(34)).hasSize(2);
	}

	@Test
	void elNombreSeComparaSinTildesNiPuntosPeroNoPorCoincidenciaParcial() {
		ParteRegistrada igual = servicio.agregarParte(34, contraparte(null, null, "CONSTRUCTORA  altamira s.a.s"));
		assertThat(igual.conflictos()).singleElement().satisfies(c -> assertThat(c.motivo()).contains("mismo nombre"));

		ParteRegistrada parecida = servicio.agregarParte(34, contraparte(null, null, "Altamira"));
		assertThat(parecida.conflictos()).isEmpty();
		assertThat(ExpedienteServicio.normalizar("José  Gómez S.A.S.")).isEqualTo("jose gomez sas");
	}

	@Test
	void avisaSiLaContraparteEsElMismoClienteDelProceso() {
		ParteRegistrada r = servicio.agregarParte(34, contraparte("CC", "1020304050", "Cliente Prueba F1"));
		assertThat(r.conflictos()).singleElement().satisfies(c -> assertThat(c.motivo()).contains("mismo cliente"));
	}

	@Test
	void siGabineteNoRespondeLaParteSeGuardaPeroSinVerificar() {
		gabinete.caido = true;

		ParteRegistrada r = servicio.agregarParte(34, contraparte("NIT", "900123456", "Ferretería El Tornillo"));

		assertThat(r.conflictosVerificados()).isFalse();
		assertThat(r.parte().idParte()).isNotNull();
	}

	@Test
	void elClienteNoSeEditaNiSeQuitaDesdeElExpediente() {
		int idCliente = servicio.partes(34).get(0).idParte();
		assertThatThrownBy(() -> servicio.eliminarParte(34, idCliente)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> servicio.editarParte(34, idCliente, contraparte(null, null, "Otro")))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void unaParteDeOtroProcesoNoSeEncuentra() {
		int id = partes.crear(new Parte(null, 99, RolParte.DEMANDADO, null, null, "Ajeno", false, null));
		assertThatThrownBy(() -> servicio.eliminarParte(34, id)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void validacionesDeLaParte() {
		assertThatThrownBy(() -> contraparte("CC", null, "Sin número")).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> contraparte("CC", "12.345.678", "Con puntos")).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> contraparte("XX", "12345678", "Tipo raro")).isInstanceOf(ReglaNegocioException.class);
		assertThat(contraparte("PAS", "AB12345", "  Ana   María  ").nombre()).isEqualTo("Ana María");
	}

	// ----------------------------------------------------------------- actuaciones

	static Actuacion actuacion(TipoActuacion tipo, LocalDate fecha, Integer idProvidencia) {
		return new Actuacion(null, 34, fecha, tipo, "Radicada por correo al juzgado", 52345678L, idProvidencia, null, null);
	}

	@Test
	void laContestacionCumpleSuTerminoYHaceAvanzarElProceso() {
		Actuacion a = servicio.registrarActuacion(34, actuacion(TipoActuacion.CONTESTACION, LocalDate.of(2026, 10, 15), null),
				7, 79123456L);

		assertThat(a.registradoPor()).isEqualTo(79123456L);
		assertThat(terminos.cumplidos).containsExactly(7);
		assertThat(expediente.etapas).containsExactly(EtapaProceso.CONTESTACION);
	}

	@Test
	void registrarTardeUnaActuacionDeUnaEtapaAnteriorNoHaceRetrocederElProceso() {
		servicio.registrarActuacion(34, actuacion(TipoActuacion.DEMANDA, LocalDate.of(2026, 9, 30), null), null, null);
		assertThat(expediente.etapas).isEmpty();     // el proceso ya está en ADMISION
	}

	@Test
	void unaAudienciaProgramadaNoCumpleTerminosNiCambiaLaEtapa() {
		servicio.registrarActuacion(34, actuacion(TipoActuacion.AUDIENCIA, LocalDate.of(2026, 11, 3), null), null, null);
		assertThat(actuaciones.datos).hasSize(1);
		assertThat(expediente.etapas).isEmpty();

		assertThatThrownBy(() -> servicio.registrarActuacion(34,
				actuacion(TipoActuacion.MEMORIAL, LocalDate.of(2026, 11, 3), null), 7, null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("programada");
	}

	@Test
	void unRecursoExigeUnaProvidenciaDelMismoProceso() {
		assertThatThrownBy(() -> actuacion(TipoActuacion.RECURSO, LocalDate.of(2026, 10, 15), null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("providencia");
		assertThatThrownBy(() -> servicio.registrarActuacion(34,
				actuacion(TipoActuacion.RECURSO, LocalDate.of(2026, 10, 15), 500), null, null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es de este proceso");

		servicio.registrarActuacion(34, actuacion(TipoActuacion.RECURSO, LocalDate.of(2026, 10, 15), 5), null, null);
		assertThat(expediente.etapas).containsExactly(EtapaProceso.RECURSO);
	}

	@Test
	void unTerminoYaCumplidoODeOtroProcesoNoSePuedeCumplirOtraVez() {
		assertThatThrownBy(() -> servicio.registrarActuacion(34,
				actuacion(TipoActuacion.MEMORIAL, LocalDate.of(2026, 10, 15), null), 8, null))
				.isInstanceOf(ConflictoException.class);
		assertThatThrownBy(() -> servicio.registrarActuacion(34,
				actuacion(TipoActuacion.MEMORIAL, LocalDate.of(2026, 10, 15), null), 9, null))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(actuaciones.datos).isEmpty();      // nada se guardó
	}

	@Test
	void elDocumentoDebeSerUnEnlaceWeb() {
		assertThatThrownBy(() -> new Actuacion(null, 34, LocalDate.of(2026, 10, 15), TipoActuacion.PODER, "Poder",
				null, null, "C:\\poderes\\poder.pdf", null)).isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void laEtapaSoloAvanzaSiElProcesoEstaActivo() {
		assertThat(EtapaProceso.ADMISION.avanzaA(EtapaProceso.CONTESTACION, "ACTIVO")).isTrue();
		assertThat(EtapaProceso.ADMISION.avanzaA(EtapaProceso.CONTESTACION, "SUSPENDIDO")).isFalse();
		assertThat(EtapaProceso.SENTENCIA.avanzaA(EtapaProceso.CONTESTACION, "ACTIVO")).isFalse();
		assertThat(EtapaProceso.CERRADO.avanzaA(EtapaProceso.RECURSO, "ACTIVO")).isFalse();
	}

	// ----------------------------------------------------------------- dobles

	static class PartesEnMemoria implements ParteRepositorio {
		final Map<Integer, Parte> datos = new HashMap<>();
		int secuencia;

		public List<Parte> deProceso(int id) {
			return datos.values().stream().filter(p -> p.idProceso() == id)
					.sorted((a, b) -> Boolean.compare(b.esCliente(), a.esCliente())).toList();
		}
		public Optional<Parte> buscar(int id) { return Optional.ofNullable(datos.get(id)); }
		public int crear(Parte p) {
			int id = ++secuencia;
			datos.put(id, new Parte(id, p.idProceso(), p.rol(), p.tipoDocumento(), p.numeroDocumento(), p.nombre(),
					p.esCliente(), p.apoderado()));
			return id;
		}
		public void actualizar(Parte p) { datos.put(p.idParte(), p); }
		public void eliminar(int id) { datos.remove(id); }
	}

	static class ActuacionesEnMemoria implements ActuacionRepositorio {
		final Map<Integer, Actuacion> datos = new HashMap<>();

		public List<Actuacion> deProceso(int id) { return datos.values().stream().filter(a -> a.idProceso() == id).toList(); }
		public Optional<Actuacion> buscar(int id) { return Optional.ofNullable(datos.get(id)); }
		public int crear(Actuacion a) {
			int id = datos.size() + 1;
			datos.put(id, new Actuacion(id, a.idProceso(), a.fecha(), a.tipo(), a.descripcion(), a.idPersona(),
					a.idProvidencia(), a.documentoUrl(), a.registradoPor()));
			return id;
		}
		public void eliminar(int id) { datos.remove(id); }
	}

	static class ExpedienteEnMemoria implements ExpedienteRepositorio {
		final List<EtapaProceso> etapas = new ArrayList<>();

		public List<EventoExpediente> lineaDeTiempo(int id) { return List.of(); }
		public List<Instancia> otrasInstancias(int id) { return List.of(); }
		public void avanzarEtapa(int id, EtapaProceso etapa) { etapas.add(etapa); }
	}

	/** Término 7 pendiente y 8 cumplido del proceso 34; término 9 de otro proceso; providencia 5 es del proceso 34. */
	static class TerminosFijos implements TerminoRepositorio {
		final List<Integer> cumplidos = new ArrayList<>();

		TerminoVista vista(int id, int proceso, String estado) {
			return new TerminoVista(id, proceso, "", "", "", "", null, "Contestar la demanda", LocalDate.of(2026, 10, 1),
					10, LocalDate.of(2026, 10, 16), estado, 79123456L, 0, "POR_VENCER");
		}
		public List<TerminoVista> listar(FiltroTerminos f) { return List.of(); }
		public Optional<TerminoVista> buscar(int id) {
			return switch (id) {
				case 7 -> Optional.of(vista(7, 34, "PENDIENTE"));
				case 8 -> Optional.of(vista(8, 34, "CUMPLIDO"));
				case 9 -> Optional.of(vista(9, 99, "PENDIENTE"));
				default -> Optional.empty();
			};
		}
		public int crear(Termino t) { return 0; }
		public void marcarCumplido(int id) { cumplidos.add(id); }
		public void eliminar(int id) { }
		public boolean providenciaDelProceso(int idProvidencia, int idProceso) { return idProvidencia == 5 && idProceso == 34; }
		public List<TerminoVista> paraAvisar(int a, int b) { return List.of(); }
	}

	/** Clientes de la firma: 12 Ferretería El Tornillo (NIT 900123456), 7 Constructora Altamira SAS y el 30 del proceso. */
	static class ClientesFalsos implements DirectorioGabinete {
		boolean caido;
		final List<ClienteResumen> clientes = List.of(
				new ClienteResumen(12, "Ferretería El Tornillo SAS", "900123456"),
				new ClienteResumen(7, "Constructora Altamira SAS", "800765432"),
				new ClienteResumen(30, "Cliente Prueba F1", "1020304050"));

		public Optional<Cliente> cliente(int id) { return Optional.empty(); }
		public Optional<Abogado> abogado(long id) { return Optional.empty(); }
		public List<ClienteResumen> buscarClientes(String texto) {
			if (caido) throw new ServicioNoDisponibleException("gabinete caído");
			if (texto == null) return clientes;
			String t = texto.toLowerCase();
			return clientes.stream().filter(c -> c.nombre().toLowerCase().contains(t) || c.numeroDocumento().contains(texto)).toList();
		}
	}

}
