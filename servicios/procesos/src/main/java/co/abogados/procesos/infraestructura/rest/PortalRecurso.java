package co.abogados.procesos.infraestructura.rest;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.PortalServicio;
import co.abogados.procesos.dominio.modelo.Portal;
import co.abogados.procesos.dominio.modelo.Portal.ResumenPublicado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Portal del cliente (/portal/procesos, solo rol CLIENTE) y resumen para el cliente que publica el abogado
 * (/procesos/{id}/resumen-cliente). X-Cliente-Id y X-Usuario-Roles salen del token (FiltroToken), no de la petición.
 */
@RestController
public class PortalRecurso {

	public record ResumenSolicitud(@NotBlank(message = "Escriba el resumen") String texto) {
	}

	private final PortalServicio servicio;

	public PortalRecurso(PortalServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/portal/procesos")
	public ResponseEntity<?> procesos(@RequestHeader(name = "X-Cliente-Id", required = false) Integer cliente,
			@RequestHeader(name = "X-Usuario-Roles", required = false) String roles) {
		if (!esCliente(cliente, roles)) {
			return prohibido();
		}
		List<Portal.ProcesoCliente> lista = servicio.procesosDe(cliente);
		return ResponseEntity.ok(lista);
	}

	@GetMapping("/portal/procesos/{idProceso}")
	public ResponseEntity<?> detalle(@PathVariable int idProceso,
			@RequestHeader(name = "X-Cliente-Id", required = false) Integer cliente,
			@RequestHeader(name = "X-Usuario-Roles", required = false) String roles) {
		if (!esCliente(cliente, roles)) {
			return prohibido();
		}
		return ResponseEntity.ok(servicio.detalle(cliente, idProceso));
	}

	/** Último resumen publicado (204 si aún no hay). */
	@GetMapping("/procesos/{idProceso}/resumen-cliente")
	public ResponseEntity<ResumenPublicado> resumen(@PathVariable int idProceso) {
		return servicio.ultimoResumen(idProceso).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
	}

	@PostMapping("/procesos/{idProceso}/resumen-cliente")
	public ResponseEntity<ResumenPublicado> publicar(@PathVariable int idProceso,
			@Valid @RequestBody ResumenSolicitud s, @RequestHeader("X-Usuario-Id") long abogado) {
		ResumenPublicado r = servicio.publicarResumen(idProceso, s.texto(), abogado);
		return ResponseEntity.created(URI.create("/procesos/" + idProceso + "/resumen-cliente")).body(r);
	}

	private static boolean esCliente(Integer cliente, String roles) {
		return cliente != null && roles != null && Arrays.asList(roles.split(",")).contains("CLIENTE");
	}

	private static ResponseEntity<Map<String, String>> prohibido() {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "El portal es solo para clientes"));
	}

}
