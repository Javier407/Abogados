package co.abogados.procesos.infraestructura.rest;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.PruebaServicio;
import co.abogados.procesos.dominio.modelo.Prueba;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Pruebas del proceso; las que tienen fecha de entrega crean su término. */
@RestController
public class PruebasRecurso {

	public record PruebaSolicitud(
			@NotNull(message = "Indique el tipo de prueba") Prueba.Tipo tipo,
			@NotBlank(message = "Describa la prueba") @Size(max = 200) String descripcion,
			Integer idPerito,
			Prueba.Estado estado,
			LocalDate fechaEntrega) {

		Prueba aPrueba(int idProceso) {
			return new Prueba(null, idProceso, tipo, descripcion, idPerito, estado, fechaEntrega, null);
		}
	}

	private final PruebaServicio servicio;

	public PruebasRecurso(PruebaServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/procesos/{idProceso}/pruebas")
	public List<Prueba> pruebas(@PathVariable int idProceso) {
		return servicio.pruebas(idProceso);
	}

	@PostMapping("/procesos/{idProceso}/pruebas")
	public ResponseEntity<Prueba> crear(@PathVariable int idProceso, @Valid @RequestBody PruebaSolicitud s) {
		Prueba creada = servicio.crear(idProceso, s.aPrueba(idProceso));
		return ResponseEntity.created(URI.create("/procesos/" + idProceso + "/pruebas/" + creada.idPrueba())).body(creada);
	}

	@PutMapping("/procesos/{idProceso}/pruebas/{idPrueba}")
	public Prueba actualizar(@PathVariable int idProceso, @PathVariable int idPrueba, @Valid @RequestBody PruebaSolicitud s) {
		return servicio.actualizar(idProceso, idPrueba, s.aPrueba(idProceso));
	}

	@DeleteMapping("/procesos/{idProceso}/pruebas/{idPrueba}")
	public ResponseEntity<Void> eliminar(@PathVariable int idProceso, @PathVariable int idPrueba) {
		servicio.eliminar(idProceso, idPrueba);
		return ResponseEntity.noContent().build();
	}

}
