package co.abogados.procesos.infraestructura.eventos;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class ConfiguracionRabbit {

	/** Cola durable de este servicio para actuacion.detectada (la publica vigilancia). */
	public static final String COLA_ACTUACIONES = "procesos.actuaciones";

	/** Exchange compartido por todos los servicios; se crea si no existe. */
	@Bean
	TopicExchange exchangeEventos(@Value("${eventos.exchange}") String nombre) {
		return new TopicExchange(nombre, true, false);
	}

	@Bean
	Queue colaActuaciones() {
		return new Queue(COLA_ACTUACIONES, true);
	}

	@Bean
	Binding actuacionesDetectadas(Queue colaActuaciones, TopicExchange exchangeEventos) {
		return BindingBuilder.bind(colaActuaciones).to(exchangeEventos).with("actuacion.detectada");
	}

	/** Los eventos viajan como JSON para que los servicios Python los lean sin librerías de Java. */
	@Bean
	MessageConverter conversorJson(ObjectMapper objectMapper) {
		return new Jackson2JsonMessageConverter(objectMapper);
	}

}
