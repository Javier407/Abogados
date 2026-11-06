package co.abogados.gabinete.infraestructura.rest;

import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.gabinete.aplicacion.AbogadoServicio;
import co.abogados.gabinete.dominio.modelo.Abogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * API REST de abogados. DELETE no borra: desactiva al abogado (sigue apareciendo en sus procesos).
 * Contrato: docs/contratos/gabinete.openapi.yaml.
 */
@RestController
@RequestMapping("/abogados")
public class AbogadosRecurso {

	public record AbogadoSolicitud(
			@NotNull(message = "La cédula es obligatoria") @Positive Long idPersona,
			@NotBlank(message = "Los nombres son obligatorios") @Size(max = 60) String nombres,
			@NotBlank(message = "Los apellidos son obligatorios") @Size(max = 60) String apellidos,
			@NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") String email,
			@Size(max = 20) String telefono,
			@NotNull(message = "El cargo es obligatorio") Short idCargo,
			@NotNull(message = "La sede es obligatoria") Short idSede,
			Short idArea,
			@NotBlank(message = "La tarjeta profesional es obligatoria") String tarjetaProfesional,
			@PositiveOrZero(message = "La tarifa no puede ser negativa") BigDecimal tarifaHora,
			@PastOrPresent(message = "La fecha de ingreso no puede ser futura") LocalDate fechaIngreso,
			Boolean activo) {

		Abogado aDominio() {
			return new Abogado(idPersona, nombres, apellidos, email, telefono, idCargo, null, idSede, null, idArea,
					null, tarjetaProfesional, tarifaHora, fechaIngreso == null ? LocalDate.now() : fechaIngreso,
					activo == null || activo);
		}
	}

	private final AbogadoServicio servicio;

	public AbogadosRecurso(AbogadoServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public List<Abogado> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
		return servicio.listar(soloActivos);
	}

	@GetMapping("/{id}")
	public Abogado obtener(@PathVariable long id) {
		return servicio.obtener(id);
	}

	@PostMapping
	public ResponseEntity<Abogado> crear(@Valid @RequestBody AbogadoSolicitud solicitud) {
		Abogado creado = servicio.crear(solicitud.aDominio());
		return ResponseEntity.created(URI.create("/abogados/" + creado.idPersona())).body(creado);
	}

	@PutMapping("/{id}")
	public Abogado actualizar(@PathVariable long id, @Valid @RequestBody AbogadoSolicitud solicitud) {
		return servicio.actualizar(id, solicitud.aDominio());
	}

	@DeleteMapping("/{id}")
	public Abogado desactivar(@PathVariable long id) {
		return servicio.desactivar(id);
	}

}
