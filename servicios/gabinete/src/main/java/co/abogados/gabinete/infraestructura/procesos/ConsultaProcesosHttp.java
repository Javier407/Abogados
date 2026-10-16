package co.abogados.gabinete.infraestructura.procesos;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import co.abogados.gabinete.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.gabinete.dominio.puertos.ConsultaProcesos;

/** Pregunta al servicio procesos por la red interna de Docker. */
@Component
public class ConsultaProcesosHttp implements ConsultaProcesos {

	private final RestClient http;

	public ConsultaProcesosHttp(RestClient.Builder builder, @Value("${procesos.url}") String url) {
		SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(2));
		tiempos.setReadTimeout(Duration.ofSeconds(5));
		this.http = builder.baseUrl(url).requestFactory(tiempos).build();
	}

	@Override
	public int procesosDelCliente(int idCliente) {
		try {
			List<Object> procesos = http.get()
					.uri("/procesos?idCliente={id}", idCliente)
					.retrieve()
					.body(new ParameterizedTypeReference<List<Object>>() {
					});
			return procesos == null ? 0 : procesos.size();
		} catch (RestClientException e) {
			throw new ServicioNoDisponibleException(
					"No se pudo verificar si el cliente tiene procesos: el servicio procesos no responde");
		}
	}

	@Override
	public Optional<Integer> clienteDelProceso(int idProceso) {
		try {
			Map<?, ?> p = http.get().uri("/procesos/{id}", idProceso).retrieve().body(Map.class);
			return p == null ? Optional.empty() : Optional.of(((Number) p.get("idCliente")).intValue());
		} catch (HttpClientErrorException.NotFound e) {
			return Optional.empty();
		} catch (RestClientException e) {
			throw new ServicioNoDisponibleException("No se pudo verificar el proceso: el servicio procesos no responde");
		}
	}

}
