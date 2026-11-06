package co.abogados.gabinete.infraestructura.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.gabinete.aplicacion.CatalogoServicio;
import co.abogados.gabinete.aplicacion.TarifaServicio;
import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Sede;
import co.abogados.gabinete.dominio.modelo.TarifaVista;

/**
 * Catálogos de la firma. Consultar: todo el personal. Crear, cambiar y borrar sedes, cargos y áreas (F11): la
 * dirección (lo exige el gateway). El id del cuerpo se ignora: en PUT manda el de la ruta.
 */
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

	@PostMapping("/sedes")
	public ResponseEntity<Sede> crearSede(@RequestBody Sede s, @RequestHeader("X-Usuario-Id") long usuario) {
		return ResponseEntity.status(HttpStatus.CREATED).body(servicio.guardarSede(null, s, usuario));
	}

	@PutMapping("/sedes/{id}")
	public Sede cambiarSede(@PathVariable short id, @RequestBody Sede s, @RequestHeader("X-Usuario-Id") long usuario) {
		return servicio.guardarSede(id, s, usuario);
	}

	@DeleteMapping("/sedes/{id}")
	public ResponseEntity<Void> borrarSede(@PathVariable short id, @RequestHeader("X-Usuario-Id") long usuario) {
		servicio.eliminarSede(id, usuario);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/cargos")
	public List<Cargo> cargos() {
		return servicio.cargos();
	}

	@PostMapping("/cargos")
	public ResponseEntity<Cargo> crearCargo(@RequestBody Cargo c, @RequestHeader("X-Usuario-Id") long usuario) {
		return ResponseEntity.status(HttpStatus.CREATED).body(servicio.guardarCargo(null, c, usuario));
	}

	@PutMapping("/cargos/{id}")
	public Cargo cambiarCargo(@PathVariable short id, @RequestBody Cargo c, @RequestHeader("X-Usuario-Id") long usuario) {
		return servicio.guardarCargo(id, c, usuario);
	}

	@DeleteMapping("/cargos/{id}")
	public ResponseEntity<Void> borrarCargo(@PathVariable short id, @RequestHeader("X-Usuario-Id") long usuario) {
		servicio.eliminarCargo(id, usuario);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/areas")
	public List<AreaPractica> areas() {
		return servicio.areas();
	}

	@PostMapping("/areas")
	public ResponseEntity<AreaPractica> crearArea(@RequestBody AreaPractica a, @RequestHeader("X-Usuario-Id") long usuario) {
		return ResponseEntity.status(HttpStatus.CREATED).body(servicio.guardarArea(null, a, usuario));
	}

	@PutMapping("/areas/{id}")
	public AreaPractica cambiarArea(@PathVariable short id, @RequestBody AreaPractica a,
			@RequestHeader("X-Usuario-Id") long usuario) {
		return servicio.guardarArea(id, a, usuario);
	}

	@DeleteMapping("/areas/{id}")
	public ResponseEntity<Void> borrarArea(@PathVariable short id, @RequestHeader("X-Usuario-Id") long usuario) {
		servicio.eliminarArea(id, usuario);
		return ResponseEntity.noContent().build();
	}

}
