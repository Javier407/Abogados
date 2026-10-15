package co.abogados.gabinete;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GabineteApplication {

	public static void main(String[] args) {
		SpringApplication.run(GabineteApplication.class, args);
	}

	@Bean
	Clock reloj() {
		return Clock.system(ZoneId.of("America/Bogota"));
	}

}
