package co.abogados.procesos.infraestructura.rest;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.ProvidenciaServicio;
import co.abogados.procesos.aplicacion.SugerenciaServicio;
import co.abogados.procesos.dominio.modelo.NuevaProvidencia;
import co.abogados.procesos.dominio.modelo.ProvidenciaVista;
import co.abogados.procesos.dominio.modelo.SugerenciaTermino;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Autos y sentencias del expediente, de la más reciente a la más antigua; también el registro manual. */
@RestController
public class ProvidenciasRecurso {

	public record ProvidenciaSolicitud(
			@NotBlank(message = "Indique si es AUTO o SENTENCIA") String tipo,
			@NotBlank(message = "Indique qué decidió el juez") @Size(max = 120) String clase,
			@NotNull(message = "La fecha de la providencia es obligatoria") LocalDate fechaProvidencia,
			@NotNull(message = "La fecha del estado es obligatoria") LocalDate fechaEstado,
			@Size(max = 300) String decision,
			String sentido) {
	}

	private final ProvidenciaServicio servicio;
	private final SugerenciaServicio sugerencias;

	public ProvidenciasRecurso(ProvidenciaServicio servicio, SugerenciaServicio sugerencias) {
		this.servicio = servicio;
		this.sugerencias = sugerencias;
	}

	@GetMapping("/procesos/{idProceso}/providencias")
	public List<ProvidenciaVista> deProceso(@PathVariable int idProceso) {
		return servicio.deProceso(idProceso);
	}

	/** Términos que abre la providencia según las reglas de referencia o su propio texto; no crea nada. */
	@GetMapping("/procesos/{idProceso}/providencias/{idProvidencia}/sugerencias")
	public List<SugerenciaTermino> sugerencias(@PathVariable int idProceso, @PathVariable int idProvidencia) {
		return sugerencias.sugerencias(idProceso, idProvidencia);
	}

	@PostMapping("/procesos/{idProceso}/providencias")
	public ResponseEntity<ProvidenciaVista> registrar(@PathVariable int idProceso,
			@Valid @RequestBody ProvidenciaSolicitud s,
			@RequestHeader(value = "X-Usuario-Id", required = false) Long usuario) {
		ProvidenciaVista creada = servicio.registrarManual(idProceso, new NuevaProvidencia(s.tipo(), s.clase(),
				s.fechaProvidencia(), s.fechaEstado(), s.decision(), s.sentido()), usuario);
		return ResponseEntity.created(URI.create("/procesos/" + idProceso + "/providencias/" + creada.idProvidencia()))
				.body(creada);
	}

}
