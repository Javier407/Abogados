package co.abogados.identidad.infraestructura;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.identidad.aplicacion.MfaServicio;
import co.abogados.identidad.dominio.Sesion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Verificación en dos pasos (F13).
 * - /auth/login/mfa*: públicas (aún no hay sesión); se identifican con el desafío que entregó /auth/login.
 * - /auth/mfa*: la persona del token configura la suya.
 * - /usuarios/mfa y /usuarios/{id}/mfa/reinicio: solo el administrador.
 */
@RestController
public class MfaRecurso {

	public record Codigo(@NotBlank(message = "Escriba el código") @Size(max = 20) String codigo) {
	}

	public record ConDesafio(@NotBlank(message = "Vuelva a iniciar sesión") @Size(max = 300) String desafio,
			@Size(max = 20) String codigo) {
	}

	public record Clave(@NotBlank(message = "Escriba su contraseña") @Size(max = 200) String clave) {
	}

	public record Desactivacion(@NotBlank(message = "Escriba su contraseña") @Size(max = 200) String clave,
			@NotBlank(message = "Escriba el código") @Size(max = 20) String codigo) {
	}

	public record Codigos(List<String> codigos) {
	}

	private final MfaServicio servicio;
	private final LimiteIntentosPorIp limite;
	private final boolean cookieSegura;

	public MfaRecurso(MfaServicio servicio, LimiteIntentosPorIp limite,
			@Value("${sesiones.cookie-segura:true}") boolean cookieSegura) {
		this.servicio = servicio;
		this.limite = limite;
		this.cookieSegura = cookieSegura;
	}

	// ------------------------------------------------------------------ segundo paso del inicio de sesión

	@PostMapping("/auth/login/mfa")
	public ResponseEntity<?> completar(@Valid @RequestBody ConDesafio d, HttpServletRequest peticion) {
		String ip = AuthRecurso.ip(peticion);
		if (!limite.permitir(ip)) {
			return demasiados();
		}
		return conSesion(servicio.completar(d.desafio(), d.codigo(), ip), null);
	}

	@PostMapping("/auth/login/mfa/inicio")
	public MfaServicio.Inicio iniciarAlEntrar(@Valid @RequestBody ConDesafio d) {
		return servicio.iniciarConDesafio(d.desafio());
	}

	@PostMapping("/auth/login/mfa/activacion")
	public ResponseEntity<?> activarAlEntrar(@Valid @RequestBody ConDesafio d, HttpServletRequest peticion) {
		String ip = AuthRecurso.ip(peticion);
		if (!limite.permitir(ip)) {
			return demasiados();
		}
		MfaServicio.Activacion a = servicio.activarConDesafio(d.desafio(), d.codigo(), ip);
		return conSesion(a.sesion(), a.codigos());
	}

	// ------------------------------------------------------------------ la propia cuenta

	@GetMapping("/auth/mfa")
	public MfaServicio.Estado estado(@RequestHeader("X-Usuario-Id") long idUsuario) {
		return servicio.estado(idUsuario);
	}

	@PostMapping("/auth/mfa/inicio")
	public MfaServicio.Inicio iniciar(@RequestHeader("X-Usuario-Id") long idUsuario) {
		return servicio.iniciar(idUsuario);
	}

	@PostMapping("/auth/mfa/activacion")
	public Codigos activar(@RequestHeader("X-Usuario-Id") long idUsuario, @Valid @RequestBody Codigo c) {
		return new Codigos(servicio.activar(idUsuario, c.codigo()));
	}

	@PostMapping("/auth/mfa/codigos")
	public Codigos regenerar(@RequestHeader("X-Usuario-Id") long idUsuario, @Valid @RequestBody Clave c) {
		return new Codigos(servicio.regenerarCodigos(idUsuario, c.clave()));
	}

	@PostMapping("/auth/mfa/desactivacion")
	public ResponseEntity<Void> desactivar(@RequestHeader("X-Usuario-Id") long idUsuario,
			@Valid @RequestBody Desactivacion d) {
		servicio.desactivar(idUsuario, d.clave(), d.codigo());
		return ResponseEntity.noContent().build();
	}

	// ------------------------------------------------------------------ administración

	@GetMapping("/usuarios/mfa")
	public Set<Long> conMfa(@RequestHeader("X-Usuario-Roles") String roles) {
		exigirAdmin(roles);
		return servicio.conMfa();
	}

	@PostMapping("/usuarios/{id}/mfa/reinicio")
	public ResponseEntity<Void> reiniciar(@PathVariable long id, @RequestHeader("X-Usuario-Id") long admin,
			@RequestHeader("X-Usuario-Roles") String roles) {
		exigirAdmin(roles);
		servicio.reiniciar(admin, id);
		return ResponseEntity.noContent().build();
	}

	// ------------------------------------------------------------------

	private ResponseEntity<AuthRecurso.SesionRespuesta> conSesion(Sesion s, List<String> codigos) {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, AuthRecurso.cookieDe(s, cookieSegura).toString())
				.body(AuthRecurso.SesionRespuesta.de(s, codigos));
	}

	private static ResponseEntity<Map<String, String>> demasiados() {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header("Retry-After", String.valueOf(LimiteIntentosPorIp.VENTANA.toSeconds()))
				.body(Map.of("error", "Demasiados intentos desde esta dirección. Espere unos minutos."));
	}

	private static void exigirAdmin(String roles) {
		if (roles == null || Arrays.stream(roles.split(",")).map(String::strip).noneMatch("ADMIN"::equals)) {
			throw new UsuariosRecurso.SinPermisoException();
		}
	}

}
