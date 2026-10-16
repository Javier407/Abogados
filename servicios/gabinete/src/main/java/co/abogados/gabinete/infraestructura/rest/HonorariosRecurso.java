package co.abogados.gabinete.infraestructura.rest;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.gabinete.aplicacion.HonorarioServicio;
import co.abogados.gabinete.dominio.modelo.EstadoHonorario;
import co.abogados.gabinete.dominio.modelo.FormaCobro;
import co.abogados.gabinete.dominio.modelo.Honorario;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.MedioPago;
import co.abogados.gabinete.dominio.modelo.Pago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Acuerdos de honorarios y pagos. Contrato: docs/contratos/gabinete.openapi.yaml. */
@RestController
@RequestMapping("/honorarios")
public class HonorariosRecurso {

	public record HonorarioSolicitud(
			@NotNull(message = "El proceso es obligatorio") @Positive Integer idProceso,
			@NotNull(message = "El cliente es obligatorio") @Positive Integer idCliente,
			Short idTarifa,
			@NotNull(message = "La forma de cobro es obligatoria") FormaCobro formaCobro,
			BigDecimal valorFijo,
			BigDecimal porcentaje,
			BigDecimal valorEstimado,
			@NotNull(message = "La fecha del acuerdo es obligatoria") LocalDate fechaPacto) {

		Honorario aDominio() {
			return new Honorario(null, idProceso, idCliente, idTarifa, formaCobro, valorFijo, porcentaje, valorEstimado,
					fechaPacto, EstadoHonorario.VIGENTE);
		}
	}

	public record PagoSolicitud(
			@NotNull(message = "La fecha es obligatoria") LocalDate fecha,
			@NotNull(message = "El valor es obligatorio") @Positive(message = "El valor debe ser mayor que cero") BigDecimal valor,
			@NotNull(message = "El medio de pago es obligatorio") MedioPago medio,
			@Size(max = 40) String referencia) {
	}

	public record CambioEstado(@NotNull(message = "El estado es obligatorio") EstadoHonorario estado) {
	}

	private final HonorarioServicio servicio;

	public HonorariosRecurso(HonorarioServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public List<HonorarioVista> listar(@RequestParam(required = false) Integer idCliente,
			@RequestParam(required = false) Integer idProceso, @RequestParam(required = false) EstadoHonorario estado) {
		return servicio.listar(idCliente, idProceso, estado);
	}

	@GetMapping("/{id}")
	public HonorarioVista obtener(@PathVariable int id) {
		return servicio.obtener(id);
	}

	@PostMapping
	public ResponseEntity<HonorarioVista> crear(@Valid @RequestBody HonorarioSolicitud s) {
		HonorarioVista creado = servicio.crear(s.aDominio());
		return ResponseEntity.created(URI.create("/honorarios/" + creado.idHonorario())).body(creado);
	}

	@PutMapping("/{id}/estado")
	public HonorarioVista cambiarEstado(@PathVariable int id, @Valid @RequestBody CambioEstado c) {
		return servicio.cambiarEstado(id, c.estado());
	}

	@GetMapping("/{id}/pagos")
	public List<Pago> pagos(@PathVariable int id) {
		return servicio.pagos(id);
	}

	@PostMapping("/{id}/pagos")
	public ResponseEntity<HonorarioVista> pagar(@PathVariable int id, @Valid @RequestBody PagoSolicitud s) {
		HonorarioVista h = servicio.registrarPago(id, new Pago(null, id, s.fecha(), s.valor(), s.medio(), s.referencia()));
		return ResponseEntity.status(201).body(h);
	}

}
