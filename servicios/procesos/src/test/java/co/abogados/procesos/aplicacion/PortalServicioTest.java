package co.abogados.procesos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.AudienciaVista;
import co.abogados.procesos.dominio.modelo.EventoExpediente;
import co.abogados.procesos.dominio.modelo.Portal;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.puertos.AgendaRepositorio;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ExpedienteRepositorio;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.PublicadorPortal;
import co.abogados.procesos.dominio.puertos.ResumenRepositorio;

/** F9: el cliente ve solo sus procesos, sin la agenda interna; el abogado publica el resumen y se le avisa. */
class PortalServicioTest {

	static final LocalDate HOY = LocalDate.of(2026, 10, 19);

	ProcesoRepositorio procesos = mock(ProcesoRepositorio.class);
	ExpedienteRepositorio expediente = mock(ExpedienteRepositorio.class);
	AgendaRepositorio agenda = mock(AgendaRepositorio.class);
	ResumenRepositorio resumenes = mock(ResumenRepositorio.class);
	DirectorioGabinete gabinete = mock(DirectorioGabinete.class);
	PublicadorPortal publicador = mock(PublicadorPortal.class);
	PortalServicio servicio;

	static ProcesoVista proceso(int id, int cliente) {
		return new ProcesoVista(id, "11001310303220260050100", "11001-31-03-032-2026-00501-00", (short) 1, "Verbal",
				"CIVIL", "Civil y comercial", "Juzgado 32", null, "Juzgado 32 Civil del Circuito", 0, cliente,
				"DEMANDANTE", "Incumplimiento de contrato", new BigDecimal("1000"), "MAYOR", "REPRESENTACION", "ACTIVO",
				"PRUEBAS", LocalDate.of(2026, 3, 1), true, 52345678L);
	}

	@BeforeEach
	void preparar() {
		servicio = new PortalServicio(procesos, expediente, agenda, resumenes, gabinete, publicador,
				Clock.fixed(HOY.atTime(9, 0).atZone(ZoneId.of("America/Bogota")).toInstant(), ZoneId.of("America/Bogota")));
		given(procesos.buscar(34)).willReturn(Optional.of(proceso(34, 6)));
		given(gabinete.abogado(52345678L)).willReturn(Optional.of(new DirectorioGabinete.Abogado(52345678L, "Catalina",
				"Gómez Restrepo", true)));
		given(expediente.lineaDeTiempo(34)).willReturn(List.of(
				new EventoExpediente(LocalDate.of(2026, 11, 2), "TERMINO", "PENDIENTE", 9, "Contestar", "10 días", null),
				new EventoExpediente(LocalDate.of(2026, 11, 10), "ACTUACION", "AUDIENCIA", 5, "Audiencia inicial", null, "FIRMA"),
				new EventoExpediente(LocalDate.of(2026, 10, 15), "PROVIDENCIA", "AUTO", 4, "Decreta pruebas", null, "VIGILANCIA"),
				new EventoExpediente(LocalDate.of(2026, 9, 1), "ACTUACION", "MEMORIAL", 2, "Memorial de pruebas", null, "FIRMA")));
		given(agenda.audiencias(eq(HOY), any(), eq(null))).willReturn(List.of(new AudienciaVista(5, 34, "R", "R", "Asunto",
				LocalDate.of(2026, 11, 10), LocalTime.of(9, 0), "Audiencia inicial", "Sala 3", 52345678L)));
	}

	@Test
	void elDetalleNoMuestraLosTerminosInternosNiLoProgramadoYTraeLaProximaAudiencia() {
		Portal.Detalle d = servicio.detalle(6, 34);

		assertThat(d.novedades()).extracting(Portal.Novedad::titulo)
				.containsExactly("Auto: Decreta pruebas", "Memorial de pruebas");
		assertThat(d.novedades().get(0).origen()).isEqualTo("El juzgado");
		assertThat(d.proceso().proximaAudiencia().lugar()).isEqualTo("Sala 3");
		assertThat(d.proceso().abogado()).isEqualTo("Catalina Gómez Restrepo");
		assertThat(d.proceso().ultimaNovedad()).isEqualTo(LocalDate.of(2026, 10, 15));
	}

	@Test
	void unProcesoDeOtroClienteNoExisteParaEsteCliente() {
		assertThatThrownBy(() -> servicio.detalle(1, 34)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void publicarUnResumenAvisaAlCliente() {
		given(resumenes.guardar(eq(34), any(), anyLong())).willAnswer(i -> new Portal.ResumenPublicado(8, 34,
				i.getArgument(1), i.getArgument(2), Instant.now()));

		servicio.publicarResumen(34, "  Su proceso está en la etapa de pruebas; el juez decretó las pruebas.  ", 52345678L);

		verify(resumenes).guardar(34, "Su proceso está en la etapa de pruebas; el juez decretó las pruebas.", 52345678L);
		verify(publicador).resumenPublicado(org.mockito.ArgumentMatchers.argThat(e -> e.idCliente() == 6 && e.idResumen() == 8));
	}

	@Test
	void unResumenDemasiadoCortoNoSePublica() {
		assertThatThrownBy(() -> servicio.publicarResumen(34, "Todo bien", 1L)).isInstanceOf(ReglaNegocioException.class);
		verify(publicador, never()).resumenPublicado(any());
	}

}
