package co.abogados.procesos.infraestructura.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.AgendaServicio;
import co.abogados.procesos.dominio.modelo.AudienciaVista;

/** Agenda de audiencias (las actuaciones de tipo AUDIENCIA) y su aviso del día hábil anterior. */
@RestController
public class AgendaRecurso {

	private final AgendaServicio servicio;

	public AgendaRecurso(AgendaServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/audiencias")
	public List<AudienciaVista> audiencias(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) Long idAsistente) {
		return servicio.audiencias(desde, hasta, idAsistente);
	}

	/** Ejecuta ya el aviso que la revisión de cada mañana hace para las audiencias del próximo día hábil. */
	@PostMapping("/audiencias/avisos")
	public Map<String, Integer> avisar() {
		return Map.of("avisadas", servicio.avisarAudiencias());
	}

}
