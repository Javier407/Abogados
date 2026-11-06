package co.abogados.procesos.infraestructura.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Token de este servicio para llamar a otros servicios (rol SERVICIO, solo lectura). Lo emite identidad a cambio
 * de la clave del servicio (infra/.env, nunca en git) y se reutiliza hasta un minuto antes de vencer.
 * Sin clave configurada (desarrollo, pruebas) las llamadas salen sin token.
 */
@Component
public class TokenServicio {

	private static final Logger log = LoggerFactory.getLogger(TokenServicio.class);

	record Respuesta(String token, Instant expira) {
	}

	private final RestClient identidad;
	private final String nombre;
	private final String clave;
	private String token;
	private Instant expira = Instant.EPOCH;

	public TokenServicio(RestClient.Builder builder, @Value("${identidad.url:http://localhost:8005}") String url,
			@Value("${servicio.nombre:${spring.application.name}}") String nombre,
			@Value("${servicio.clave:}") String clave) {
		SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
		tiempos.setConnectTimeout(Duration.ofSeconds(2));
		tiempos.setReadTimeout(Duration.ofSeconds(5));
		this.identidad = builder.baseUrl(url).requestFactory(tiempos).build();
		this.nombre = nombre;
		this.clave = clave;
	}

	/** Agrega "Authorization: Bearer token-del-servicio" a cada llamada. */
	public ClientHttpRequestInterceptor interceptor() {
		return (peticion, cuerpo, ejecucion) -> {
			String t = vigente();
			if (t != null && !peticion.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
				peticion.getHeaders().setBearerAuth(t);
			}
			return ejecucion.execute(peticion, cuerpo);
		};
	}

	synchronized String vigente() {
		if (clave == null || clave.isBlank()) {
			return null;
		}
		if (token != null && Instant.now().isBefore(expira.minusSeconds(60))) {
			return token;
		}
		try {
			Respuesta r = identidad.post().uri("/interno/token").contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("servicio", nombre, "clave", clave)).retrieve().body(Respuesta.class);
			if (r != null) {
				token = r.token();
				expira = r.expira();
			}
		} catch (RestClientException e) {
			log.warn("No se pudo obtener el token del servicio {} en identidad: {}", nombre, e.getMessage());
		}
		return token;
	}

}
