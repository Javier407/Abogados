package co.abogados.gateway;

import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Pasa a los servicios quién hace la petición: X-Usuario-Id (documento), X-Usuario-Roles y X-Cliente-Id,
 * tomados del token ya verificado. Primero BORRA las que traiga la petición: nadie puede hacerse pasar por otro
 * escribiéndolas a mano. Desde F9 cada servicio verifica además el token: se reenvía en Authorization (también
 * el del stream de avisos, que llega en la URL y se quita de ella).
 */
@Component
public class IdentidadEnCabeceras implements GlobalFilter, Ordered {

	static final String ID = "X-Usuario-Id";
	static final String ROLES = "X-Usuario-Roles";
	static final String CLIENTE = "X-Cliente-Id";

	@Override
	public Mono<Void> filter(ServerWebExchange intercambio, GatewayFilterChain cadena) {
		return ReactiveSecurityContextHolder.getContext()
				.map(SecurityContext::getAuthentication)
				.filter(JwtAuthenticationToken.class::isInstance)
				.cast(JwtAuthenticationToken.class)
				.map(token -> conIdentidad(intercambio, token))
				.switchIfEmpty(Mono.fromSupplier(() -> sinIdentidad(intercambio)))
				.flatMap(cadena::filter);
	}

	static ServerWebExchange conIdentidad(ServerWebExchange intercambio, JwtAuthenticationToken token) {
		String roles = PoliticaAcceso.roles(token).stream().sorted().collect(Collectors.joining(","));
		Object cliente = token.getToken().getClaims().get("idCliente");
		return conCabeceras(intercambio, token.getToken().getSubject(), roles, cliente == null ? null : cliente.toString(),
				token.getToken().getTokenValue());
	}

	static ServerWebExchange sinIdentidad(ServerWebExchange intercambio) {
		return conCabeceras(intercambio, null, null, null, null);
	}

	/**
	 * Copia las cabeceras sin las de identidad y agrega las del token (si hay). Se arma una copia porque las
	 * cabeceras de la petición real (Netty) son de solo lectura: borrarlas en su lugar lanza una excepción.
	 */
	private static ServerWebExchange conCabeceras(ServerWebExchange intercambio, String id, String roles, String cliente,
			String tokenValor) {
		HttpHeaders nuevas = new HttpHeaders();
		intercambio.getRequest().getHeaders().forEach((nombre, valores) -> {
			if (!nombre.equalsIgnoreCase(ID) && !nombre.equalsIgnoreCase(ROLES) && !nombre.equalsIgnoreCase(CLIENTE)) {
				nuevas.addAll(nombre, valores);
			}
		});
		if (id != null) {
			nuevas.set(ID, id);
			nuevas.set(ROLES, roles);
			if (cliente != null) {
				nuevas.set(CLIENTE, cliente);
			}
			nuevas.setBearerAuth(tokenValor);
		}
		ServerHttpRequest peticion = new ServerHttpRequestDecorator(intercambio.getRequest()) {
			@Override
			public HttpHeaders getHeaders() {
				return HttpHeaders.readOnlyHttpHeaders(nuevas);
			}
		};
		return intercambio.mutate().request(peticion).build();
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

}
