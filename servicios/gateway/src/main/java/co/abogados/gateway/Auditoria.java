package co.abogados.gateway;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import co.abogados.gateway.auditoria.Acceso;
import co.abogados.gateway.auditoria.Bitacora;
import reactor.core.publisher.Mono;

/**
 * Rastro de quién hizo qué: un registro por cada petición a /api con el usuario (documento), sus roles, el método,
 * la ruta SIN parámetros (ahí podría ir un token), el resultado, la duración y la IP. No guarda cuerpos.
 *
 * Va primero que la seguridad, así quedan también los intentos rechazados (401 y 403), que antes no llegaban a la
 * línea AUDITORIA. Quién es lo anota {@link #capturarIdentidad()} dentro de la cadena de seguridad, justo después
 * de validar el token y antes de decidir el permiso.
 *
 * Cada acceso va a la bitácora encadenada ({@link Bitacora}, docs/14-auditoria.md) y, como antes, a una línea
 * AUDITORIA del log. Hasta F17 era un filtro del gateway que solo escribía esa línea.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class Auditoria implements WebFilter {

	private static final Logger log = LoggerFactory.getLogger("AUDITORIA");
	static final String QUIEN = Auditoria.class.getName() + ".quien";

	record Quien(String usuario, String roles) {
	}

	private final Bitacora bitacora;

	public Auditoria(Bitacora bitacora) {
		this.bitacora = bitacora;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange intercambio, WebFilterChain cadena) {
		ServerHttpRequest peticion = intercambio.getRequest();
		String ruta = peticion.getPath().value();
		if (HttpMethod.OPTIONS.equals(peticion.getMethod()) || !(ruta.equals("/api") || ruta.startsWith("/api/"))) {
			return cadena.filter(intercambio);       // preflight de CORS y health: no son acciones de nadie
		}
		Instant momento = Instant.now();
		long inicio = System.nanoTime();
		return cadena.filter(intercambio).doFinally(senal -> anotar(intercambio, momento, inicio));
	}

	private void anotar(ServerWebExchange intercambio, Instant momento, long inicio) {
		ServerHttpRequest peticion = intercambio.getRequest();
		Quien quien = intercambio.getAttribute(QUIEN);
		HttpStatusCode estado = intercambio.getResponse().getStatusCode();
		Acceso acceso = new Acceso(momento,
				quien == null ? null : quien.usuario(), quien == null ? null : quien.roles(),
				peticion.getMethod().name(), peticion.getPath().value(),
				estado == null ? null : estado.value(),
				(int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - inicio) / 1_000_000),
				ip(peticion));
		log.info("AUDITORIA usuario={} roles={} {} {} -> {} ({} ms) ip={}", acceso.usuario(),
				acceso.roles().isEmpty() ? Acceso.ANONIMO : acceso.roles(), acceso.metodo(), acceso.ruta(),
				acceso.estado() == null ? "-" : acceso.estado(), acceso.duracionMs(), acceso.ip() == null ? "-" : acceso.ip());
		bitacora.anotar(acceso);
	}

	/** nginx pone X-Real-IP con la IP real del cliente; sin nginx (diagnóstico local), la de la conexión. */
	static String ip(ServerHttpRequest peticion) {
		String real = peticion.getHeaders().getFirst("X-Real-IP");
		if (real != null && !real.isBlank()) {
			return real.trim();
		}
		InetSocketAddress remota = peticion.getRemoteAddress();
		return remota == null || remota.getAddress() == null ? null : remota.getAddress().getHostAddress();
	}

	/**
	 * Para la cadena de seguridad (antes de AUTHORIZATION): anota en el intercambio quién hace la petición, ya con
	 * el token validado. Los atributos del intercambio son los mismos aunque otros filtros hagan copias de él.
	 */
	public static WebFilter capturarIdentidad() {
		return (intercambio, cadena) -> ReactiveSecurityContextHolder.getContext()
				.map(SecurityContext::getAuthentication)
				.filter(JwtAuthenticationToken.class::isInstance)
				.cast(JwtAuthenticationToken.class)
				.doOnNext(t -> intercambio.getAttributes().put(QUIEN, new Quien(t.getToken().getSubject(),
						t.getAuthorities().stream().map(GrantedAuthority::getAuthority)
								.filter(r -> r.startsWith("ROL_")).map(r -> r.substring(4)).sorted()
								.collect(Collectors.joining(",")))))
				.then(Mono.defer(() -> cadena.filter(intercambio)));
	}

}
