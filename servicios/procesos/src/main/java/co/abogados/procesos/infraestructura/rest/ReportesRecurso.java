package co.abogados.procesos.infraestructura.rest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.Reportes.CargaAbogado;
import co.abogados.procesos.dominio.modelo.Reportes.CumplimientoTerminos;
import co.abogados.procesos.dominio.modelo.Reportes.ResultadoRama;
import co.abogados.procesos.dominio.puertos.ReporteRepositorio;

/**
 * Reportes para la dirección (el gateway solo deja pasar a ADMIN y SOCIO). Son consultas de lectura sin
 * reglas propias, por eso el recurso usa el repositorio directamente. Sin fechas: los últimos 12 meses.
 */
@RestController
@Transactional(readOnly = true)
public class ReportesRecurso {

	private final ReporteRepositorio reportes;
	private final Clock reloj;

	public ReportesRecurso(ReporteRepositorio reportes, Clock reloj) {
		this.reportes = reportes;
		this.reloj = reloj;
	}

	@GetMapping("/reportes/carga")
	public List<CargaAbogado> carga() {
		return reportes.carga(LocalDate.now(reloj));
	}

	@GetMapping("/reportes/terminos")
	public List<CumplimientoTerminos> terminos(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate[] r = rango(desde, hasta, hoy);
		return reportes.cumplimiento(r[0], r[1], hoy);
	}

	@GetMapping("/reportes/resultados")
	public List<ResultadoRama> resultados(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		LocalDate[] r = rango(desde, hasta, LocalDate.now(reloj));
		return reportes.resultados(r[0], r[1]);
	}

	private static LocalDate[] rango(LocalDate desde, LocalDate hasta, LocalDate hoy) {
		LocalDate h = hasta == null ? hoy : hasta;
		LocalDate d = desde == null ? h.minusYears(1) : desde;
		if (h.isBefore(d)) {
			throw new ReglaNegocioException("La fecha final es anterior a la inicial");
		}
		if (ChronoUnit.DAYS.between(d, h) > 366 * 5) {
			throw new ReglaNegocioException("Consulte máximo cinco años");
		}
		return new LocalDate[] { d, h };
	}

}
