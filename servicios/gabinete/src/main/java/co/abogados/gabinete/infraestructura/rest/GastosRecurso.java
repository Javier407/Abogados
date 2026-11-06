package co.abogados.gabinete.infraestructura.rest;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.gabinete.aplicacion.GastoServicio;
import co.abogados.gabinete.dominio.modelo.CarteraCliente;
import co.abogados.gabinete.dominio.modelo.Gasto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Gastos procesales y cartera por cliente. */
@RestController
public class GastosRecurso {

	public record GastoSolicitud(
			@NotNull(message = "El proceso es obligatorio") @Positive Integer idProceso,
			@NotNull(message = "La fecha es obligatoria") LocalDate fecha,
			@NotBlank(message = "Describa el gasto") @Size(max = 150) String concepto,
			@NotNull(message = "El valor es obligatorio") @Positive(message = "El valor debe ser mayor que cero") BigDecimal valor,
			@NotNull(message = "Indique quién lo pagó") Gasto.PagadoPor pagadoPor,
			boolean reembolsable,
			@Size(max = 300) String soporteUrl) {
	}

	public record ReembolsoSolicitud(LocalDate fecha) {
	}

	private final GastoServicio servicio;

	public GastosRecurso(GastoServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/gastos")
	public List<Gasto> listar(@RequestParam(required = false) Integer idProceso, @RequestParam(required = false) Integer idCliente) {
		return servicio.listar(idProceso, idCliente);
	}

	@PostMapping("/gastos")
	public ResponseEntity<Gasto> registrar(@Valid @RequestBody GastoSolicitud s,
			@RequestHeader(value = "X-Usuario-Id", required = false) Long usuario) {
		// El cliente lo pone el servicio a partir del proceso; aquí va 0 provisional.
		Gasto g = servicio.registrar(new Gasto(null, s.idProceso(), 0, s.fecha(), s.concepto(), s.valor(), s.pagadoPor(),
				s.reembolsable(), null, s.soporteUrl(), usuario), usuario);
		return ResponseEntity.created(URI.create("/gastos/" + g.idGasto())).body(g);
	}

	@PostMapping("/gastos/{id}/reembolso")
	public Gasto reembolsar(@PathVariable int id, @RequestBody(required = false) ReembolsoSolicitud s) {
		return servicio.marcarReembolsado(id, s == null ? null : s.fecha());
	}

	@DeleteMapping("/gastos/{id}")
	public ResponseEntity<Void> eliminar(@PathVariable int id) {
		servicio.eliminar(id);
		return ResponseEntity.noContent().build();
	}

	/** Lo que debe cada cliente: saldo de honorarios y gastos por reembolsar. */
	@GetMapping("/honorarios/cartera")
	public List<CarteraCliente> cartera() {
		return servicio.cartera();
	}

}
