package co.abogados.procesos.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.CalendarioJudicial;
import co.abogados.procesos.dominio.modelo.DiaInhabil;
import co.abogados.procesos.dominio.puertos.CalendarioRepositorio;

/** Calendario judicial: días inhábiles y cálculo de vencimientos en días hábiles. */
@Service
@Transactional(readOnly = true)
public class CalendarioServicio {

	private final CalendarioRepositorio repositorio;

	public CalendarioServicio(CalendarioRepositorio repositorio) {
		this.repositorio = repositorio;
	}

	/** Se arma en cada uso: son unos cien días y así un festivo agregado en la base cuenta de inmediato. */
	public CalendarioJudicial calendario() {
		List<DiaInhabil> dias = repositorio.diasInhabiles();
		Set<LocalDate> fechas = dias.stream().map(DiaInhabil::fecha).collect(Collectors.toSet());
		// Cubre hasta el 31 de diciembre del último año con festivos, o hasta el último día de vacancia si es posterior.
		LocalDate finAnio = dias.stream().filter(d -> d.tipo().equals("FESTIVO"))
				.map(d -> LocalDate.of(d.fecha().getYear(), 12, 31)).max(LocalDate::compareTo)
				.orElseThrow(() -> new ReglaNegocioException("No hay festivos cargados en procesos.festivos"));
		LocalDate ultimo = fechas.stream().max(LocalDate::compareTo).orElse(finAnio);
		return new CalendarioJudicial(fechas, ultimo.isAfter(finAnio) ? ultimo : finAnio);
	}

	public List<DiaInhabil> diasInhabiles(LocalDate desde, LocalDate hasta) {
		if (hasta.isBefore(desde)) {
			throw new ReglaNegocioException("La fecha final es anterior a la inicial");
		}
		return repositorio.diasInhabiles().stream()
				.filter(d -> !d.fecha().isBefore(desde) && !d.fecha().isAfter(hasta))
				.toList();
	}

	public LocalDate vencimiento(LocalDate notificacion, int diasHabiles) {
		return calendario().sumarDiasHabiles(notificacion, diasHabiles);
	}

}
