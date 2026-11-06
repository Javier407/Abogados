package co.abogados.gabinete.infraestructura.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ninguna ruta responde sin un token válido, salvo las públicas (salud y las que se indiquen).
 * - La identidad sale del token, no de las cabeceras: X-Usuario-Id, X-Usuario-Roles y X-Cliente-Id se reescriben
 *   con lo que dice el token (las que traiga la petición se descartan).
 * - Un servicio interno (rol SERVICIO) solo puede consultar (GET).
 * - Con una contraseña temporal solo se permiten las rutas indicadas (cambiar la contraseña).
 */
public class FiltroToken extends OncePerRequestFilter {

	static final String ID = "X-Usuario-Id";
	static final String ROLES = "X-Usuario-Roles";
	static final String CLIENTE = "X-Cliente-Id";

	private static final AntPathMatcher RUTAS = new AntPathMatcher();

	private final VerificadorToken verificador;
	private final List<String> publicas;
	private final List<String> conClaveTemporal;

	public FiltroToken(VerificadorToken verificador, List<String> publicas, List<String> conClaveTemporal) {
		this.verificador = verificador;
		this.publicas = publicas;
		this.conClaveTemporal = conClaveTemporal;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		String ruta = peticion.getRequestURI().substring(peticion.getContextPath().length());
		if (coincide(publicas, ruta)) {
			cadena.doFilter(peticion, respuesta);
			return;
		}
		Optional<VerificadorToken.Identidad> identidad = verificador.verificar(token(peticion));
		if (identidad.isEmpty()) {
			responder(respuesta, HttpServletResponse.SC_UNAUTHORIZED, "Inicie sesión para continuar");
			return;
		}
		VerificadorToken.Identidad id = identidad.get();
		String metodo = peticion.getMethod();
		if (id.esServicio() && !("GET".equals(metodo) || "HEAD".equals(metodo))) {
			responder(respuesta, HttpServletResponse.SC_FORBIDDEN, "Un servicio interno solo puede consultar");
			return;
		}
		if (id.claveTemporal() && !coincide(conClaveTemporal, ruta)) {
			responder(respuesta, HttpServletResponse.SC_FORBIDDEN, "Cambie su contraseña temporal antes de continuar");
			return;
		}
		cadena.doFilter(new ConIdentidad(peticion, id), respuesta);
	}

	private static boolean coincide(List<String> patrones, String ruta) {
		return patrones.stream().anyMatch(p -> RUTAS.match(p, ruta));
	}

	private static String token(HttpServletRequest p) {
		String h = p.getHeader(HttpHeaders.AUTHORIZATION);
		return h != null && h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).strip() : null;
	}

	private static void responder(HttpServletResponse r, int estado, String mensaje) throws IOException {
		r.setStatus(estado);
		r.setContentType(MediaType.APPLICATION_JSON_VALUE);
		r.setCharacterEncoding(StandardCharsets.UTF_8.name());
		if (estado == HttpServletResponse.SC_UNAUTHORIZED) {
			r.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		}
		r.getWriter().write("{\"error\":\"" + mensaje + "\"}");
	}

	/** La petición con las cabeceras de identidad tomadas del token. */
	static class ConIdentidad extends HttpServletRequestWrapper {

		private final Map<String, String> propias = new LinkedHashMap<>();

		ConIdentidad(HttpServletRequest p, VerificadorToken.Identidad id) {
			super(p);
			if (!id.esServicio()) {
				propias.put(ID.toLowerCase(Locale.ROOT), id.sujeto());
			}
			propias.put(ROLES.toLowerCase(Locale.ROOT), String.join(",", id.roles()));
			if (id.idCliente() != null) {
				propias.put(CLIENTE.toLowerCase(Locale.ROOT), String.valueOf(id.idCliente()));
			}
		}

		private static boolean deIdentidad(String nombre) {
			return ID.equalsIgnoreCase(nombre) || ROLES.equalsIgnoreCase(nombre) || CLIENTE.equalsIgnoreCase(nombre);
		}

		@Override
		public String getHeader(String nombre) {
			return deIdentidad(nombre) ? propias.get(nombre.toLowerCase(Locale.ROOT)) : super.getHeader(nombre);
		}

		@Override
		public Enumeration<String> getHeaders(String nombre) {
			if (!deIdentidad(nombre)) {
				return super.getHeaders(nombre);
			}
			String v = propias.get(nombre.toLowerCase(Locale.ROOT));
			return v == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(v));
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			List<String> nombres = new ArrayList<>();
			Collections.list(super.getHeaderNames()).stream().filter(n -> !deIdentidad(n)).forEach(nombres::add);
			nombres.addAll(propias.keySet());
			return Collections.enumeration(nombres);
		}
	}

}
