package co.abogados.identidad.infraestructura;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.abogados.identidad.aplicacion.AccesoExternoServicio;
import co.abogados.identidad.dominio.puertos.ProveedorExterno;

/**
 * Proveedores externos activos: solo los que tienen identificador y secreto en infra/.env (los escribe Javier;
 * nunca van en git). Sin ninguno, el inicio de sesión no muestra los botones y todo sigue igual.
 */
@Configuration
public class ConfiguracionAccesoExterno {

	private static final Logger log = LoggerFactory.getLogger(ConfiguracionAccesoExterno.class);

	@Bean
	AccesoExternoServicio.Configurados proveedoresExternos(ObjectMapper json,
			@Value("${externo.google.id:}") String googleId, @Value("${externo.google.secreto:}") String googleSecreto,
			@Value("${externo.microsoft.id:}") String msId, @Value("${externo.microsoft.secreto:}") String msSecreto,
			@Value("${externo.microsoft.tenant:common}") String msTenant,
			@Value("${externo.github.id:}") String githubId, @Value("${externo.github.secreto:}") String githubSecreto) {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NEVER).build();
		List<ProveedorExterno> lista = new ArrayList<>();
		if (completo(googleId, googleSecreto)) {
			lista.add(new ProveedorOAuth(ProveedorOAuth.google(), googleId.strip(), googleSecreto.strip(), http, json));
		}
		if (completo(msId, msSecreto)) {
			lista.add(new ProveedorOAuth(ProveedorOAuth.microsoft(msTenant.isBlank() ? "common" : msTenant.strip()),
					msId.strip(), msSecreto.strip(), http, json));
		}
		if (completo(githubId, githubSecreto)) {
			lista.add(new ProveedorOAuth(ProveedorOAuth.github(), githubId.strip(), githubSecreto.strip(), http, json));
		}
		log.info("Acceso con cuentas externas: {}", lista.isEmpty() ? "ninguno configurado"
				: lista.stream().map(p -> p.proveedor().nombre()).toList());
		return new AccesoExternoServicio.Configurados(List.copyOf(lista));
	}

	private static boolean completo(String id, String secreto) {
		return id != null && !id.isBlank() && secreto != null && !secreto.isBlank();
	}

}
