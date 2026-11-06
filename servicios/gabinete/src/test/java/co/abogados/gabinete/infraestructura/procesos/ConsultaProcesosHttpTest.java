package co.abogados.gabinete.infraestructura.procesos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import co.abogados.gabinete.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.gabinete.infraestructura.seguridad.TokenServicio;

/** El cliente HTTP de procesos contra un servidor local que hace de procesos e identidad. */
class ConsultaProcesosHttpTest {

	HttpServer servidor;
	List<String> autorizaciones = new CopyOnWriteArrayList<>();
	List<String> rutas = new CopyOnWriteArrayList<>();

	static void responder(HttpExchange h, int estado, String cuerpo) throws IOException {
		byte[] r = cuerpo.getBytes(StandardCharsets.UTF_8);
		h.getResponseHeaders().add("Content-Type", "application/json");
		h.sendResponseHeaders(estado, r.length);
		try (OutputStream o = h.getResponseBody()) {
			o.write(r);
		}
	}

	@BeforeEach
	void arrancar() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/interno/token",
				h -> responder(h, 200, "{\"token\":\"tk-gabinete\",\"expira\":\"2999-01-01T00:00:00Z\"}"));
		servidor.createContext("/procesos", h -> {
			String ruta = h.getRequestURI().toString();
			rutas.add(ruta);
			autorizaciones.add(String.valueOf(h.getRequestHeaders().getFirst("Authorization")));
			switch (ruta) {
				case "/procesos?idCliente=12" -> responder(h, 200, "[{\"idProceso\":34},{\"idProceso\":35}]");
				case "/procesos?idCliente=13" -> responder(h, 200, "[]");
				case "/procesos/34" -> responder(h, 200, "{\"idProceso\":34,\"idCliente\":12}");
				case "/procesos/99" -> responder(h, 404, "{\"error\":\"No existe\"}");
				default -> responder(h, 500, "{}");
			}
		});
		servidor.start();
	}

	@AfterEach
	void detener() {
		servidor.stop(0);
	}

	ConsultaProcesosHttp cliente(String clave) {
		String url = "http://127.0.0.1:" + servidor.getAddress().getPort();
		return new ConsultaProcesosHttp(RestClient.builder(), url, new TokenServicio(RestClient.builder(), url, "gabinete", clave));
	}

	@Test
	void cuentaLosProcesosDelClienteConElTokenDelServicio() {
		ConsultaProcesosHttp c = cliente("secreta");

		assertThat(c.procesosDelCliente(12)).isEqualTo(2);
		assertThat(c.procesosDelCliente(13)).isZero();
		assertThat(autorizaciones).containsOnly("Bearer tk-gabinete");
	}

	@Test
	void sinClaveLaLlamadaSaleSinToken() {
		assertThat(cliente("").clienteDelProceso(34)).contains(12);
		assertThat(autorizaciones).containsExactly("null");
	}

	@Test
	void unProcesoInexistenteNoTieneCliente() {
		assertThat(cliente("").clienteDelProceso(99)).isEmpty();
		assertThat(rutas).containsExactly("/procesos/99");
	}

	@Test
	void siProcesosFallaElServicioNoEstaDisponible() {
		ConsultaProcesosHttp c = cliente("");

		assertThatThrownBy(() -> c.clienteDelProceso(500)).isInstanceOf(ServicioNoDisponibleException.class)
				.hasMessageContaining("verificar el proceso");
		assertThatThrownBy(() -> c.procesosDelCliente(500)).isInstanceOf(ServicioNoDisponibleException.class)
				.hasMessageContaining("tiene procesos");
	}

}
