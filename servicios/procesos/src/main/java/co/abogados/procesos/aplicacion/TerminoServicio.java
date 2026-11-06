package co.abogados.procesos.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.AvisoTermino;
import co.abogados.procesos.dominio.modelo.EstadoTermino;
import co.abogados.procesos.dominio.modelo.FiltroTerminos;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Termino;
import co.abogados.procesos.dominio.modelo.TerminoVista;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.PublicadorEventos;
import co.abogados.procesos.dominio.puertos.TerminoRepositorio;

/**
 * Términos de los procesos: registrarlos con su vencimiento en días hábiles, marcarlos cumplidos
 * y avisar cada mañana de los que están por vencer o ya vencieron.
 */
@Service
@Transactional
public class TerminoServicio {

	private static final Logger log = LoggerFactory.getLogger(TerminoServicio.class);

	private final TerminoRepositorio terminos;
	private final ProcesoRepositorio procesos;
	private final CalendarioServicio calendario;
	private final DirectorioGabinete gabinete;
	private final PublicadorEventos eventos;
	private final Clock reloj;
	private final int diasAviso;
	private final int diasRecordarVencidos;

	public TerminoServicio(TerminoRepositorio terminos, ProcesoRepositorio procesos, CalendarioServicio calendario,
			DirectorioGabinete gabinete, PublicadorEventos eventos, Clock reloj,
			@Value("${terminos.dias-aviso:5}") int diasAviso,
			@Value("${terminos.dias-recordar-vencidos:3}") int diasRecordarVencidos) {
		this.terminos = terminos;
		this.procesos = procesos;
		this.calendario = calendario;
		this.gabinete = gabinete;
		this.eventos = eventos;
		this.reloj = reloj;
		this.diasAviso = diasAviso;
		this.diasRecordarVencidos = diasRecordarVencidos;
	}

	/** Lo que llega del formulario: el vencimiento lo calcula el servicio. */
	public record NuevoTermino(String descripcion, LocalDate fechaInicio, int diasHabiles, Integer idProvidencia,
			Long idResponsable) {
	}

	/** Resultado de una revisión de vencimientos. */
	public record Revision(LocalDate fecha, int porVencer, int vencidos) {
	}

	@Transactional(readOnly = true)
	public List<TerminoVista> listar(FiltroTerminos filtro) {
		return terminos.listar(filtro);
	}

	@Transactional(readOnly = true)
	public TerminoVista obtener(int idTermino) {
		return terminos.buscar(idTermino)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el término " + idTermino));
	}

	public TerminoVista crear(int idProceso, NuevoTermino nuevo) {
		ProcesoVista proceso = procesos.buscar(idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
		if (proceso.estado().equals("TERMINADO") || proceso.estado().equals("ARCHIVADO")) {
			throw new ReglaNegocioException("El proceso está " + proceso.estado() + ": no corren términos");
		}
		if (nuevo.fechaInicio() == null) {
			throw new ReglaNegocioException("La fecha de notificación es obligatoria");
		}
		if (nuevo.fechaInicio().isAfter(LocalDate.now(reloj))) {
			throw new ReglaNegocioException("La fecha de notificación no puede ser futura");
		}
		if (nuevo.idProvidencia() != null && !terminos.providenciaDelProceso(nuevo.idProvidencia(), idProceso)) {
			throw new ReglaNegocioException("La providencia " + nuevo.idProvidencia() + " no es de este proceso");
		}
		long responsable = responsable(proceso, nuevo.idResponsable());
		LocalDate vence = calendario.vencimiento(nuevo.fechaInicio(), nuevo.diasHabiles());

		int id = terminos.crear(new Termino(null, idProceso, nuevo.idProvidencia(), nuevo.descripcion(),
				nuevo.fechaInicio(), nuevo.diasHabiles(), vence, EstadoTermino.PENDIENTE, responsable));
		return obtener(id);
	}

	public TerminoVista cumplir(int idTermino) {
		TerminoVista t = obtener(idTermino);
		if (t.estado().equals("CUMPLIDO")) {
			throw new ConflictoException("El término " + idTermino + " ya estaba cumplido");
		}
		terminos.marcarCumplido(idTermino);
		return obtener(idTermino);
	}

	public void eliminar(int idTermino) {
		obtener(idTermino);
		terminos.eliminar(idTermino);
	}

	/**
	 * Publica termino.por_vencer o termino.vencido por cada término pendiente que lo amerite.
	 * La corre la tarea programada de cada mañana; también se puede pedir a mano. Si se repite el
	 * mismo día, el servicio notificaciones no duplica los avisos (usa una clave por día).
	 */
	@Transactional(readOnly = true)
	public Revision revisarVencimientos() {
		Instant ahora = Instant.now(reloj);
		int porVencer = 0;
		int vencidos = 0;
		for (TerminoVista t : terminos.paraAvisar(diasAviso, diasRecordarVencidos)) {
			AvisoTermino.Tipo tipo = t.diasHabilesRestantes() < 0 || t.situacion().equals("VENCIDO")
					? AvisoTermino.Tipo.VENCIDO
					: AvisoTermino.Tipo.POR_VENCER;
			eventos.avisoTermino(new AvisoTermino(tipo, t.idTermino(), t.idProceso(), t.radicado(), t.descripcion(),
					t.fechaVencimiento(), t.diasHabilesRestantes(), t.idResponsable(), ahora));
			if (tipo == AvisoTermino.Tipo.VENCIDO) {
				vencidos++;
			} else {
				porVencer++;
			}
		}
		log.info("Revisión de términos: {} por vencer, {} vencidos", porVencer, vencidos);
		return new Revision(LocalDate.now(reloj), porVencer, vencidos);
	}

	/** Por defecto, el responsable del proceso; otro abogado debe existir y estar activo. */
	private long responsable(ProcesoVista proceso, Long pedido) {
		if (pedido == null || pedido.equals(proceso.idResponsable())) {
			if (proceso.idResponsable() == null) {
				throw new ReglaNegocioException("El proceso no tiene abogado responsable; indique quién atiende el término");
			}
			return proceso.idResponsable();
		}
		DirectorioGabinete.Abogado a = gabinete.abogado(pedido)
				.orElseThrow(() -> new ReglaNegocioException("No existe un abogado con la cédula " + pedido));
		if (!a.activo()) {
			throw new ReglaNegocioException("El abogado " + a.nombres() + " " + a.apellidos() + " está inactivo");
		}
		return pedido;
	}

}
