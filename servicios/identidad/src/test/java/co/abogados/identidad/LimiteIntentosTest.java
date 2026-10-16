package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import co.abogados.identidad.infraestructura.LimiteIntentosPorIp;

class LimiteIntentosTest {

	@Test
	void permiteHastaElMaximoPorIpYSeRecuperaAlPasarLaVentana() {
		AutenticacionTest.MutableReloj reloj = new AutenticacionTest.MutableReloj(Instant.parse("2026-10-16T15:00:00Z"));
		LimiteIntentosPorIp limite = new LimiteIntentosPorIp(reloj);

		for (int i = 0; i < LimiteIntentosPorIp.MAXIMO; i++) {
			assertThat(limite.permitir("100.64.0.9")).isTrue();
		}
		assertThat(limite.permitir("100.64.0.9")).isFalse();
		assertThat(limite.permitir("100.64.0.10")).isTrue();          // otra IP no se ve afectada

		reloj.ahora = reloj.ahora.plus(LimiteIntentosPorIp.VENTANA).plus(Duration.ofSeconds(1));
		assertThat(limite.permitir("100.64.0.9")).isTrue();
	}

}
