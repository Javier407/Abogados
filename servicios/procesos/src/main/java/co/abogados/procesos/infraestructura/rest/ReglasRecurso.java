package co.abogados.procesos.infraestructura.rest;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.ReglaServicio;
import co.abogados.procesos.dominio.excepciones.AccesoDenegadoException;
import co.abogados.procesos.dominio.modelo.CambioRegla;
import co.abogados.procesos.dominio.modelo.ReglaDatos;
import co.abogados.procesos.dominio.modelo.ReglaVista;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reglas de términos (F12). Verlas: el personal. Crearlas, editarlas, validarlas y cambiar los días de contestación:
 * solo un SOCIO (es un juicio jurídico). El gateway ya lo exige; aquí se revisa otra vez con X-Usuario-Roles.
 */
@RestController
public class ReglasRecurso {

	public record ReglaSolicitud(
			@NotBlank(message = "Elija AUTO o SENTENCIA") String tipoProvidencia,
			String codigoRama,
			Short idTipo,
			@NotBlank(message = "Escriba el patrón") @Size(max = 200) String patron,
			String lado,
			@NotBlank(message = "Escriba la descripción") @Size(max = 150) String descripcion,
			@Min(value = 1, message = "Mínimo 1 día") @Max(value = 365, message = "Máximo 365 días") Integer dias,
			@NotBlank(message = "Escriba la norma") @Size(max = 120) String norma,
			Integer prioridad,
			Boolean activa) {

		ReglaDatos datos() {
			return new ReglaDatos(tipoProvidencia.strip().toUpperCase(), vacioANull(codigoRama), idTipo, patron.strip(),
					vacioANull(lado), descripcion.strip(), dias, norma.strip(), prioridad == null ? 1 : prioridad,
					activa == null || activa);
		}

		private static String vacioANull(String s) {
			return s == null || s.isBlank() ? null : s.strip().toUpperCase();
		}
	}

	public record ContestacionSolicitud(Integer dias) {
	}

	private final ReglaServicio servicio;

	public ReglasRecurso(ReglaServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/reglas-termino")
	public List<ReglaVista> listar() {
		return servicio.listar();
	}

	@GetMapping("/reglas-termino/{idRegla}/historial")
	public List<CambioRegla> historial(@PathVariable int idRegla) {
		return servicio.historial(idRegla);
	}

	@GetMapping("/reglas-termino/prueba")
	public ReglaServicio.Prueba probar(@RequestParam String patron, @RequestParam String clase) {
		return servicio.probar(patron, clase);
	}

	@PostMapping("/reglas-termino")
	public ResponseEntity<ReglaVista> crear(@Valid @RequestBody ReglaSolicitud s, @RequestHeader("X-Usuario-Id") long usuario,
			@RequestHeader(name = "X-Usuario-Roles", required = false) String roles) {
		socio(roles);
		ReglaVista r = servicio.crear(s.datos(), usuario);
		return ResponseEntity.created(URI.create("/reglas-termino/" + r.idRegla())).body(r);
	}

	@PutMapping("/reglas-termino/{idRegla}")
	public ReglaVista actualizar(@PathVariable int idRegla, @Valid @RequestBody ReglaSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario,
			@RequestHeader(name = "X-Usuario-Roles", required = false) String roles) {
		socio(roles);
		return servicio.actualizar(idRegla, s.datos(), usuario);
	}

	@PostMapping("/reglas-termino/{idRegla}/validacion")
	public ReglaVista validar(@PathVariable int idRegla, @RequestHeader("X-Usuario-Id") long usuario,
			@RequestHeader(name = "X-Usuario-Roles", required = false) String roles) {
		socio(roles);
		return servicio.validar(idRegla, usuario);
	}

	@PutMapping("/catalogos/tipos/{idTipo}/contestacion")
	public Map<String, Integer> contestacion(@PathVariable short idTipo, @RequestBody ContestacionSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario,
			@RequestHeader(name = "X-Usuario-Roles", required = false) String roles) {
		socio(roles);
		return Map.of("reglasSinValidar", servicio.cambiarContestacion(idTipo, s.dias(), usuario));
	}

	private static void socio(String roles) {
		if (roles == null || Arrays.stream(roles.split(",")).map(String::strip).noneMatch("SOCIO"::equals)) {
			throw new AccesoDenegadoException("Solo un socio cambia o valida las reglas de términos");
		}
	}

}
