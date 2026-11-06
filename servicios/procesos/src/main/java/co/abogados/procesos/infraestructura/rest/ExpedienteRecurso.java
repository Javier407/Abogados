package co.abogados.procesos.infraestructura.rest;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.ExpedienteServicio;
import co.abogados.procesos.dominio.modelo.Actuacion;
import co.abogados.procesos.dominio.modelo.EventoExpediente;
import co.abogados.procesos.dominio.modelo.Instancia;
import co.abogados.procesos.dominio.modelo.Parte;
import co.abogados.procesos.dominio.modelo.ParteRegistrada;
import co.abogados.procesos.dominio.modelo.RolParte;
import co.abogados.procesos.dominio.modelo.TipoActuacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Expediente de un proceso: partes, actuaciones de la firma, línea de tiempo y otras instancias.
 * Quién registra cada actuación sale de la cabecera X-Usuario-Id, que el gateway toma del token.
 */
@RestController
public class ExpedienteRecurso {

	public record ParteSolicitud(
			@NotNull(message = "Indique el rol de la parte") RolParte rol,
			String tipoDocumento,
			@Size(max = 15) String numeroDocumento,
			@NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombre,
			@Size(max = 120) String apoderado) {

		Parte aParte(int idProceso) {
			return new Parte(null, idProceso, rol, tipoDocumento, numeroDocumento, nombre, false, apoderado);
		}
	}

	public record ActuacionSolicitud(
			@NotNull(message = "Indique el tipo de actuación") TipoActuacion tipo,
			@NotNull(message = "La fecha es obligatoria") LocalDate fecha,
			@NotBlank(message = "Describa la actuación") @Size(max = 200) String descripcion,
			Long idPersona,
			Integer idProvidencia,
			@Size(max = 300) String documentoUrl,
			Integer idTerminoCumplido,
			LocalTime hora,
			@Size(max = 200) String lugar) {
	}

	public record Expediente(List<EventoExpediente> lineaDeTiempo, List<Instancia> otrasInstancias) {
	}

	private final ExpedienteServicio servicio;

	public ExpedienteRecurso(ExpedienteServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/procesos/{idProceso}/expediente")
	public Expediente expediente(@PathVariable int idProceso) {
		return new Expediente(servicio.lineaDeTiempo(idProceso), servicio.otrasInstancias(idProceso));
	}

	// ----------------------------------------------------------------- partes

	@GetMapping("/procesos/{idProceso}/partes")
	public List<Parte> partes(@PathVariable int idProceso) {
		return servicio.partes(idProceso);
	}

	@PostMapping("/procesos/{idProceso}/partes")
	public ResponseEntity<ParteRegistrada> agregarParte(@PathVariable int idProceso, @Valid @RequestBody ParteSolicitud s) {
		ParteRegistrada r = servicio.agregarParte(idProceso, s.aParte(idProceso));
		return ResponseEntity.created(URI.create("/procesos/" + idProceso + "/partes/" + r.parte().idParte())).body(r);
	}

	@PutMapping("/procesos/{idProceso}/partes/{idParte}")
	public ParteRegistrada editarParte(@PathVariable int idProceso, @PathVariable int idParte,
			@Valid @RequestBody ParteSolicitud s) {
		return servicio.editarParte(idProceso, idParte, s.aParte(idProceso));
	}

	@DeleteMapping("/procesos/{idProceso}/partes/{idParte}")
	public ResponseEntity<Void> eliminarParte(@PathVariable int idProceso, @PathVariable int idParte) {
		servicio.eliminarParte(idProceso, idParte);
		return ResponseEntity.noContent().build();
	}

	// ----------------------------------------------------------------- actuaciones

	@GetMapping("/procesos/{idProceso}/actuaciones")
	public List<Actuacion> actuaciones(@PathVariable int idProceso) {
		return servicio.actuaciones(idProceso);
	}

	@PostMapping("/procesos/{idProceso}/actuaciones")
	public ResponseEntity<Actuacion> registrarActuacion(@PathVariable int idProceso,
			@Valid @RequestBody ActuacionSolicitud s,
			@RequestHeader(value = "X-Usuario-Id", required = false) Long usuario) {
		Actuacion datos = new Actuacion(null, idProceso, s.fecha(), s.tipo(), s.descripcion(), s.idPersona(),
				s.idProvidencia(), s.documentoUrl(), usuario, s.hora(), s.lugar());
		Actuacion creada = servicio.registrarActuacion(idProceso, datos, s.idTerminoCumplido(), usuario);
		return ResponseEntity.created(URI.create("/procesos/" + idProceso + "/actuaciones/" + creada.idActuacion()))
				.body(creada);
	}

	@DeleteMapping("/procesos/{idProceso}/actuaciones/{idActuacion}")
	public ResponseEntity<Void> eliminarActuacion(@PathVariable int idProceso, @PathVariable int idActuacion) {
		servicio.eliminarActuacion(idProceso, idActuacion);
		return ResponseEntity.noContent().build();
	}

}
