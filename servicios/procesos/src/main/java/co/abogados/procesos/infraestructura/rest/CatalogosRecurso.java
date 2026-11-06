package co.abogados.procesos.infraestructura.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.CatalogoServicio;
import co.abogados.procesos.dominio.modelo.Despacho;
import co.abogados.procesos.dominio.modelo.Rama;
import co.abogados.procesos.dominio.modelo.TipoProceso;

@RestController
@RequestMapping("/catalogos")
public class CatalogosRecurso {

	private final CatalogoServicio servicio;

	public CatalogosRecurso(CatalogoServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/ramas")
	public List<Rama> ramas() {
		return servicio.ramas();
	}

	@GetMapping("/tipos")
	public List<TipoProceso> tipos(@RequestParam(required = false) String rama) {
		return servicio.tipos(rama);
	}

	@GetMapping("/despachos")
	public List<Despacho> despachos(@RequestParam(required = false) String rama) {
		return servicio.despachos(rama);
	}

}
