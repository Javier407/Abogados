package co.abogados.procesos.infraestructura.tareas;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.abogados.procesos.aplicacion.AgendaServicio;
import co.abogados.procesos.aplicacion.TerminoServicio;

/**
 * Cada mañana hábil revisa los términos y las audiencias del día hábil siguiente, y publica los avisos. Hora configurable con
 * terminos.cron (por defecto 7:00 de lunes a viernes, hora de Colombia).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "terminos.revision-automatica", havingValue = "true", matchIfMissing = true)
public class RevisionDiariaTerminos {

	private final TerminoServicio terminos;
	private final AgendaServicio agenda;

	public RevisionDiariaTerminos(TerminoServicio terminos, AgendaServicio agenda) {
		this.terminos = terminos;
		this.agenda = agenda;
	}

	@Scheduled(cron = "${terminos.cron:0 0 7 * * MON-FRI}", zone = "America/Bogota")
	public void revisar() {
		terminos.revisarVencimientos();
		agenda.avisarAudiencias();      // las audiencias del próximo día hábil
	}

}
