package co.abogados.procesos.infraestructura.eventos;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.abogados.procesos.dominio.modelo.ProcesoCreado;
import co.abogados.procesos.dominio.puertos.PublicadorEventos;

/**
 * Publica en el exchange topic gabinete.eventos. El mensaje sale solo después del commit, para no
 * anunciar un proceso que luego se deshizo; si RabbitMQ no está disponible se registra y se sigue.
 * Formato del mensaje: docs/contratos/eventos.md.
 */
@Component
public class PublicadorEventosRabbit implements PublicadorEventos {

	private static final Logger log = LoggerFactory.getLogger(PublicadorEventosRabbit.class);

	private final RabbitTemplate rabbit;
	private final String exchange;

	public PublicadorEventosRabbit(RabbitTemplate rabbit, @Value("${eventos.exchange}") String exchange) {
		this.rabbit = rabbit;
		this.exchange = exchange;
	}

	@Override
	public void procesoCreado(ProcesoCreado e) {
		Map<String, Object> cuerpo = Map.of(
				"id_proceso", e.idProceso(),
				"radicado", e.radicado(),
				"id_abogado_responsable", e.idAbogadoResponsable(),
				"ocurrido", e.ocurrido().toString());
		despuesDelCommit(() -> enviar("proceso.creado", cuerpo));
	}

	private void enviar(String clave, Map<String, Object> cuerpo) {
		try {
			rabbit.convertAndSend(exchange, clave, cuerpo);
			log.info("Evento {} publicado: {}", clave, cuerpo);
		} catch (AmqpException ex) {
			log.warn("No se pudo publicar el evento {} ({}): {}", clave, ex.getMessage(), cuerpo);
		}
	}

	private static void despuesDelCommit(Runnable accion) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					accion.run();
				}
			});
		} else {
			accion.run();
		}
	}

}
