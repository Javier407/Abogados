package co.abogados.procesos.aplicacion;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.Cierre;
import co.abogados.procesos.dominio.modelo.EstadoProceso;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.puertos.CierreRepositorio;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;

/**
 * Cierre de un proceso con su motivo (sentencia, sentencia anticipada, conciliación, desistimiento, retiro de la
 * demanda, liquidación, renuncia al poder, archivo…). Al cerrarlo:
 * - queda TERMINADO (o ARCHIVADO) en la etapa CERRADO, con fecha, motivo y nota;
 * - sus términos pendientes y audiencias dejan de avisar y de salir en la agenda (no se borran: se ven en el
 *   expediente y vuelven si se reabre);
 * - deja de vigilarse.
 * Los honorarios no se tocan: liquidarlos es una decisión aparte.
 */
@Service
@Transactional
public class CierreServicio {

	private static final Logger log = LoggerFactory.getLogger(CierreServicio.class);

	private final CierreRepositorio cierres;
	private final ProcesoRepositorio procesos;
	private final Clock reloj;

	public CierreServicio(CierreRepositorio cierres, ProcesoRepositorio procesos, Clock reloj) {
		this.cierres = cierres;
		this.procesos = procesos;
		this.reloj = reloj;
	}

	/** Sin fecha, hoy. */
	public Cierre.Resultado cerrar(int idProceso, Cierre.Motivo motivo, LocalDate fecha, String nota, long autor) {
		Cierre cierre = new Cierre(motivo, fecha != null ? fecha : LocalDate.now(reloj), nota);
		ProcesoVista proceso = proceso(idProceso);
		if (EstadoProceso.valueOf(proceso.estado()).esCerrado()) {
			throw new ConflictoException("El proceso ya está cerrado");
		}
		if (cierre.fecha().isAfter(LocalDate.now(reloj))) {
			throw new ReglaNegocioException("La fecha de cierre no puede ser futura");
		}
		if (cierre.fecha().isBefore(proceso.fechaRadicacion())) {
			throw new ReglaNegocioException("La fecha de cierre no puede ser anterior a la radicación del proceso");
		}
		Cierre.Efectos efectos = cierres.cerrar(idProceso, cierre, autor);
		log.info("Proceso {} cerrado por {} ({}): {} término(s) y {} audiencia(s) quedan sin efecto", idProceso, autor,
				cierre.motivo(), efectos.terminosPendientes(), efectos.audienciasFuturas());
		return new Cierre.Resultado(proceso(idProceso), efectos);
	}

	/**
	 * Reabre un proceso cerrado. Vuelve a la etapa que tenía al cerrarse; si no se sabe (se cerró antes de que se
	 * guardara), hay que indicarla.
	 */
	public ProcesoVista reabrir(int idProceso, EtapaProceso etapa, long autor) {
		ProcesoVista proceso = proceso(idProceso);
		if (!EstadoProceso.valueOf(proceso.estado()).esCerrado()) {
			throw new ConflictoException("El proceso no está cerrado");
		}
		EtapaProceso destino = etapa != null ? etapa
				: proceso.etapaAntesCierre() != null ? EtapaProceso.valueOf(proceso.etapaAntesCierre()) : null;
		if (destino == null) {
			throw new ReglaNegocioException("Indique en qué etapa queda el proceso al reabrirlo");
		}
		if (destino == EtapaProceso.CERRADO) {
			throw new ReglaNegocioException("Un proceso abierto no puede quedar en la etapa Cerrado");
		}
		cierres.reabrir(idProceso, destino);
		log.info("Proceso {} reabierto por {} en la etapa {}", idProceso, autor, destino);
		return proceso(idProceso);
	}

	private ProcesoVista proceso(int idProceso) {
		return procesos.buscar(idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
	}

}
