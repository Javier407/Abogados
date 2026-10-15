package co.abogados.gabinete.infraestructura.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.gabinete.aplicacion.CatalogoServicio;
import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Sede;

@RestController
@RequestMapping("/gabinete")
public class CatalogosRecurso {

	private final CatalogoServicio servicio;

	public CatalogosRecurso(CatalogoServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/sedes")
	public List<Sede> sedes() {
		return servicio.sedes();
	}

	@GetMapping("/cargos")
	public List<Cargo> cargos() {
		return servicio.cargos();
	}

	@GetMapping("/areas")
	public List<AreaPractica> areas() {
		return servicio.areas();
	}

}
