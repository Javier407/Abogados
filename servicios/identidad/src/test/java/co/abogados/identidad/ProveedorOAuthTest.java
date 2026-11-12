package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import co.abogados.identidad.dominio.AccesoExterno.Identidad;
import co.abogados.identidad.dominio.AccesoExterno.Proveedor;
import co.abogados.identidad.infraestructura.ProveedorOAuth;

/** El adaptador OAuth contra un servidor local que imita al proveedor (sin salir a internet). */
class ProveedorOAuthTest {

	HttpServer servidor;
	String base;
	final List<String> recibido = new ArrayList<>();
	String respuestaToken = "{\"access_token\":\"tok-123\",\"token_type\":\"Bearer\"}";
	String respuestaPersona = "{\"sub\":\"1180\",\"email\":\"c@gmail.com\",\"name\":\"Carolina\"}";

	@BeforeEach
	void levantar() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/token", h -> {
			recibido.add("token:" + new String(h.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			responder(h, respuestaToken);
		});
		servidor.createContext("/persona", h -> {
			recibido.add("persona:" + h.getRequestHeaders().getFirst("Authorization"));
			responder(h, respuestaPersona);
		});
		servidor.start();
		base = "http://127.0.0.1:" + servidor.getAddress().getPort();
	}

	static void responder(com.sun.net.httpserver.HttpExchange h, String cuerpo) throws IOException {
		byte[] b = cuerpo.getBytes(StandardCharsets.UTF_8);
		h.getResponseHeaders().add("Content-Type", "application/json");
		h.sendResponseHeaders(200, b.length);
		h.getResponseBody().write(b);
		h.close();
	}

	@AfterEach
	void apagar() {
		servidor.stop(0);
	}

	ProveedorOAuth proveedor(ProveedorOAuth.Datos plantilla) {
		ProveedorOAuth.Datos d = new ProveedorOAuth.Datos(plantilla.proveedor(), base + "/auth", base + "/token",
				base + "/persona", plantilla.alcance(), plantilla.extrasAutorizacion(), plantilla.lector());
		return new ProveedorOAuth(d, "id-cliente", "secreto-cliente", HttpClient.newHttpClient(), new ObjectMapper());
	}

	@Test
	void laUrlDeIdaLlevaPkceEstadoYVueltaCodificados() {
		String url = proveedor(ProveedorOAuth.google()).urlAutorizacion("est ado", "reto-1", "https://lexora.test/api/auth/externo/google/vuelta");
		assertThat(url).startsWith(base + "/auth?response_type=code&client_id=id-cliente")
				.contains("state=est+ado", "code_challenge=reto-1", "code_challenge_method=S256", "scope=openid+email+profile",
						"redirect_uri=https%3A%2F%2Flexora.test%2Fapi%2Fauth%2Fexterno%2Fgoogle%2Fvuelta", "prompt=select_account")
				.doesNotContain("secreto-cliente");
	}

	@Test
	void canjeaElCodigoConElVerificadorYLeeLaIdentidadOidc() {
		Identidad i = proveedor(ProveedorOAuth.google()).canjear("cod-9", "verif-9", "https://lexora.test/v");

		assertThat(i).isEqualTo(new Identidad(Proveedor.GOOGLE, "1180", "c@gmail.com", "Carolina"));
		assertThat(recibido.get(0)).contains("grant_type=authorization_code", "code=cod-9", "code_verifier=verif-9",
				"client_secret=secreto-cliente", "redirect_uri=https%3A%2F%2Flexora.test%2Fv");
		assertThat(recibido.get(1)).isEqualTo("persona:Bearer tok-123");
	}

	@Test
	void gitHubSeIdentificaPorSuIdNumericoNoPorElLogin() {
		respuestaPersona = "{\"id\":583231,\"login\":\"octocat\",\"name\":null,\"email\":null}";
		Identidad i = proveedor(ProveedorOAuth.github()).canjear("c", "v", "https://lexora.test/v");
		assertThat(i).isEqualTo(new Identidad(Proveedor.GITHUB, "583231", null, "octocat"));
	}

	@Test
	void sinTokenOSinIdentificadorNoHayIdentidad() {
		respuestaToken = "{\"error\":\"bad_verification_code\"}";   // GitHub responde 200 con error
		assertThatThrownBy(() -> proveedor(ProveedorOAuth.github()).canjear("c", "v", "u"))
				.hasMessageContaining("bad_verification_code");

		respuestaToken = "{\"access_token\":\"t\"}";
		respuestaPersona = "{\"email\":\"sin-sub@x.co\"}";
		assertThatThrownBy(() -> proveedor(ProveedorOAuth.google()).canjear("c", "v", "u"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void microsoftUsaElDirectorioIndicado() {
		assertThat(ProveedorOAuth.microsoft("common").urlAutorizacion())
				.isEqualTo("https://login.microsoftonline.com/common/oauth2/v2.0/authorize");
		assertThat(ProveedorOAuth.microsoft("firma-123").urlToken())
				.isEqualTo("https://login.microsoftonline.com/firma-123/oauth2/v2.0/token");
		assertThat(Map.copyOf(ProveedorOAuth.github().extrasAutorizacion())).containsEntry("allow_signup", "false");
	}

}
