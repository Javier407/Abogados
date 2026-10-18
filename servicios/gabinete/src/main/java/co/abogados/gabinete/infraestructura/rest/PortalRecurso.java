package co.abogados.gabinete.infraestructura.rest;

import java.util.Arrays;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.gabinete.aplicacion.PortalFinanzasServicio;

/** Portal del cliente: sus honorarios, pagos y gastos por reembolsar. X-Cliente-Id sale del token (FiltroToken). */
@RestController
public class PortalRecurso {

	private final PortalFinanzasServicio servicio;

	public PortalRecurso(PortalFinanzasServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/portal/finanzas")
	public ResponseEntity<?> finanzas(@RequestHeader(name = "X-Cliente-Id", required = false) Integer cliente,
			@RequestHeader(name = "X-Usuario-Roles", required = false) String roles) {
		if (cliente == null || roles == null || !Arrays.asList(roles.split(",")).contains("CLIENTE")) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "El portal es solo para clientes"));
		}
		return ResponseEntity.ok(servicio.deCliente(cliente));
	}

}
