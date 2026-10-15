package co.abogados.procesos.infraestructura.rest;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.ProcesoServicio;
import co.abogados.procesos.dominio.modelo.DesgloseRadicado;
import co.abogados.procesos.dominio.modelo.EstadoProceso;
import co.abogados.procesos.dominio.modelo.FiltroProcesos;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import jakarta.validation.Valid;

/** API REST de procesos. Contrato: docs/contratos/procesos.openapi.yaml. */
@RestController
@RequestMapping("/procesos")
public class ProcesosRecurso {

	private final ProcesoServicio servicio;

	public ProcesosRecurso(ProcesoServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public List<ProcesoVista> listar(
			@RequestParam(required = false) EstadoProceso estado,
			@RequestParam(required = false) String rama,
			@RequestParam(required = false) Integer idCliente,
			@RequestParam(required = false) Long idAbogado,
			@RequestParam(required = false) String q) {
		return servicio.listar(new FiltroProcesos(estado, rama, idCliente, idAbogado, q));
	}

	@GetMapping("/{id}")
	public ProcesoVista obtener(@PathVariable int id) {
		return servicio.obtener(id);
	}

	@GetMapping("/radicado/{numero}")
	public DesgloseRadicado desglosar(@PathVariable String numero) {
		return servicio.desglosar(numero);
	}

	@PostMapping
	public ResponseEntity<ProcesoVista> crear(@Valid @RequestBody ProcesoSolicitud solicitud) {
		ProcesoVista creado = servicio.crear(solicitud.aDominio());
		return ResponseEntity.created(URI.create("/procesos/" + creado.idProceso())).body(creado);
	}

	@PutMapping("/{id}")
	public ProcesoVista actualizar(@PathVariable int id, @Valid @RequestBody ProcesoSolicitud solicitud) {
		return servicio.actualizar(id, solicitud.aDominio());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable int id) {
		servicio.eliminar(id);
		return ResponseEntity.noContent().build();
	}

}
