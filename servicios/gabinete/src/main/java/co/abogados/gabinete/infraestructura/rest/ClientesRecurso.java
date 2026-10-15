package co.abogados.gabinete.infraestructura.rest;

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

import co.abogados.gabinete.aplicacion.ClienteServicio;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.modelo.TipoPersona;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API REST de clientes. Contrato: docs/contratos/gabinete.openapi.yaml. */
@RestController
@RequestMapping("/clientes")
public class ClientesRecurso {

	public record ClienteSolicitud(
			@NotNull(message = "El tipo de persona es obligatorio") TipoPersona tipoPersona,
			@NotNull(message = "El tipo de documento es obligatorio") TipoDocumento tipoDocumento,
			@NotBlank(message = "El número de documento es obligatorio") String numeroDocumento,
			@NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombre,
			@Email(message = "El correo no es válido") @Size(max = 120) String email,
			@Size(max = 20) String telefono,
			@Size(max = 150) String direccion,
			@NotBlank(message = "La ciudad es obligatoria") @Size(max = 60) String ciudad,
			@NotNull(message = "La sede es obligatoria") Short idSede) {

		Cliente aDominio() {
			return new Cliente(null, tipoPersona, tipoDocumento, numeroDocumento, nombre, email, telefono, direccion,
					ciudad, idSede, null);
		}
	}

	private final ClienteServicio servicio;

	public ClientesRecurso(ClienteServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public List<Cliente> listar(@RequestParam(required = false) String q) {
		return servicio.listar(q);
	}

	@GetMapping("/{id}")
	public Cliente obtener(@PathVariable int id) {
		return servicio.obtener(id);
	}

	@PostMapping
	public ResponseEntity<Cliente> crear(@Valid @RequestBody ClienteSolicitud solicitud) {
		Cliente creado = servicio.crear(solicitud.aDominio());
		return ResponseEntity.created(URI.create("/clientes/" + creado.idCliente())).body(creado);
	}

	@PutMapping("/{id}")
	public Cliente actualizar(@PathVariable int id, @Valid @RequestBody ClienteSolicitud solicitud) {
		return servicio.actualizar(id, solicitud.aDominio());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable int id) {
		servicio.eliminar(id);
		return ResponseEntity.noContent().build();
	}

}
