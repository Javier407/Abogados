package co.abogados.identidad.infraestructura;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.identidad.aplicacion.CuentasServicio;
import co.abogados.identidad.dominio.Cuenta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Administración de cuentas, solo para ADMIN. El gateway ya lo exige; aquí se comprueba otra vez con la
 * cabecera X-Usuario-Roles (que el gateway pone desde el token) por si alguien llegara al servicio por la red
 * interna.
 */
@RestController
public class UsuariosRecurso {

	public record CuentaSolicitud(
			@NotNull(message = "El documento es obligatorio") @Positive Long idUsuario,
			@NotBlank(message = "El usuario es obligatorio") @Size(max = 30) String usuario,
			@NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String nombre,
			@NotBlank(message = "El correo es obligatorio") @Size(max = 120) String email,
			@NotEmpty(message = "Asigne al menos un rol") Set<String> roles,
			@NotBlank(message = "Escriba la contraseña temporal") @Size(max = 72) String claveTemporal,
			@Positive Integer idCliente) {
	}

	public record CambioSolicitud(
			@NotBlank(message = "El usuario es obligatorio") @Size(max = 30) String usuario,
			@NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String nombre,
			@NotBlank(message = "El correo es obligatorio") @Size(max = 120) String email,
			@NotEmpty(message = "Asigne al menos un rol") Set<String> roles,
			boolean activo,
			@Positive Integer idCliente) {
	}

	public record ClaveSolicitud(@NotBlank(message = "Escriba la contraseña temporal") @Size(max = 72) String claveTemporal) {
	}

	static class SinPermisoException extends RuntimeException {
		SinPermisoException() {
			super("Solo el administrador puede gestionar las cuentas");
		}
	}

	private final CuentasServicio servicio;

	public UsuariosRecurso(CuentasServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/usuarios")
	public List<Cuenta> listar(@RequestHeader("X-Usuario-Roles") String roles) {
		exigirAdmin(roles);
		return servicio.listar();
	}

	@PostMapping("/usuarios")
	public ResponseEntity<Cuenta> crear(@RequestHeader("X-Usuario-Id") long admin,
			@RequestHeader("X-Usuario-Roles") String roles, @Valid @RequestBody CuentaSolicitud s) {
		exigirAdmin(roles);
		Cuenta creada = servicio.crear(new CuentasServicio.NuevaCuenta(s.idUsuario(), s.usuario(), s.nombre(), s.email(),
				s.roles(), s.claveTemporal(), s.idCliente()), admin);
		return ResponseEntity.created(URI.create("/usuarios/" + creada.idUsuario())).body(creada);
	}

	@PutMapping("/usuarios/{id}")
	public Cuenta actualizar(@PathVariable long id, @RequestHeader("X-Usuario-Id") long admin,
			@RequestHeader("X-Usuario-Roles") String roles, @Valid @RequestBody CambioSolicitud s) {
		exigirAdmin(roles);
		return servicio.actualizar(id, new CuentasServicio.CambioCuenta(s.usuario(), s.nombre(), s.email(), s.roles(),
				s.activo(), s.idCliente()), admin);
	}

	@PostMapping("/usuarios/{id}/clave")
	public Cuenta restablecerClave(@PathVariable long id, @RequestHeader("X-Usuario-Id") long admin,
			@RequestHeader("X-Usuario-Roles") String roles, @Valid @RequestBody ClaveSolicitud s) {
		exigirAdmin(roles);
		return servicio.restablecerClave(id, s.claveTemporal(), admin);
	}

	@PostMapping("/usuarios/{id}/desbloqueo")
	public Cuenta desbloquear(@PathVariable long id, @RequestHeader("X-Usuario-Id") long admin,
			@RequestHeader("X-Usuario-Roles") String roles) {
		exigirAdmin(roles);
		return servicio.desbloquear(id, admin);
	}

	private static void exigirAdmin(String roles) {
		if (roles == null || Arrays.stream(roles.split(",")).map(String::strip).noneMatch("ADMIN"::equals)) {
			throw new SinPermisoException();
		}
	}

}
