package co.abogados.procesos.infraestructura.gabinete;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import co.abogados.procesos.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;

/** Consulta clientes y abogados al servicio gabinete por su API REST (red interna de Docker). */
@Component
public class DirectorioGabineteHttp implements DirectorioGabinete {

	private final RestClient http;

	public DirectorioGabineteHttp(RestClient.Builder builder, @Value("${gabinete.url}") String url) {
		SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(2));
		tiempos.setReadTimeout(Duration.ofSeconds(5));
		this.http = builder.baseUrl(url).requestFactory(tiempos).build();
	}

	@Override
	public Optional<Cliente> cliente(int idCliente) {
		return consultar("/clientes/{id}", Cliente.class, idCliente);
	}

	@Override
	public Optional<Abogado> abogado(long idPersona) {
		return consultar("/abogados/{id}", Abogado.class, idPersona);
	}

	@Override
	public List<ClienteResumen> buscarClientes(String texto) {
		try {
			ClienteResumen[] encontrados = http.get()
					.uri(b -> texto == null ? b.path("/clientes").build() : b.path("/clientes").queryParam("q", texto).build())
					.retrieve().body(ClienteResumen[].class);
			return encontrados == null ? List.of() : List.of(encontrados);
		} catch (RestClientException e) {
			throw new ServicioNoDisponibleException("El servicio gabinete no responde; intente de nuevo en unos segundos");
		}
	}

	private <T> Optional<T> consultar(String ruta, Class<T> tipo, Object id) {
		try {
			return Optional.ofNullable(http.get().uri(ruta, id).retrieve().body(tipo));
		} catch (HttpClientErrorException.NotFound e) {
			return Optional.empty();
		} catch (RestClientException e) {
			throw new ServicioNoDisponibleException("El servicio gabinete no responde; intente de nuevo en unos segundos");
		}
	}

}
