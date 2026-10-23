package co.abogados.identidad.infraestructura;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.AvisosSeguridad;

/**
 * Publica cuenta.seguridad en el exchange de eventos (F15); notificaciones avisa a la persona por la app, por correo y
 * por Telegram. Se publica después de confirmar la transacción (si se deshace, no se avisa nada). No hay outbox: es un
 * aviso informativo y un fallo de RabbitMQ no debe impedir cambiar una contraseña; queda en el log.
 */
@Component
public class AvisosSeguridadRabbit implements AvisosSeguridad {

	static final String EVENTO = "cuenta.seguridad";
	private static final Logger log = LoggerFactory.getLogger(AvisosSeguridadRabbit.class);

	private final RabbitTemplate rabbit;
	private final ObjectMapper json;
	private final Clock reloj;
	private final String exchange;

	public AvisosSeguridadRabbit(RabbitTemplate rabbit, ObjectMapper json, Clock reloj,
			@Value("${eventos.exchange:gabinete.eventos}") String exchange) {
		this.rabbit = rabbit;
		this.json = json;
		this.reloj = reloj;
		this.exchange = exchange;
	}

	@Override
	public void avisar(Tipo tipo, Usuario u) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("tipo", tipo.name());
		cuerpo.put("idUsuario", u.idUsuario());
		cuerpo.put("usuario", u.usuario());
		cuerpo.put("nombre", u.nombre());
		cuerpo.put("email", u.email());
		cuerpo.put("momento", Instant.now(reloj).toString());
		Runnable enviar = () -> enviar(cuerpo);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					enviar.run();
				}
			});
		} else {
			enviar.run();
		}
	}

	void enviar(Map<String, Object> cuerpo) {
		try {
			MessageProperties p = new MessageProperties();
			p.setContentType(MessageProperties.CONTENT_TYPE_JSON);
			p.setContentEncoding(StandardCharsets.UTF_8.name());
			p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
			p.setMessageId(UUID.randomUUID().toString());
			rabbit.send(exchange, EVENTO, new Message(json.writeValueAsBytes(cuerpo), p));
		} catch (JsonProcessingException | RuntimeException e) {
			log.warn("No se pudo publicar el aviso de seguridad {} de la cuenta {}: {}", cuerpo.get("tipo"),
					cuerpo.get("idUsuario"), e.getMessage());
		}
	}

}
