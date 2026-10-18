package co.abogados.gateway;

import java.time.Duration;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Los servicios se encuentran por el DNS de Docker Compose, que responde con un TTL de 10 minutos. Si un
 * contenedor se recrea, su IP cambia (y puede quedar con la que tenía otro servicio): con la caché por defecto,
 * el gateway siguió enviando /api/notificaciones al contenedor de vigilancia. Se limita la caché a 10 segundos.
 */
@Configuration
public class ResolucionDns {

	static final Duration MAXIMO = Duration.ofSeconds(10);

	@Bean
	HttpClientCustomizer cacheDnsCorta() {
		return cliente -> cliente.resolver(r -> r.cacheMaxTimeToLive(MAXIMO).cacheNegativeTimeToLive(Duration.ofSeconds(2)));
	}

}
