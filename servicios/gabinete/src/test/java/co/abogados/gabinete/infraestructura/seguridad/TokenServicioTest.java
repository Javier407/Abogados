package co.abogados.gabinete.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/** El token del servicio se pide a identidad (un servidor local de prueba) y se reutiliza mientras no venza. */
class TokenServicioTest {

	HttpServer identidad;
	AtomicInteger pedidos = new AtomicInteger();
	List<String> cuerpos = new ArrayList<>();
	volatile int estado = 200;
	volatile String expira = "2999-01-01T00:00:00Z";

	@BeforeEach
	void arrancar() throws IOException {
		identidad = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		identidad.createContext("/interno/token", h -> {
			pedidos.incrementAndGet();
			cuerpos.add(new String(h.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] r = ("{\"token\":\"tk-" + pedidos.get() + "\",\"expira\":\"" + expira + "\"}")
					.getBytes(StandardCharsets.UTF_8);
			h.getResponseHeaders().add("Content-Type", "application/json");
			h.sendResponseHeaders(estado, r.length);
			try (OutputStream o = h.getResponseBody()) {
				o.write(r);
			}
		});
		identidad.start();
	}

	@AfterEach
	void detener() {
		identidad.stop(0);
	}

	TokenServicio servicio(String clave) {
		return new TokenServicio(RestClient.builder(), "http://127.0.0.1:" + identidad.getAddress().getPort(),
				"gabinete", clave);
	}

	@Test
	void sinClaveLasLlamadasSalenSinToken() {
		assertThat(servicio("").vigente()).isNull();
		assertThat(servicio(null).vigente()).isNull();
		assertThat(pedidos).hasValue(0);
	}

	@Test
	void pideElTokenUnaVezYLoReutilizaMientrasEsteVigente() {
		TokenServicio s = servicio("secreta");

		assertThat(s.vigente()).isEqualTo("tk-1");
		assertThat(s.vigente()).isEqualTo("tk-1");
		assertThat(pedidos).hasValue(1);
		assertThat(cuerpos.get(0)).contains("\"servicio\":\"gabinete\"").contains("\"clave\":\"secreta\"");
	}

	@Test
	void unTokenVencidoSePideOtraVez() {
		expira = "2000-01-01T00:00:00Z";
		TokenServicio s = servicio("secreta");

		assertThat(s.vigente()).isEqualTo("tk-1");
		assertThat(s.vigente()).isEqualTo("tk-2");
	}

	@Test
	void siIdentidadFallaNoHayTokenPeroNoSeRompe() {
		estado = 500;

		assertThat(servicio("secreta").vigente()).isNull();
		assertThat(pedidos).hasValue(1);
	}

}
