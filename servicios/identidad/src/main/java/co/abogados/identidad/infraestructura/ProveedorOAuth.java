package co.abogados.identidad.infraestructura;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.abogados.identidad.dominio.AccesoExterno.Identidad;
import co.abogados.identidad.dominio.AccesoExterno.Proveedor;
import co.abogados.identidad.dominio.puertos.ProveedorExterno;

/**
 * Proveedor OAuth 2.0 genérico (código de autorización + PKCE S256), sin librerías: Google, Microsoft y GitHub
 * solo cambian sus direcciones, el alcance y cómo leer a la persona de su respuesta.
 * La identidad se pide al proveedor con el token de acceso recién canjeado, servidor a servidor por HTTPS y con el
 * secreto del cliente; por eso no hace falta verificar la firma de un id_token.
 */
public class ProveedorOAuth implements ProveedorExterno {

	/** Lo que distingue a cada proveedor. lector: de la respuesta del endpoint de la persona a su identidad. */
	public record Datos(Proveedor proveedor, String urlAutorizacion, String urlToken, String urlPersona, String alcance,
			Map<String, String> extrasAutorizacion, Function<JsonNode, Identidad> lector) {
	}

	private static final Duration TIEMPO = Duration.ofSeconds(10);

	private final Datos datos;
	private final String idCliente;
	private final String secreto;
	private final HttpClient http;
	private final ObjectMapper json;

	public ProveedorOAuth(Datos datos, String idCliente, String secreto, HttpClient http, ObjectMapper json) {
		this.datos = datos;
		this.idCliente = idCliente;
		this.secreto = secreto;
		this.http = http;
		this.json = json;
	}

	@Override
	public Proveedor proveedor() {
		return datos.proveedor();
	}

	@Override
	public String urlAutorizacion(String estado, String reto, String urlVuelta) {
		Map<String, String> p = new LinkedHashMap<>();
		p.put("response_type", "code");
		p.put("client_id", idCliente);
		p.put("redirect_uri", urlVuelta);
		p.put("scope", datos.alcance());
		p.put("state", estado);
		p.put("code_challenge", reto);
		p.put("code_challenge_method", "S256");
		p.putAll(datos.extrasAutorizacion());
		return datos.urlAutorizacion() + "?" + formulario(p);
	}

	@Override
	public Identidad canjear(String codigo, String verificador, String urlVuelta) {
		Map<String, String> p = new LinkedHashMap<>();
		p.put("grant_type", "authorization_code");
		p.put("code", codigo);
		p.put("redirect_uri", urlVuelta);
		p.put("client_id", idCliente);
		p.put("client_secret", secreto);
		p.put("code_verifier", verificador);
		JsonNode token = pedir(HttpRequest.newBuilder(URI.create(datos.urlToken()))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(formulario(p))));
		String acceso = token.path("access_token").asText("");
		if (acceso.isBlank()) {
			// GitHub responde 200 con {"error": ...}; nunca se registra el código ni el secreto
			throw new IllegalStateException("El proveedor no entregó el token (" + token.path("error").asText("sin detalle") + ")");
		}
		JsonNode persona = pedir(HttpRequest.newBuilder(URI.create(datos.urlPersona()))
				.header("Authorization", "Bearer " + acceso).GET());
		return datos.lector().apply(persona);
	}

	private JsonNode pedir(HttpRequest.Builder peticion) {
		try {
			HttpResponse<String> r = http.send(peticion.timeout(TIEMPO).header("Accept", "application/json")
					.header("User-Agent", "Lexora-identidad").build(), HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() / 100 != 2) {
				throw new IllegalStateException(datos.proveedor() + " respondió " + r.statusCode());
			}
			return json.readTree(r.body());
		} catch (IOException e) {
			throw new IllegalStateException("No se pudo hablar con " + datos.proveedor() + ": " + e.getClass().getSimpleName());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrumpido");
		}
	}

	static String formulario(Map<String, String> p) {
		return p.entrySet().stream()
				.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
	}

	// ---------- los tres proveedores ----------

	/** OpenID Connect: sub es el identificador estable. */
	static Identidad oidc(Proveedor proveedor, JsonNode n) {
		return new Identidad(proveedor, n.path("sub").asText(""), texto(n, "email"), texto(n, "name"));
	}

	public static Datos google() {
		return new Datos(Proveedor.GOOGLE, "https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token",
				"https://openidconnect.googleapis.com/v1/userinfo", "openid email profile",
				Map.of("prompt", "select_account"), n -> oidc(Proveedor.GOOGLE, n));
	}

	/** tenant: "common" (cuentas personales y de empresa) o el id del directorio de la firma en Microsoft Entra. */
	public static Datos microsoft(String tenant) {
		String base = "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/";
		return new Datos(Proveedor.MICROSOFT, base + "authorize", base + "token", "https://graph.microsoft.com/oidc/userinfo",
				"openid email profile", Map.of("prompt", "select_account"), n -> oidc(Proveedor.MICROSOFT, n));
	}

	/** GitHub no es OpenID Connect: el identificador estable es el id numérico (el login puede cambiar). */
	public static Datos github() {
		return new Datos(Proveedor.GITHUB, "https://github.com/login/oauth/authorize", "https://github.com/login/oauth/access_token",
				"https://api.github.com/user", "read:user", Map.of("allow_signup", "false"),
				n -> new Identidad(Proveedor.GITHUB, n.path("id").asText(""), texto(n, "email"),
						texto(n, "name") != null ? texto(n, "name") : texto(n, "login")));
	}

	private static String texto(JsonNode n, String campo) {
		JsonNode v = n.get(campo);
		return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
	}

}
