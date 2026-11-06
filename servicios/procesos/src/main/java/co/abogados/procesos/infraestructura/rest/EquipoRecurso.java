package co.abogados.procesos.infraestructura.rest;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.EquipoServicio;
import co.abogados.procesos.dominio.modelo.Asignacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Equipo del proceso (F11). Ver lo puede el personal; cambiarlo, los abogados (lo exige el gateway). */
@RestController
public class EquipoRecurso {

	public record IntegranteSolicitud(@NotNull(message = "Elija la persona") @Positive Long idPersona,
			@NotNull(message = "Elija el rol") Asignacion.Rol rol) {
	}

	private final EquipoServicio servicio;

	public EquipoRecurso(EquipoServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/procesos/{idProceso}/asignaciones")
	public List<Asignacion> equipo(@PathVariable int idProceso) {
		return servicio.equipo(idProceso);
	}

	@PostMapping("/procesos/{idProceso}/asignaciones")
	public ResponseEntity<Asignacion> agregar(@PathVariable int idProceso, @Valid @RequestBody IntegranteSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario) {
		Asignacion a = servicio.agregar(idProceso, s.idPersona(), s.rol(), usuario);
		return ResponseEntity.created(URI.create("/procesos/" + idProceso + "/asignaciones/" + a.idPersona())).body(a);
	}

	@DeleteMapping("/procesos/{idProceso}/asignaciones/{idPersona}")
	public ResponseEntity<Void> quitar(@PathVariable int idProceso, @PathVariable long idPersona,
			@RequestHeader("X-Usuario-Id") long usuario) {
		servicio.quitar(idProceso, idPersona, usuario);
		return ResponseEntity.noContent().build();
	}

}
