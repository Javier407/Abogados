package co.abogados.procesos.infraestructura.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.ProvidenciaServicio;
import co.abogados.procesos.dominio.modelo.ProvidenciaVista;

/** Autos y sentencias del expediente, de la más reciente a la más antigua. */
@RestController
public class ProvidenciasRecurso {

	private final ProvidenciaServicio servicio;

	public ProvidenciasRecurso(ProvidenciaServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/procesos/{idProceso}/providencias")
	public List<ProvidenciaVista> deProceso(@PathVariable int idProceso) {
		return servicio.deProceso(idProceso);
	}

}
