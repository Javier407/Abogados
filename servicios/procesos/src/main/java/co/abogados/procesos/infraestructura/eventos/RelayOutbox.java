package co.abogados.procesos.infraestructura.eventos;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envía a RabbitMQ los eventos del outbox, en orden, cada 2 segundos. Cada mensaje espera la confirmación del
 * broker (publisher confirms) antes de marcarse enviado. Si RabbitMQ no responde, el evento queda pendiente y se
 * reintenta en la siguiente vuelta: no se pierde. Puede llegar dos veces (si se cae justo después de confirmar):
 * los consumidores ya ignoran los repetidos (clave del aviso, referencia de la actuación). El message-id es
 * "procesos-<id>" para identificarlos.
 */
@Component
public class RelayOutbox {

	private static final Logger log = LoggerFactory.getLogger(RelayOutbox.class);
	static final int LOTE = 50;

	/** Envía un mensaje y espera la confirmación; lanza excepción si no se confirmó. */
	public interface Envio {
		void enviar(String clave, String cuerpoJson, String idMensaje);
	}

	private final Outbox outbox;
	private final Envio envio;
	private final TransactionTemplate tx;
	private int vueltas;
	private boolean caido;

	public RelayOutbox(Outbox outbox, Envio envio, TransactionTemplate tx) {
		this.outbox = outbox;
		this.envio = envio;
		this.tx = tx;
	}

	@Scheduled(fixedDelayString = "${outbox.cada:PT2S}", initialDelayString = "PT10S")
	public void programado() {
		enviarPendientes();
		if (++vueltas % 1800 == 0) {   // más o menos una vez por hora
			tx.executeWithoutResult(s -> outbox.purgar(Duration.ofDays(7)));
		}
	}

	/** Devuelve cuántos eventos se enviaron. Se detiene en el primer fallo para conservar el orden. */
	public int enviarPendientes() {
		Integer enviados = tx.execute(s -> {
			List<Outbox.Pendiente> lote = outbox.pendientes(LOTE);
			int n = 0;
			for (Outbox.Pendiente p : lote) {
				try {
					envio.enviar(p.clave(), p.cuerpo(), "procesos-" + p.id());
				} catch (RuntimeException e) {
					outbox.marcarFallo(p.id(), e.getMessage());
					if (!caido) {
						log.warn("RabbitMQ no confirmó el evento {} ({}): queda pendiente y se reintenta", p.id(), e.getMessage());
						caido = true;
					}
					break;
				}
				outbox.marcarEnviado(p.id());
				n++;
			}
			return n;
		});
		if (enviados != null && enviados > 0) {
			if (caido) {
				log.info("RabbitMQ volvió: se enviaron los eventos pendientes");
				caido = false;
			}
			log.info("{} eventos enviados desde el outbox", enviados);
		}
		return enviados == null ? 0 : enviados;
	}

	/** Envío real a RabbitMQ con confirmación (spring.rabbitmq.publisher-confirm-type: simple). */
	@Component
	static class EnvioRabbit implements Envio {

		private final RabbitTemplate rabbit;
		private final String exchange;

		EnvioRabbit(RabbitTemplate rabbit, @Value("${eventos.exchange}") String exchange) {
			this.rabbit = rabbit;
			this.exchange = exchange;
		}

		@Override
		public void enviar(String clave, String cuerpoJson, String idMensaje) {
			MessageProperties props = new MessageProperties();
			props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
			props.setContentEncoding(StandardCharsets.UTF_8.name());
			props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
			props.setMessageId(idMensaje);
			Message mensaje = new Message(cuerpoJson.getBytes(StandardCharsets.UTF_8), props);
			rabbit.invoke(ops -> {
				ops.send(exchange, clave, mensaje);
				ops.waitForConfirmsOrDie(5_000);
				return null;
			});
		}
	}

}
