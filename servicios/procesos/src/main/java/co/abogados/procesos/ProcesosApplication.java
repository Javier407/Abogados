package co.abogados.procesos;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ProcesosApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProcesosApplication.class, args);
	}

	/** Los términos judiciales se cuentan en hora de Colombia. */
	@Bean
	Clock reloj() {
		return Clock.system(ZoneId.of("America/Bogota"));
	}

}
