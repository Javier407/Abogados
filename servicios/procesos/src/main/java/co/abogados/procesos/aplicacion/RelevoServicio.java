package co.abogados.procesos.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.procesos.dominio.modelo.EstadoProceso;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Relevo;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.PublicadorEquipo;
import co.abogados.procesos.dominio.puertos.RelevoRepositorio;

/**
 * Relevo del abogado responsable: otro abogado recibe el proceso y queda escrito por qué.
 * - Quien recibe debe ser un abogado activo de la firma y distinto del actual.
 * - El anterior sale del equipo o queda de apoyo; sus términos pendientes y audiencias futuras pasan al nuevo si
 *   así se pide (si no, seguirían avisándole a quien ya no lleva el proceso).
 * - Un proceso cerrado no cambia de responsable.
 * - Se avisa a los dos abogados (evento proceso.reasignado).
 */
@Service
@Transactional
public class RelevoServicio {

	private static final Logger log = LoggerFactory.getLogger(RelevoServicio.class);

	private final RelevoRepositorio relevos;
	private final ProcesoRepositorio procesos;
	private final DirectorioGabinete gabinete;
	private final PublicadorEquipo eventos;
	private final Clock reloj;

	public RelevoServicio(RelevoRepositorio relevos, ProcesoRepositorio procesos, DirectorioGabinete gabinete,
			PublicadorEquipo eventos, Clock reloj) {
		this.relevos = relevos;
		this.procesos = procesos;
		this.gabinete = gabinete;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	public Relevo.Resultado relevar(int idProceso, Relevo.Solicitud solicitud, long autor) {
		ProcesoVista proceso = proceso(idProceso);
		if (EstadoProceso.valueOf(proceso.estado()).esCerrado()) {
			throw new ReglaNegocioException("El proceso está cerrado: reábralo antes de cambiar el abogado a cargo");
		}
		Long anterior = proceso.idResponsable();
		if (anterior != null && anterior == solicitud.idNuevoResponsable()) {
			throw new ReglaNegocioException("Ese abogado ya está a cargo del proceso");
		}
		DirectorioGabinete.Abogado nuevo = gabinete.abogado(solicitud.idNuevoResponsable())
				.orElseThrow(() -> new ReglaNegocioException(
						"No existe un abogado con la cédula " + solicitud.idNuevoResponsable()));
		if (!nuevo.activo()) {
			throw new ReglaNegocioException("El abogado " + nuevo.nombres() + " " + nuevo.apellidos()
					+ " está inactivo y no puede recibir procesos");
		}

		Relevo.Resultado resultado = relevos.relevar(idProceso, anterior, solicitud, autor);
		eventos.procesoReasignado(new Relevo.Reasignado(idProceso, proceso.radicado(), proceso.asunto(), anterior,
				solicitud.idNuevoResponsable(), solicitud.motivo().name(), resultado.terminosTrasladados(),
				Instant.now(reloj)));
		log.info("Proceso {}: el responsable pasa de {} a {} ({}) por {}; {} término(s) y {} audiencia(s) trasladados",
				idProceso, anterior, solicitud.idNuevoResponsable(), solicitud.motivo(), autor,
				resultado.terminosTrasladados(), resultado.audienciasTrasladadas());
		return resultado;
	}

	@Transactional(readOnly = true)
	public List<Relevo.Periodo> historial(int idProceso) {
		proceso(idProceso);
		Map<Long, String> nombres = new HashMap<>();
		return relevos.historial(idProceso).stream()
				.map(p -> {
					String nombre = nombres.computeIfAbsent(p.idPersona(), this::nombre);
					return p.conNombre(nombre.isEmpty() ? null : nombre);
				})
				.toList();
	}

	private ProcesoVista proceso(int idProceso) {
		return procesos.buscar(idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
	}

	/** Si gabinete no responde, el historial se muestra igual, sin nombres. */
	private String nombre(long idPersona) {
		try {
			return gabinete.abogado(idPersona).map(p -> (p.nombres() + " " + p.apellidos()).strip()).orElse("");
		} catch (ServicioNoDisponibleException e) {
			return "";
		}
	}

}
