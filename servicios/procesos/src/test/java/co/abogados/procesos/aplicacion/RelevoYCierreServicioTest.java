package co.abogados.procesos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.procesos.dominio.modelo.Cierre;
import co.abogados.procesos.dominio.modelo.EstadoProceso;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Relevo;
import co.abogados.procesos.dominio.puertos.CierreRepositorio;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.RelevoRepositorio;

/** Relevo del abogado a cargo y cierre de un proceso: las reglas, sin base de datos. */
class RelevoYCierreServicioTest {

	static final long CATALINA = 52345678L;
	static final long PEDRO = 79123456L;
	static final long INACTIVA = 1010101010L;
	static final long SOCIO = 19000111L;

	ProcesoRepositorio procesos = mock(ProcesoRepositorio.class);
	RelevoRepositorio relevos = mock(RelevoRepositorio.class);
	CierreRepositorio cierres = mock(CierreRepositorio.class);
	DirectorioGabinete gabinete = mock(DirectorioGabinete.class);
	List<Relevo.Reasignado> eventos = new ArrayList<>();
	Clock reloj = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneId.of("America/Bogota"));
	RelevoServicio relevo;
	CierreServicio cierre;

	static ProcesoVista proceso(String estado, String etapa, Long responsable, String etapaAntes) {
		return new ProcesoVista(34, "11001310303220260050100", "11001-31-03-032-2026-00501-00", (short) 1, "Verbal",
				"CIVIL", "Civil y comercial", "Juzgado 32", null, "Juzgado 32", 0, 7, "DEMANDANTE",
				"Incumplimiento de contrato", BigDecimal.ONE, "MAYOR", "REPRESENTACION", estado, etapa,
				LocalDate.of(2026, 3, 1), true, responsable, null, null, null, etapaAntes);
	}

	@BeforeEach
	void preparar() {
		given(procesos.buscar(34)).willReturn(Optional.of(proceso("ACTIVO", "PRUEBAS", CATALINA, null)));
		given(gabinete.abogado(CATALINA)).willReturn(Optional.of(new DirectorioGabinete.Abogado(CATALINA, "Catalina", "Gómez", true)));
		given(gabinete.abogado(PEDRO)).willReturn(Optional.of(new DirectorioGabinete.Abogado(PEDRO, "Pedro", "Rincón", true)));
		given(gabinete.abogado(INACTIVA)).willReturn(Optional.of(new DirectorioGabinete.Abogado(INACTIVA, "Diana", "Ochoa", false)));
		relevo = new RelevoServicio(relevos, procesos, gabinete, eventos::add, reloj);
		cierre = new CierreServicio(cierres, procesos, reloj);
	}

	static Relevo.Solicitud a(long nuevo) {
		return new Relevo.Solicitud(nuevo, Relevo.Motivo.SUCESION, " Licencia de maternidad ", false, true);
	}

	// ----------------------------------------------------------------- relevo

	@Test
	void elNuevoAbogadoRecibeElProcesoYSeAvisaALosDos() {
		given(relevos.relevar(34, CATALINA, a(PEDRO), SOCIO)).willReturn(new Relevo.Resultado(CATALINA, PEDRO, 3, 1));

		Relevo.Resultado r = relevo.relevar(34, a(PEDRO), SOCIO);

		assertThat(r.terminosTrasladados()).isEqualTo(3);
		assertThat(r.audienciasTrasladadas()).isEqualTo(1);
		assertThat(eventos).singleElement().satisfies(e -> {
			assertThat(e.idProceso()).isEqualTo(34);
			assertThat(e.radicado()).isEqualTo("11001310303220260050100");
			assertThat(e.idAnterior()).isEqualTo(CATALINA);
			assertThat(e.idNuevo()).isEqualTo(PEDRO);
			assertThat(e.motivo()).isEqualTo("SUCESION");
			assertThat(e.terminosTrasladados()).isEqualTo(3);
			assertThat(e.ocurrido()).isEqualTo(Instant.parse("2026-11-11T15:00:00Z"));
		});
	}

	@Test
	void noSeRelevaAlMismoAbogadoNiAUnoInactivoODesconocido() {
		assertThatThrownBy(() -> relevo.relevar(34, a(CATALINA), SOCIO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está a cargo");
		assertThatThrownBy(() -> relevo.relevar(34, a(INACTIVA), SOCIO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("inactivo");
		assertThatThrownBy(() -> relevo.relevar(34, a(999L), SOCIO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("No existe un abogado");
		assertThatThrownBy(() -> relevo.relevar(99, a(PEDRO), SOCIO)).isInstanceOf(RecursoNoEncontradoException.class);
		verify(relevos, never()).relevar(anyInt(), any(), any(), anyLong());
		assertThat(eventos).isEmpty();
	}

	@Test
	void unProcesoCerradoNoCambiaDeAbogado() {
		given(procesos.buscar(34)).willReturn(Optional.of(proceso("TERMINADO", "CERRADO", CATALINA, "PRUEBAS")));

		assertThatThrownBy(() -> relevo.relevar(34, a(PEDRO), SOCIO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cerrado");
	}

	@Test
	void unProcesoSinResponsableSoloRecibeAlNuevo() {
		given(procesos.buscar(34)).willReturn(Optional.of(proceso("ACTIVO", "PRUEBAS", null, null)));
		given(relevos.relevar(34, null, a(PEDRO), SOCIO)).willReturn(new Relevo.Resultado(null, PEDRO, 0, 0));

		assertThat(relevo.relevar(34, a(PEDRO), SOCIO).idAnterior()).isNull();
		assertThat(eventos).singleElement().satisfies(e -> assertThat(e.idAnterior()).isNull());
	}

	@Test
	void laSolicitudExigeAbogadoYMotivoYLimpiaLaNota() {
		assertThat(a(PEDRO).nota()).isEqualTo("Licencia de maternidad");
		assertThat(new Relevo.Solicitud(PEDRO, Relevo.Motivo.RENUNCIA, "  ", true, false).nota()).isNull();
		assertThatThrownBy(() -> new Relevo.Solicitud(0, Relevo.Motivo.RENUNCIA, null, false, true))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Elija el abogado");
		assertThatThrownBy(() -> new Relevo.Solicitud(PEDRO, null, null, false, true))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("motivo");
		assertThatThrownBy(() -> new Relevo.Solicitud(PEDRO, Relevo.Motivo.RENUNCIA, "x".repeat(301), false, true))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("300");
	}

	@Test
	void elHistorialLlevaLosNombresYSeMuestraAunqueGabineteNoResponda() {
		Relevo.Periodo vigente = new Relevo.Periodo(2, PEDRO, "RESPONSABLE", LocalDate.of(2026, 11, 11), null, null, null,
				SOCIO, null, null);
		Relevo.Periodo terminado = new Relevo.Periodo(1, CATALINA, "RESPONSABLE", LocalDate.of(2026, 3, 1),
				LocalDate.of(2026, 11, 11), "SUCESION", "Licencia", null, SOCIO, null);
		given(relevos.historial(34)).willReturn(List.of(vigente, terminado));

		assertThat(relevo.historial(34)).extracting(Relevo.Periodo::nombre).containsExactly("Pedro Rincón", "Catalina Gómez");

		given(gabinete.abogado(PEDRO)).willThrow(new ServicioNoDisponibleException("gabinete no responde"));
		given(gabinete.abogado(CATALINA)).willReturn(Optional.empty());
		assertThat(relevo.historial(34)).extracting(Relevo.Periodo::nombre).containsExactly(null, null);
		assertThat(relevo.historial(34)).extracting(Relevo.Periodo::motivo).containsExactly(null, "SUCESION");
	}

	// ----------------------------------------------------------------- cierre

	@Test
	void cierraConMotivoYCuentaLoQueQuedaSinEfecto() {
		Cierre esperado = new Cierre(Cierre.Motivo.SENTENCIA_ANTICIPADA, LocalDate.of(2026, 11, 11), "Prescripción probada");
		given(cierres.cerrar(34, esperado, CATALINA)).willReturn(new Cierre.Efectos(2, 1, true));

		Cierre.Resultado r = cierre.cerrar(34, Cierre.Motivo.SENTENCIA_ANTICIPADA, null, " Prescripción probada ", CATALINA);

		assertThat(r.efectos().terminosPendientes()).isEqualTo(2);
		assertThat(r.efectos().audienciasFuturas()).isEqualTo(1);
		assertThat(r.efectos().seApagoLaVigilancia()).isTrue();
		verify(cierres).cerrar(34, esperado, CATALINA);            // sin fecha: la de hoy en Bogotá
	}

	@Test
	void elMotivoDecideSiQuedaTerminadoOArchivado() {
		LocalDate hoy = LocalDate.of(2026, 11, 11);
		assertThat(new Cierre(Cierre.Motivo.ARCHIVO, hoy, null).estado()).isEqualTo(EstadoProceso.ARCHIVADO);
		assertThat(new Cierre(Cierre.Motivo.DESISTIMIENTO, hoy, null).estado()).isEqualTo(EstadoProceso.TERMINADO);
		assertThat(new Cierre(Cierre.Motivo.LIQUIDACION, hoy, null).estado()).isEqualTo(EstadoProceso.TERMINADO);
		assertThatThrownBy(() -> new Cierre(Cierre.Motivo.OTRO, hoy, " "))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("explique");
		assertThatThrownBy(() -> new Cierre(null, hoy, null)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> new Cierre(Cierre.Motivo.PAGO, null, null)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> new Cierre(Cierre.Motivo.PAGO, hoy, "x".repeat(301))).isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void noSeCierraDosVecesNiConFechaFuturaOAnteriorALaRadicacion() {
		assertThatThrownBy(() -> cierre.cerrar(34, Cierre.Motivo.SENTENCIA, LocalDate.of(2026, 11, 12), null, CATALINA))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("futura");
		assertThatThrownBy(() -> cierre.cerrar(34, Cierre.Motivo.SENTENCIA, LocalDate.of(2026, 2, 28), null, CATALINA))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("radicación");
		given(procesos.buscar(34)).willReturn(Optional.of(proceso("ARCHIVADO", "CERRADO", CATALINA, null)));
		assertThatThrownBy(() -> cierre.cerrar(34, Cierre.Motivo.SENTENCIA, null, null, CATALINA))
				.isInstanceOf(ConflictoException.class).hasMessageContaining("ya está cerrado");
		verify(cierres, never()).cerrar(anyInt(), any(), anyLong());
	}

	@Test
	void alReabrirVuelveALaEtapaQueTeniaOALaQueSeIndique() {
		given(procesos.buscar(34)).willReturn(Optional.of(proceso("TERMINADO", "CERRADO", CATALINA, "PRUEBAS")));

		cierre.reabrir(34, null, SOCIO);
		verify(cierres).reabrir(34, EtapaProceso.PRUEBAS);

		cierre.reabrir(34, EtapaProceso.RECURSO, SOCIO);
		verify(cierres).reabrir(34, EtapaProceso.RECURSO);

		assertThatThrownBy(() -> cierre.reabrir(34, EtapaProceso.CERRADO, SOCIO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Cerrado");
	}

	@Test
	void noSeReabreUnoAbiertoYSiNoSeSabeLaEtapaHayQueDecirla() {
		assertThatThrownBy(() -> cierre.reabrir(34, null, SOCIO))
				.isInstanceOf(ConflictoException.class).hasMessageContaining("no está cerrado");

		// cerrado antes de que se guardara la etapa anterior
		given(procesos.buscar(34)).willReturn(Optional.of(proceso("TERMINADO", "CERRADO", CATALINA, null)));
		assertThatThrownBy(() -> cierre.reabrir(34, null, SOCIO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Indique en qué etapa");
		assertThatThrownBy(() -> cierre.reabrir(99, null, SOCIO)).isInstanceOf(RecursoNoEncontradoException.class);
	}

}
