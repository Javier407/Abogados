package co.abogados.gabinete.infraestructura.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.gabinete.aplicacion.CatalogoServicio;
import co.abogados.gabinete.aplicacion.TarifaServicio;
import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Sede;
import co.abogados.gabinete.dominio.modelo.TarifaVista;

@RestController
@RequestMapping("/gabinete")
public class CatalogosRecurso {

	private final CatalogoServicio servicio;
	private final TarifaServicio tarifas;

	public CatalogosRecurso(CatalogoServicio servicio, TarifaServicio tarifas) {
		this.servicio = servicio;
		this.tarifas = tarifas;
	}

	/** Tarifas de referencia con su valor en pesos para el salario mínimo del año (por defecto, el actual). */
	@GetMapping("/tarifas")
	public List<TarifaVista> tarifas(@RequestParam(required = false) Integer anio) {
		return tarifas.listar(anio);
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
