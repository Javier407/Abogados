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
import org.springframework.web.bind.annotation.RequestHeader;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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

	public record AnulacionSolicitud(@NotBlank(message = "Escriba el motivo de la anulación") @Size(max = 200) String motivo) {
	}

	public record LiquidacionSolicitud(
			@NotNull(message = "Indique el valor obtenido (o la cuantía)") @PositiveOrZero BigDecimal valorBase,
			LocalDate fecha) {
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

	/** Anula un pago (no lo borra). Quién lo anuló sale del token (X-Usuario-Id, puesta por el gateway). */
	@PostMapping("/{id}/pagos/{idPago}/anulacion")
	public HonorarioVista anularPago(@PathVariable int id, @PathVariable int idPago,
			@Valid @RequestBody AnulacionSolicitud s,
			@RequestHeader(value = "X-Usuario-Id", required = false) Long usuario) {
		return servicio.anularPago(id, idPago, s.motivo(), usuario);
	}

	@PostMapping("/{id}/liquidacion")
	public HonorarioVista liquidar(@PathVariable int id, @Valid @RequestBody LiquidacionSolicitud s) {
		return servicio.liquidar(id, s.valorBase(), s.fecha());
	}

	@PostMapping("/{id}/pagos")
	public ResponseEntity<HonorarioVista> pagar(@PathVariable int id, @Valid @RequestBody PagoSolicitud s) {
		HonorarioVista h = servicio.registrarPago(id, new Pago(null, id, s.fecha(), s.valor(), s.medio(), s.referencia()));
		return ResponseEntity.status(201).body(h);
	}

}
