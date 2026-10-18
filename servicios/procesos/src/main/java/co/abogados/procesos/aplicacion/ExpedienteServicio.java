package co.abogados.procesos.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.procesos.dominio.modelo.Actuacion;
import co.abogados.procesos.dominio.modelo.ConflictoInteres;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.EventoExpediente;
import co.abogados.procesos.dominio.modelo.Instancia;
import co.abogados.procesos.dominio.modelo.Parte;
import co.abogados.procesos.dominio.modelo.ParteRegistrada;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.TerminoVista;
import co.abogados.procesos.dominio.modelo.Texto;
import co.abogados.procesos.dominio.puertos.ActuacionRepositorio;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete.ClienteResumen;
import co.abogados.procesos.dominio.puertos.ExpedienteRepositorio;
import co.abogados.procesos.dominio.puertos.ParteRepositorio;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.TerminoRepositorio;

/**
 * Expediente del proceso: partes (con aviso de conflicto de intereses), actuaciones de la firma,
 * línea de tiempo y otras instancias del mismo radicado.
 */
@Service
@Transactional
public class ExpedienteServicio {

	private static final Logger log = LoggerFactory.getLogger(ExpedienteServicio.class);

	private final ProcesoRepositorio procesos;
	private final ParteRepositorio partes;
	private final ActuacionRepositorio actuaciones;
	private final ExpedienteRepositorio expediente;
	private final TerminoRepositorio terminos;
	private final DirectorioGabinete gabinete;
	private final Clock reloj;

	public ExpedienteServicio(ProcesoRepositorio procesos, ParteRepositorio partes, ActuacionRepositorio actuaciones,
			ExpedienteRepositorio expediente, TerminoRepositorio terminos, DirectorioGabinete gabinete, Clock reloj) {
		this.procesos = procesos;
		this.partes = partes;
		this.actuaciones = actuaciones;
		this.expediente = expediente;
		this.terminos = terminos;
		this.gabinete = gabinete;
		this.reloj = reloj;
	}

	// ----------------------------------------------------------------- partes

	@Transactional(readOnly = true)
	public List<Parte> partes(int idProceso) {
		proceso(idProceso);
		return partes.deProceso(idProceso);
	}

	/** Agrega la contraparte o un tercero. El cliente lo agrega el propio proceso, no esta operación. */
	public ParteRegistrada agregarParte(int idProceso, Parte datos) {
		ProcesoVista proceso = proceso(idProceso);
		Parte nueva = new Parte(null, idProceso, datos.rol(), datos.tipoDocumento(), datos.numeroDocumento(),
				datos.nombre(), false, datos.apoderado());
		int id = partes.crear(nueva);
		return conConflictos(proceso, partes.buscar(id).orElseThrow());
	}

	public ParteRegistrada editarParte(int idProceso, int idParte, Parte datos) {
		ProcesoVista proceso = proceso(idProceso);
		Parte actual = parteDelProceso(idProceso, idParte);
		if (actual.esCliente()) {
			throw new ReglaNegocioException("La parte que es el cliente se cambia editando el proceso");
		}
		partes.actualizar(new Parte(idParte, idProceso, datos.rol(), datos.tipoDocumento(), datos.numeroDocumento(),
				datos.nombre(), false, datos.apoderado()));
		return conConflictos(proceso, partes.buscar(idParte).orElseThrow());
	}

	public void eliminarParte(int idProceso, int idParte) {
		Parte actual = parteDelProceso(idProceso, idParte);
		if (actual.esCliente()) {
			throw new ReglaNegocioException("No se puede quitar al cliente del proceso");
		}
		partes.eliminar(idParte);
	}

	/**
	 * Busca la parte entre los clientes de la firma. No bloquea: la decisión de aceptar el caso es del abogado,
	 * pero debe saberlo. Si gabinete no responde, la parte queda guardada y se avisa que no se verificó.
	 */
	private ParteRegistrada conConflictos(ProcesoVista proceso, Parte parte) {
		try {
			return new ParteRegistrada(parte, conflictos(proceso, parte), true);
		} catch (ServicioNoDisponibleException e) {
			log.warn("No se verificó el conflicto de intereses de la parte {}: gabinete no responde", parte.idParte());
			return new ParteRegistrada(parte, List.of(), false);
		}
	}

	/**
	 * Compara contra todos los clientes de la firma: la búsqueda de gabinete distingue tildes y puntos
	 * ("S.A.S." frente a "SAS"), así que se normaliza aquí. Sirve para cientos o pocos miles de clientes;
	 * más allá conviene una búsqueda normalizada en gabinete (por ejemplo, con unaccent).
	 */
	List<ConflictoInteres> conflictos(ProcesoVista proceso, Parte parte) {
		String nombre = Texto.normalizar(parte.nombre());
		List<ConflictoInteres> encontrados = new ArrayList<>();
		for (ClienteResumen c : gabinete.buscarClientes(null)) {
			boolean mismoDocumento = parte.numeroDocumento() != null
					&& parte.numeroDocumento().equalsIgnoreCase(c.numeroDocumento());
			boolean mismoNombre = nombre.equals(Texto.normalizar(c.nombre()));
			if (!mismoDocumento && !mismoNombre) {
				continue;
			}
			String motivo = c.idCliente() == proceso.idCliente()
					? "Es el mismo cliente de este proceso"
					: mismoDocumento ? "Tiene el mismo documento que un cliente de la firma"
							: "Tiene el mismo nombre que un cliente de la firma";
			encontrados.add(new ConflictoInteres(c.idCliente(), c.nombre(), c.numeroDocumento(), motivo));
		}
		return encontrados;
	}

	// ----------------------------------------------------------------- actuaciones

	@Transactional(readOnly = true)
	public List<Actuacion> actuaciones(int idProceso) {
		proceso(idProceso);
		return actuaciones.deProceso(idProceso);
	}

	/**
	 * Registra lo que hizo la firma. Si se indica idTerminoCumplido, ese término queda cumplido (por ejemplo,
	 * la contestación cumple el término para contestar). Si la actuación ya ocurrió y abre una etapa posterior
	 * a la actual (demanda, contestación, recurso), el proceso avanza; nunca retrocede.
	 */
	public Actuacion registrarActuacion(int idProceso, Actuacion datos, Integer idTerminoCumplido, Long registradoPor) {
		ProcesoVista proceso = proceso(idProceso);
		if (datos.idProvidencia() != null && !terminos.providenciaDelProceso(datos.idProvidencia(), idProceso)) {
			throw new ReglaNegocioException("La providencia " + datos.idProvidencia() + " no es de este proceso");
		}
		LocalDate hoy = LocalDate.now(reloj);
		if (idTerminoCumplido != null) {
			TerminoVista t = terminos.buscar(idTerminoCumplido)
					.filter(x -> x.idProceso() == idProceso)
					.orElseThrow(() -> new ReglaNegocioException("El término " + idTerminoCumplido + " no es de este proceso"));
			if ("CUMPLIDO".equals(t.estado())) {
				throw new ConflictoException("El término «" + t.descripcion() + "» ya estaba cumplido");
			}
			if (datos.fecha().isAfter(hoy)) {
				throw new ReglaNegocioException("Una actuación programada no puede dar por cumplido un término");
			}
		}

		int id = actuaciones.crear(new Actuacion(null, idProceso, datos.fecha(), datos.tipo(), datos.descripcion(),
				datos.idPersona(), datos.idProvidencia(), datos.documentoUrl(), registradoPor, datos.hora(), datos.lugar()));
		if (idTerminoCumplido != null) {
			terminos.marcarCumplido(idTerminoCumplido);
		}
		EtapaProceso etapa = datos.tipo().etapaQueAbre();
		if (etapa != null && !datos.fecha().isAfter(hoy)) {
			avanzar(proceso, etapa);
		}
		return actuaciones.buscar(id).orElseThrow();
	}

	public void eliminarActuacion(int idProceso, int idActuacion) {
		actuaciones.buscar(idActuacion)
				.filter(a -> a.idProceso() == idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la actuación " + idActuacion + " en este proceso"));
		actuaciones.eliminar(idActuacion);
	}

	// ----------------------------------------------------------------- consulta y etapas

	@Transactional(readOnly = true)
	public List<EventoExpediente> lineaDeTiempo(int idProceso) {
		proceso(idProceso);
		return expediente.lineaDeTiempo(idProceso);
	}

	@Transactional(readOnly = true)
	public List<Instancia> otrasInstancias(int idProceso) {
		proceso(idProceso);
		return expediente.otrasInstancias(idProceso);
	}

	private void avanzar(ProcesoVista proceso, EtapaProceso etapa) {
		if (EtapaProceso.valueOf(proceso.etapa()).avanzaA(etapa, proceso.estado())) {
			expediente.avanzarEtapa(proceso.idProceso(), etapa);
		}
	}

	private ProcesoVista proceso(int idProceso) {
		return procesos.buscar(idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
	}

	private Parte parteDelProceso(int idProceso, int idParte) {
		return partes.buscar(idParte)
				.filter(p -> p.idProceso() == idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la parte " + idParte + " en este proceso"));
	}

}
