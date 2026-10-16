package co.abogados.gabinete.infraestructura.rest;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.gabinete.aplicacion.PeritoServicio;
import co.abogados.gabinete.dominio.modelo.Perito;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Peritos de la firma (no se borran: las pruebas periciales los referencian). */
@RestController
@RequestMapping("/peritos")
public class PeritosRecurso {

	public record PeritoSolicitud(
			@NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String nombre,
			@NotBlank(message = "La especialidad es obligatoria") @Size(max = 80) String especialidad,
			@NotBlank(message = "La profesión es obligatoria") @Size(max = 80) String profesion,
			@Size(max = 30) String registro,
			@Size(max = 20) String telefono,
			@Email(message = "El correo no es válido") @Size(max = 120) String email,
			@NotBlank(message = "La ciudad es obligatoria") @Size(max = 60) String ciudad,
			@PositiveOrZero(message = "La tarifa no puede ser negativa") BigDecimal tarifaDictamen,
			@Min(value = 1, message = "La calificación va de 1 a 5") @Max(value = 5, message = "La calificación va de 1 a 5")
			Short calificacion) {

		Perito aDominio() {
			return new Perito(null, nombre, especialidad, profesion, registro, telefono, email, ciudad, tarifaDictamen,
					calificacion);
		}
	}

	private final PeritoServicio servicio;

	public PeritosRecurso(PeritoServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public List<Perito> listar(@RequestParam(required = false) String q) {
		return servicio.listar(q);
	}

	@GetMapping("/{id}")
	public Perito obtener(@PathVariable int id) {
		return servicio.obtener(id);
	}

	@PostMapping
	public ResponseEntity<Perito> crear(@Valid @RequestBody PeritoSolicitud s) {
		Perito creado = servicio.crear(s.aDominio());
		return ResponseEntity.created(URI.create("/peritos/" + creado.idPerito())).body(creado);
	}

	@PutMapping("/{id}")
	public Perito actualizar(@PathVariable int id, @Valid @RequestBody PeritoSolicitud s) {
		return servicio.actualizar(id, s.aDominio());
	}

}
