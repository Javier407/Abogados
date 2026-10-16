package co.abogados.procesos.aplicacion;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.modelo.ActuacionDetectada;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.ProvidenciaVista;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.ProvidenciaRepositorio;

/** Providencias del expediente, incluidas las que detecta la vigilancia de la Rama Judicial. */
@Service
@Transactional
public class ProvidenciaServicio {

	private static final Logger log = LoggerFactory.getLogger(ProvidenciaServicio.class);

	private final ProvidenciaRepositorio providencias;
	private final ProcesoRepositorio procesos;

	public ProvidenciaServicio(ProvidenciaRepositorio providencias, ProcesoRepositorio procesos) {
		this.providencias = providencias;
		this.procesos = procesos;
	}

	@Transactional(readOnly = true)
	public List<ProvidenciaVista> deProceso(int idProceso) {
		procesos.buscar(idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
		return providencias.deProceso(idProceso);
	}

	/**
	 * Registra una actuación detectada. Es idempotente: si el evento llega repetido (reintentos de
	 * RabbitMQ o de la vigilancia) no se duplica. Devuelve vacío si no hubo nada que registrar.
	 */
	public Optional<Integer> registrarDetectada(ActuacionDetectada a) {
		if (providencias.existeReferencia(a.referencia())) {
			log.info("Actuación {} ya registrada; se ignora", a.referencia());
			return Optional.empty();
		}
		Optional<ProcesoVista> proceso = procesos.buscar(a.idProceso())
				.filter(p -> p.radicado().equals(a.radicado()));
		if (proceso.isEmpty()) {
			log.warn("Actuación {} de un radicado que no coincide con el proceso {}; se ignora", a.referencia(),
					a.idProceso());
			return Optional.empty();
		}
		int id = providencias.registrarDetectada(a.idProceso(), a);
		log.info("Actuación {} registrada como providencia {} del proceso {}", a.referencia(), id, a.idProceso());
		return Optional.of(id);
	}

}
