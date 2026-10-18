package co.abogados.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Las rutas están en application.yml; la seguridad en ConfiguracionSeguridad y PoliticaAcceso. */
@SpringBootApplication
@EnableScheduling
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

}
