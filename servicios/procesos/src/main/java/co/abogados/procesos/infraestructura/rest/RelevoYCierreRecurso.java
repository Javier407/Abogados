package co.abogados.procesos.infraestructura.rest;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.CierreServicio;
import co.abogados.procesos.aplicacion.RelevoServicio;
import co.abogados.procesos.dominio.modelo.Cierre;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Relevo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cambiar el abogado a cargo de un proceso (con su historial) y cerrar o reabrir el proceso. Los permisos los pone
 * el gateway: relevar y cerrar, los abogados; reabrir, solo la dirección.
 */
@RestController
public class RelevoYCierreRecurso {

	public record RelevoSolicitud(
			@NotNull(message = "Elija el abogado que recibe el proceso") @Positive Long idResponsable,
			@NotNull(message = "Indique el motivo del cambio") Relevo.Motivo motivo,
			@Size(max = 300, message = "La nota admite máximo 300 caracteres") String nota,
			Boolean anteriorQuedaDeApoyo,
			Boolean trasladarPendientes) {
	}

	public record CierreSolicitud(
			@NotNull(message = "Indique por qué se cierra el proceso") Cierre.Motivo motivo,
			LocalDate fecha,
			@Size(max = 300, message = "La nota admite máximo 300 caracteres") String nota) {
	}

	public record ReaperturaSolicitud(EtapaProceso etapa) {
	}

	private final RelevoServicio relevos;
	private final CierreServicio cierres;

	public RelevoYCierreRecurso(RelevoServicio relevos, CierreServicio cierres) {
		this.relevos = relevos;
		this.cierres = cierres;
	}

	/** Por defecto el anterior sale del equipo y su trabajo pendiente pasa al nuevo responsable. */
	@PostMapping("/procesos/{idProceso}/relevo")
	public Relevo.Resultado relevar(@PathVariable int idProceso, @Valid @RequestBody RelevoSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario) {
		return relevos.relevar(idProceso, new Relevo.Solicitud(s.idResponsable(), s.motivo(), s.nota(),
				Boolean.TRUE.equals(s.anteriorQuedaDeApoyo()), !Boolean.FALSE.equals(s.trasladarPendientes())), usuario);
	}

	@GetMapping("/procesos/{idProceso}/asignaciones/historial")
	public List<Relevo.Periodo> historial(@PathVariable int idProceso) {
		return relevos.historial(idProceso);
	}

	/** Sin fecha, hoy. */
	@PostMapping("/procesos/{idProceso}/cierre")
	public Cierre.Resultado cerrar(@PathVariable int idProceso, @Valid @RequestBody CierreSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario) {
		return cierres.cerrar(idProceso, s.motivo(), s.fecha(), s.nota(), usuario);
	}

	@PostMapping("/procesos/{idProceso}/reapertura")
	public ProcesoVista reabrir(@PathVariable int idProceso, @RequestBody(required = false) ReaperturaSolicitud s,
			@RequestHeader("X-Usuario-Id") long usuario) {
		return cierres.reabrir(idProceso, s == null ? null : s.etapa(), usuario);
	}

}
