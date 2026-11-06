package co.abogados.gabinete.infraestructura.rest;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.gabinete.aplicacion.ConsultaServicio;
import co.abogados.gabinete.aplicacion.ConsultaServicio.ConsultaConAvisos;
import co.abogados.gabinete.dominio.modelo.Consulta;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Consultas iniciales (F10). Quién puede qué lo decide el gateway (registrar: personal; contratar: quien crea clientes). */
@RestController
public class ConsultasRecurso {

	public record ConsultaSolicitud(LocalDate fecha,
			@NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombre,
			TipoDocumento tipoDocumento, @Size(max = 15) String numeroDocumento,
			@Size(max = 120) String email, @Size(max = 20) String telefono,
			@Size(max = 150) String contraparte, @Size(max = 15) String contraparteDocumento,
			@Size(max = 20) String codigoRama,
			@NotBlank(message = "Describa el asunto") @Size(max = 300) String asunto,
			@Size(max = 2000) String descripcion, Long idAbogado) {
	}

	public record ContratacionSolicitud(@NotBlank(message = "La ciudad es obligatoria") @Size(max = 60) String ciudad,
			@NotNull(message = "Elija la sede") Short idSede, @Size(max = 150) String direccion) {
	}

	public record CierreSolicitud(@NotNull(message = "Indique el resultado") Consulta.Estado estado,
			@NotBlank(message = "Escriba el motivo") @Size(max = 300) String motivo) {
	}

	private final ConsultaServicio servicio;

	public ConsultasRecurso(ConsultaServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/consultas")
	public List<Consulta> listar(@RequestParam(required = false) Consulta.Estado estado) {
		return servicio.listar(estado);
	}

	@GetMapping("/consultas/{id}")
	public ConsultaConAvisos obtener(@PathVariable int id) {
		return servicio.obtener(id);
	}

	@PostMapping("/consultas")
	public ResponseEntity<ConsultaConAvisos> registrar(@Valid @RequestBody ConsultaSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario) {
		ConsultaConAvisos r = servicio.registrar(new Consulta(null, s.fecha(), s.nombre(), s.tipoDocumento(),
				s.numeroDocumento(), s.email(), s.telefono(), s.contraparte(), s.contraparteDocumento(), s.codigoRama(),
				s.asunto(), s.descripcion(), s.idAbogado(), null, null, null, usuario), usuario);
		return ResponseEntity.created(URI.create("/consultas/" + r.consulta().idConsulta())).body(r);
	}

	@PostMapping("/consultas/{id}/contratacion")
	public ConsultaConAvisos contratar(@PathVariable int id, @Valid @RequestBody ContratacionSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario) {
		return servicio.contratar(id, new ConsultaServicio.DatosCliente(s.ciudad(), s.idSede(), s.direccion()), usuario);
	}

	@PostMapping("/consultas/{id}/cierre")
	public ConsultaConAvisos cerrar(@PathVariable int id, @Valid @RequestBody CierreSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario) {
		return servicio.cerrar(id, s.estado(), s.motivo(), usuario);
	}

}
