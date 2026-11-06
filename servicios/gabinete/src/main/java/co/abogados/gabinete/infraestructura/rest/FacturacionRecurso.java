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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.gabinete.aplicacion.FacturacionServicio;
import co.abogados.gabinete.dominio.modelo.ConfigFacturacion;
import co.abogados.gabinete.dominio.modelo.Factura;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Facturación electrónica (F14). Ver las facturas: quienes ven finanzas; emitir y anular: quienes las manejan;
 * cambiar la configuración: la dirección (lo exige el gateway).
 */
@RestController
public class FacturacionRecurso {

	public record LineaSolicitud(@NotNull(message = "Elija el tipo de línea") Factura.TipoLinea tipo,
			@Size(max = 200) String descripcion, @Positive BigDecimal cantidad, @Positive BigDecimal valorUnitario,
			Integer idHonorario, Integer idGasto) {

		FacturacionServicio.LineaSolicitud aServicio() {
			return new FacturacionServicio.LineaSolicitud(tipo, descripcion, cantidad == null ? BigDecimal.ONE : cantidad,
					valorUnitario, idHonorario, idGasto);
		}
	}

	public record FacturaSolicitud(@NotNull(message = "Elija el cliente") @Positive Integer idCliente, Integer idProceso,
			Factura.FormaPago formaPago,
			@DecimalMin(value = "0", message = "La retención va de 0 a 100 %") @DecimalMax(value = "100", message = "La retención va de 0 a 100 %") BigDecimal retencionPct,
			@Size(max = 300) String observaciones,
			@NotEmpty(message = "Agregue al menos una línea") @Size(max = 50) List<@Valid LineaSolicitud> lineas) {

		FacturacionServicio.Solicitud aServicio() {
			return new FacturacionServicio.Solicitud(idCliente, idProceso, formaPago, retencionPct, observaciones,
					lineas.stream().map(LineaSolicitud::aServicio).toList());
		}
	}

	public record AnulacionSolicitud(@NotBlank(message = "Escriba el motivo") @Size(max = 200) String motivo) {
	}

	public record ConfigSolicitud(boolean responsableIva, @NotNull BigDecimal tarifaIva, @NotNull BigDecimal retencionJuridica,
			@NotNull BigDecimal retencionNatural, int diasCredito,
			@NotBlank(message = "Escriba el número de resolución") @Size(max = 30) String resolucion,
			@NotBlank @Size(max = 4) String prefijo, long rangoDesde, long rangoHasta, long siguiente,
			@NotNull LocalDate vigenteDesde, @NotNull LocalDate vigenteHasta, @NotBlank @Size(max = 4) String prefijoNota,
			long siguienteNota, @NotBlank String ambiente, @NotBlank String proveedor) {

		ConfigFacturacion aDominio() {
			return new ConfigFacturacion(responsableIva, tarifaIva, retencionJuridica, retencionNatural, diasCredito,
					resolucion.strip(), prefijo.strip().toUpperCase(), rangoDesde, rangoHasta, siguiente, vigenteDesde,
					vigenteHasta, prefijoNota.strip().toUpperCase(), siguienteNota, ambiente.strip().toUpperCase(),
					proveedor.strip().toUpperCase(), null, null);
		}
	}

	private final FacturacionServicio servicio;

	public FacturacionRecurso(FacturacionServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/facturas")
	public List<Factura> listar(@RequestParam(required = false) Integer idCliente,
			@RequestParam(required = false) Factura.Estado estado) {
		return servicio.listar(idCliente, estado);
	}

	@GetMapping("/facturas/{id}")
	public FacturacionServicio.Detalle detalle(@PathVariable int id) {
		return servicio.detalle(id);
	}

	@GetMapping("/facturas/pendientes")
	public List<FacturacionServicio.Pendiente> pendientes(@RequestParam int idCliente) {
		return servicio.pendientes(idCliente);
	}

	@PostMapping("/facturas/vista-previa")
	public FacturacionServicio.VistaPrevia vistaPrevia(@Valid @RequestBody FacturaSolicitud s) {
		return servicio.vistaPrevia(s.aServicio());
	}

	@PostMapping("/facturas")
	public ResponseEntity<FacturacionServicio.Detalle> emitir(@Valid @RequestBody FacturaSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario) {
		FacturacionServicio.Detalle d = servicio.emitir(s.aServicio(), usuario);
		return ResponseEntity.created(URI.create("/facturas/" + d.factura().idFactura())).body(d);
	}

	@PostMapping("/facturas/{id}/anulacion")
	public FacturacionServicio.Detalle anular(@PathVariable int id, @Valid @RequestBody AnulacionSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario) {
		return servicio.anular(id, s.motivo(), usuario);
	}

	@GetMapping("/facturacion/config")
	public FacturacionServicio.EstadoConfig config() {
		return servicio.estadoConfig();
	}

	@PutMapping("/facturacion/config")
	public FacturacionServicio.EstadoConfig guardarConfig(@Valid @RequestBody ConfigSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario) {
		return servicio.guardarConfig(s.aDominio(), usuario);
	}

	@PostMapping("/facturacion/config/validacion")
	public FacturacionServicio.EstadoConfig validarConfig(@RequestHeader("X-Usuario-Id") long usuario) {
		return servicio.validarConfig(usuario);
	}

}
