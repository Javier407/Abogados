package co.abogados.procesos.infraestructura.rest;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.TerminoServicio;
import co.abogados.procesos.dominio.modelo.FiltroTerminos;
import co.abogados.procesos.dominio.modelo.SituacionTermino;
import co.abogados.procesos.dominio.modelo.TerminoVista;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Términos: agenda, registro por proceso, cumplimiento y revisión de vencimientos. */
@RestController
public class TerminosRecurso {

	public record TerminoSolicitud(
			@NotBlank(message = "La descripción es obligatoria") @Size(max = 150) String descripcion,
			@NotNull(message = "La fecha de notificación es obligatoria") LocalDate fechaInicio,
			@NotNull(message = "Los días hábiles son obligatorios")
			@Min(value = 1, message = "Mínimo 1 día hábil") @Max(value = 365, message = "Máximo 365 días hábiles") Integer diasHabiles,
			Integer idProvidencia,
			Long idResponsable) {
	}

	private final TerminoServicio servicio;

	public TerminosRecurso(TerminoServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/terminos")
	public List<TerminoVista> listar(
			@RequestParam(required = false) SituacionTermino situacion,
			@RequestParam(required = false) Long idResponsable,
			@RequestParam(required = false) Integer idProceso,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return servicio.listar(new FiltroTerminos(situacion, idResponsable, idProceso, desde, hasta));
	}

	@GetMapping("/terminos/{id}")
	public TerminoVista obtener(@PathVariable int id) {
		return servicio.obtener(id);
	}

	@PostMapping("/procesos/{idProceso}/terminos")
	public ResponseEntity<TerminoVista> crear(@PathVariable int idProceso,
			@Valid @RequestBody TerminoSolicitud s) {
		TerminoVista creado = servicio.crear(idProceso, new TerminoServicio.NuevoTermino(s.descripcion(),
				s.fechaInicio(), s.diasHabiles(), s.idProvidencia(), s.idResponsable()));
		return ResponseEntity.created(URI.create("/terminos/" + creado.idTermino())).body(creado);
	}

	@PostMapping("/terminos/{id}/cumplir")
	public TerminoVista cumplir(@PathVariable int id) {
		return servicio.cumplir(id);
	}

	@DeleteMapping("/terminos/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable int id) {
		servicio.eliminar(id);
		return ResponseEntity.noContent().build();
	}

	/** Ejecuta ya la revisión que la tarea programada hace cada mañana. */
	@PostMapping("/terminos/revision")
	public TerminoServicio.Revision revisar() {
		return servicio.revisarVencimientos();
	}

}
