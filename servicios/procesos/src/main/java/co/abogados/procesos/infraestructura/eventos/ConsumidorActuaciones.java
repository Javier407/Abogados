package co.abogados.procesos.infraestructura.eventos;

import java.time.LocalDate;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import co.abogados.procesos.aplicacion.ProvidenciaServicio;
import co.abogados.procesos.dominio.modelo.ActuacionDetectada;

/** Escucha actuacion.detectada (cola procesos.actuaciones) y la registra en el expediente. */
@Component
public class ConsumidorActuaciones {

	private static final Logger log = LoggerFactory.getLogger(ConsumidorActuaciones.class);

	private final ProvidenciaServicio providencias;

	public ConsumidorActuaciones(ProvidenciaServicio providencias) {
		this.providencias = providencias;
	}

	@RabbitListener(queues = ConfiguracionRabbit.COLA_ACTUACIONES)
	public void recibir(Map<String, Object> m) {
		providencias.registrarDetectada(leer(m));
	}

	/** Un mensaje que no cumple el contrato no se arregla reintentando: se descarta. */
	static ActuacionDetectada leer(Map<String, Object> m) {
		try {
			return new ActuacionDetectada(texto(m, "radicado"), ((Number) m.get("id_proceso")).intValue(),
					texto(m, "referencia"), LocalDate.parse(texto(m, "fecha")), texto(m, "tipo"),
					texto(m, "actuacion"), (String) m.get("anotacion"), texto(m, "fuente"));
		} catch (RuntimeException e) {
			log.error("actuacion.detectada mal formada, se descarta: {} ({})", m, e.getMessage());
			throw new AmqpRejectAndDontRequeueException("Mensaje inválido", e);
		}
	}

	private static String texto(Map<String, Object> m, String clave) {
		Object v = m.get(clave);
		if (v == null || v.toString().isBlank()) {
			throw new IllegalArgumentException("Falta " + clave);
		}
		return v.toString();
	}

}
