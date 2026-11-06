package co.abogados.gateway;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Tokens de acceso cortados antes de vencer (sesión cerrada, cuenta desactivada, contraseña cambiada).
 * Se descarga de identidad cada 15 s; un token revocado deja de servir en ese plazo como máximo.
 * Si identidad no responde se conserva la última lista (los tokens igual vencen a los 15 minutos).
 *
 * También rechaza los tokens de servicio: solo sirven dentro de la red interna, nunca desde fuera.
 */
@Component
public class ListaRevocados implements OAuth2TokenValidator<Jwt> {

	private static final Logger log = LoggerFactory.getLogger(ListaRevocados.class);
	private static final OAuth2Error REVOCADO = new OAuth2Error("invalid_token", "El token fue revocado", null);

	private final WebClient identidad;
	private volatile Set<String> revocados = Set.of();
	private volatile boolean avisado;

	public ListaRevocados(WebClient.Builder builder, @Value("${IDENTIDAD_URL:http://localhost:8005}") String url) {
		this.identidad = builder.baseUrl(url).build();
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		if (jwt.getSubject() != null && jwt.getSubject().startsWith("servicio:")) {
			return OAuth2TokenValidatorResult.failure(REVOCADO);
		}
		return jwt.getId() != null && revocados.contains(jwt.getId())
				? OAuth2TokenValidatorResult.failure(REVOCADO)
				: OAuth2TokenValidatorResult.success();
	}

	@Scheduled(fixedDelayString = "${revocados.cada:PT15S}", initialDelayString = "PT5S")
	public void actualizar() {
		try {
			List<String> lista = identidad.get().uri("/interno/revocados").retrieve()
					.bodyToMono(new ParameterizedTypeReference<List<String>>() {
					}).block(Duration.ofSeconds(5));
			revocados = lista == null ? Set.of() : Set.copyOf(lista);
			avisado = false;
		} catch (RuntimeException e) {
			if (!avisado) {
				log.warn("No se pudo actualizar la lista de tokens revocados: {}", e.getMessage());
				avisado = true;
			}
		}
	}

	/** Para las pruebas. */
	void reemplazar(Set<String> jtis) {
		revocados = Set.copyOf(jtis);
	}

}
