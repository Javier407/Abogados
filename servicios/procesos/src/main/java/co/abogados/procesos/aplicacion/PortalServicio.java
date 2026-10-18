package co.abogados.procesos.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.procesos.dominio.modelo.AudienciaVista;
import co.abogados.procesos.dominio.modelo.FiltroProcesos;
import co.abogados.procesos.dominio.modelo.Portal;
import co.abogados.procesos.dominio.modelo.Portal.Audiencia;
import co.abogados.procesos.dominio.modelo.Portal.Novedad;
import co.abogados.procesos.dominio.modelo.Portal.ProcesoCliente;
import co.abogados.procesos.dominio.modelo.Portal.ResumenAvisado;
import co.abogados.procesos.dominio.modelo.Portal.ResumenPublicado;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.puertos.AgendaRepositorio;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ExpedienteRepositorio;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.PublicadorPortal;
import co.abogados.procesos.dominio.puertos.ResumenRepositorio;

/**
 * Portal del cliente y resúmenes publicados.
 * - El cliente solo ve sus procesos: el idCliente sale de su token, nunca de la petición. Un proceso ajeno
 *   responde "no existe" (no se revela que existe).
 * - El resumen lo redacta (o lo genera el asistente y lo corrige) el abogado y lo publica; al publicarlo se avisa
 *   al cliente.
 */
@Service
public class PortalServicio {

	/** Hasta cuándo se muestran audiencias programadas. */
	private static final int DIAS_AGENDA = 180;

	private final ProcesoRepositorio procesos;
	private final ExpedienteRepositorio expediente;
	private final AgendaRepositorio agenda;
	private final ResumenRepositorio resumenes;
	private final DirectorioGabinete gabinete;
	private final PublicadorPortal publicador;
	private final Clock reloj;

	public PortalServicio(ProcesoRepositorio procesos, ExpedienteRepositorio expediente, AgendaRepositorio agenda,
			ResumenRepositorio resumenes, DirectorioGabinete gabinete, PublicadorPortal publicador, Clock reloj) {
		this.procesos = procesos;
		this.expediente = expediente;
		this.agenda = agenda;
		this.resumenes = resumenes;
		this.gabinete = gabinete;
		this.publicador = publicador;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public List<ProcesoCliente> procesosDe(int idCliente) {
		List<AudienciaVista> proximas = proximasAudiencias();
		return procesos.listar(new FiltroProcesos(null, null, idCliente, null, null)).stream()
				.map(p -> aCliente(p, proximas))
				.sorted(Comparator.comparing(ProcesoCliente::estado).thenComparing(ProcesoCliente::fechaRadicacion,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
	}

	@Transactional(readOnly = true)
	public Portal.Detalle detalle(int idCliente, int idProceso) {
		ProcesoVista p = procesos.buscar(idProceso).filter(v -> v.idCliente() == idCliente)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
		List<AudienciaVista> proximas = proximasAudiencias();
		LocalDate hoy = LocalDate.now(reloj);
		List<Novedad> novedades = expediente.lineaDeTiempo(idProceso).stream().map(Portal::novedad)
				.filter(Objects::nonNull).filter(n -> !n.fecha().isAfter(hoy)).toList();   // lo programado va en audiencias
		List<Audiencia> audiencias = proximas.stream().filter(a -> a.idProceso() == idProceso).map(PortalServicio::audiencia)
				.toList();
		return new Portal.Detalle(aCliente(p, proximas), novedades, audiencias, resumenes.ultimo(idProceso).orElse(null));
	}

	// ------------------------------------------------------------------ resúmenes (los publica el abogado)

	@Transactional(readOnly = true)
	public Optional<ResumenPublicado> ultimoResumen(int idProceso) {
		existe(idProceso);
		return resumenes.ultimo(idProceso);
	}

	@Transactional
	public ResumenPublicado publicarResumen(int idProceso, String texto, long abogado) {
		ProcesoVista p = existe(idProceso);
		ResumenPublicado r = resumenes.guardar(idProceso, ResumenPublicado.validar(texto), abogado);
		publicador.resumenPublicado(new ResumenAvisado(r.idResumen(), idProceso, p.radicadoLegible(), p.idCliente(),
				Instant.now(reloj)));
		return r;
	}

	// ------------------------------------------------------------------

	private ProcesoVista existe(int idProceso) {
		return procesos.buscar(idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
	}

	private List<AudienciaVista> proximasAudiencias() {
		LocalDate hoy = LocalDate.now(reloj);
		return agenda.audiencias(hoy, hoy.plusDays(DIAS_AGENDA), null);
	}

	private ProcesoCliente aCliente(ProcesoVista p, List<AudienciaVista> proximas) {
		Audiencia proxima = proximas.stream().filter(a -> a.idProceso() == p.idProceso()).findFirst()
				.map(PortalServicio::audiencia).orElse(null);
		LocalDate novedad = expediente.lineaDeTiempo(p.idProceso()).stream().map(Portal::novedad)
				.filter(Objects::nonNull).map(Novedad::fecha).filter(f -> !f.isAfter(LocalDate.now(reloj)))
				.max(Comparator.naturalOrder()).orElse(null);
		return new ProcesoCliente(p.idProceso(), p.radicadoLegible(), p.tipoProceso(), p.rama(), p.despachoActual(),
				p.asunto(), p.rolCliente(), p.etapa(), p.estado(), p.fechaRadicacion(), abogado(p.idResponsable()),
				proxima, novedad);
	}

	/** Nombre del abogado responsable; si gabinete no responde, el portal sigue sin el nombre. */
	private String abogado(Long idResponsable) {
		if (idResponsable == null) {
			return null;
		}
		try {
			return gabinete.abogado(idResponsable).map(a -> (a.nombres() + " " + a.apellidos()).strip()).orElse(null);
		} catch (ServicioNoDisponibleException e) {
			return null;
		}
	}

	private static Audiencia audiencia(AudienciaVista a) {
		return new Audiencia(a.fecha(), a.hora(), a.lugar(), a.descripcion());
	}

}
