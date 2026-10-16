package co.abogados.identidad;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class IdentidadApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdentidadApplication.class, args);
	}

	@Bean
	Clock reloj() {
		return Clock.systemUTC();
	}

}
