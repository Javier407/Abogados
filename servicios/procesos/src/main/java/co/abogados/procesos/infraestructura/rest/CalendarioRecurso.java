package co.abogados.procesos.infraestructura.rest;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.abogados.procesos.aplicacion.CalendarioServicio;
import co.abogados.procesos.dominio.modelo.DiaInhabil;

/** Calendario judicial: días en que no corren términos y cálculo de vencimientos. */
@RestController
@RequestMapping("/calendario")
public class CalendarioRecurso {

	public record Vencimiento(LocalDate fechaInicio, int diasHabiles, LocalDate fechaVencimiento) {
	}

	private final CalendarioServicio servicio;

	public CalendarioRecurso(CalendarioServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public List<DiaInhabil> diasInhabiles(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return servicio.diasInhabiles(desde, hasta);
	}

	@GetMapping("/vencimiento")
	public Vencimiento vencimiento(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam int dias) {
		return new Vencimiento(desde, dias, servicio.vencimiento(desde, dias));
	}

}
