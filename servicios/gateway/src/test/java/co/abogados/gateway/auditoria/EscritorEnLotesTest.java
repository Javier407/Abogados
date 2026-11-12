package co.abogados.gateway.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Anotar nunca bloquea; si la base se cae, lo pendiente espera y se guarda en orden cuando vuelve. */
class EscritorEnLotesTest {

	static Acceso acceso(int n) {
		return new Acceso(Instant.EPOCH.plusSeconds(n), "u" + n, "ABOGADO", "GET", "/api/procesos/" + n, 200, 1, null);
	}

	static void esperar(java.util.function.BooleanSupplier condicion) throws InterruptedException {
		long hasta = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!condicion.getAsBoolean() && System.nanoTime() < hasta) {
			Thread.sleep(10);
		}
		assertThat(condicion.getAsBoolean()).as("se cumplió a tiempo").isTrue();
	}

	@Test
	void guardaEnLotesYEnElOrdenDeLlegada() throws Exception {
		List<Acceso> guardados = Collections.synchronizedList(new ArrayList<>());
		List<Integer> tamanos = Collections.synchronizedList(new ArrayList<>());
		try (EscritorEnLotes e = new EscritorEnLotes(lote -> {
			tamanos.add(lote.size());
			guardados.addAll(lote);
		}, 1000, 50, Duration.ofMillis(10), Duration.ofMillis(50))) {
			for (int i = 0; i < 120; i++) {
				e.anotar(acceso(i));
			}
			esperar(() -> guardados.size() == 120);
			assertThat(e.pendientes()).isZero();
		}
		assertThat(guardados).extracting(Acceso::usuario).startsWith("u0", "u1", "u2").endsWith("u119");
		assertThat(tamanos).allMatch(t -> t <= 50);
	}

	@Test
	void conLaBaseCaidaReintentaElMismoLoteSinPerderNada() throws Exception {
		AtomicInteger fallas = new AtomicInteger(3);
		List<Acceso> guardados = Collections.synchronizedList(new ArrayList<>());
		try (EscritorEnLotes e = new EscritorEnLotes(lote -> {
			if (fallas.getAndDecrement() > 0) {
				throw new java.sql.SQLException("Connection refused");
			}
			guardados.addAll(lote);
		}, 1000, 500, Duration.ofMillis(5), Duration.ofMillis(20))) {
			for (int i = 0; i < 10; i++) {
				e.anotar(acceso(i));
			}
			esperar(() -> guardados.size() == 10);
			assertThat(e.perdidos()).isZero();
		}
		assertThat(guardados).extracting(Acceso::usuario).containsExactly("u0", "u1", "u2", "u3", "u4", "u5", "u6",
				"u7", "u8", "u9");
	}

	@Test
	void siLaEsperaSeLlenaCuentaLosPerdidosSinBloquear() throws Exception {
		CountDownLatch soltar = new CountDownLatch(1);
		CountDownLatch guardando = new CountDownLatch(1);
		try (EscritorEnLotes e = new EscritorEnLotes(lote -> {
			guardando.countDown();
			soltar.await();            // la base "tarda": el primer lote queda ocupando al escritor
		}, 5, 1, Duration.ofMillis(5), Duration.ofMillis(5))) {
			e.anotar(acceso(0));
			assertThat(guardando.await(5, TimeUnit.SECONDS)).isTrue();
			long antes = System.nanoTime();
			for (int i = 1; i <= 8; i++) {
				e.anotar(acceso(i));    // caben 5 en la espera; 3 se pierden
			}
			assertThat(Duration.ofNanos(System.nanoTime() - antes)).isLessThan(Duration.ofSeconds(1));
			assertThat(e.perdidos()).isEqualTo(3);
			assertThat(e.pendientes()).isEqualTo(6);
			soltar.countDown();
			esperar(() -> e.pendientes() == 0);
		}
	}

}
