package co.abogados.procesos.infraestructura.tareas;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.abogados.procesos.aplicacion.TerminoServicio;

/**
 * Cada mañana hábil revisa los términos y publica los avisos. Hora configurable con
 * terminos.cron (por defecto 7:00 de lunes a viernes, hora de Colombia).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "terminos.revision-automatica", havingValue = "true", matchIfMissing = true)
public class RevisionDiariaTerminos {

	private final TerminoServicio terminos;

	public RevisionDiariaTerminos(TerminoServicio terminos) {
		this.terminos = terminos;
	}

	@Scheduled(cron = "${terminos.cron:0 0 7 * * MON-FRI}", zone = "America/Bogota")
	public void revisar() {
		terminos.revisarVencimientos();
	}

}
