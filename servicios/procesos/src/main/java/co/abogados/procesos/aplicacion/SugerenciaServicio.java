package co.abogados.procesos.aplicacion;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.modelo.FiltroTerminos;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.ProvidenciaVista;
import co.abogados.procesos.dominio.modelo.SugerenciaTermino;
import co.abogados.procesos.dominio.modelo.SugeridorTerminos;
import co.abogados.procesos.dominio.modelo.TerminoVista;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.ProvidenciaRepositorio;
import co.abogados.procesos.dominio.puertos.ReglaTerminoRepositorio;
import co.abogados.procesos.dominio.puertos.TerminoRepositorio;

/**
 * Términos sugeridos para una providencia. El abogado los crea con POST /procesos/{id}/terminos indicando la
 * providencia; así dejan de sugerirse.
 */
@Service
@Transactional(readOnly = true)
public class SugerenciaServicio {

	private final ProcesoRepositorio procesos;
	private final ProvidenciaRepositorio providencias;
	private final ReglaTerminoRepositorio reglas;
	private final TerminoRepositorio terminos;
	private final CalendarioServicio calendario;

	public SugerenciaServicio(ProcesoRepositorio procesos, ProvidenciaRepositorio providencias,
			ReglaTerminoRepositorio reglas, TerminoRepositorio terminos, CalendarioServicio calendario) {
		this.procesos = procesos;
		this.providencias = providencias;
		this.reglas = reglas;
		this.terminos = terminos;
		this.calendario = calendario;
	}

	public List<SugerenciaTermino> sugerencias(int idProceso, int idProvidencia) {
		ProcesoVista proceso = procesos.buscar(idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
		ProvidenciaVista providencia = providencias.deProceso(idProceso).stream()
				.filter(p -> p.idProvidencia() == idProvidencia).findFirst()
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la providencia " + idProvidencia + " en este proceso"));
		List<String> yaCreados = terminos.listar(new FiltroTerminos(null, null, idProceso, null, null)).stream()
				.filter(t -> t.idProvidencia() != null && t.idProvidencia() == idProvidencia)
				.map(TerminoVista::descripcion)
				.toList();
		return SugeridorTerminos.sugerir(proceso, providencia, reglas.reglas(),
				reglas.diasContestacion(proceso.idTipo()).orElse(null), yaCreados,
				dias -> calendario.vencimiento(providencia.fechaEstado(), dias));
	}

}
