package co.abogados.procesos.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.AudienciaVista;
import co.abogados.procesos.dominio.modelo.AvisoAudiencia;
import co.abogados.procesos.dominio.puertos.AgendaRepositorio;
import co.abogados.procesos.dominio.puertos.PublicadorAgenda;

/** Agenda de audiencias: consulta por fechas y aviso el día hábil anterior (evento audiencia.proxima). */
@Service
@Transactional(readOnly = true)
public class AgendaServicio {

	private static final Logger log = LoggerFactory.getLogger(AgendaServicio.class);

	private final AgendaRepositorio agenda;
	private final CalendarioServicio calendario;
	private final PublicadorAgenda publicador;
	private final Clock reloj;

	public AgendaServicio(AgendaRepositorio agenda, CalendarioServicio calendario, PublicadorAgenda publicador, Clock reloj) {
		this.agenda = agenda;
		this.calendario = calendario;
		this.publicador = publicador;
		this.reloj = reloj;
	}

	public List<AudienciaVista> audiencias(LocalDate desde, LocalDate hasta, Long idAsistente) {
		if (desde == null || hasta == null || hasta.isBefore(desde)) {
			throw new ReglaNegocioException("Indique un rango de fechas válido (desde ≤ hasta)");
		}
		if (ChronoUnit.DAYS.between(desde, hasta) > 400) {
			throw new ReglaNegocioException("Consulte máximo un año de audiencias a la vez");
		}
		return agenda.audiencias(desde, hasta, idAsistente);
	}

	/**
	 * Avisa las audiencias del próximo día hábil: el viernes avisa las del sábado al lunes, y el día antes de
	 * un festivo, también las del día siguiente al festivo. La corre la revisión de cada mañana; si se repite,
	 * notificaciones no duplica (la clave del aviso lleva la audiencia y su fecha).
	 */
	public int avisarAudiencias() {
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate siguienteHabil = calendario.vencimiento(hoy, 1);
		Instant ahora = Instant.now(reloj);
		int avisadas = 0;
		for (AudienciaVista a : agenda.audiencias(hoy.plusDays(1), siguienteHabil, null)) {
			if (a.idAsistente() == null) {
				log.warn("La audiencia {} del proceso {} no tiene quién la atienda; no se avisa", a.idActuacion(), a.idProceso());
				continue;
			}
			publicador.avisoAudiencia(new AvisoAudiencia(a.idActuacion(), a.idProceso(), a.radicado(), a.descripcion(),
					a.fecha(), a.hora(), a.lugar(), a.idAsistente(), ahora));
			avisadas++;
		}
		log.info("Audiencias avisadas para el próximo día hábil ({}): {}", siguienteHabil, avisadas);
		return avisadas;
	}

}
