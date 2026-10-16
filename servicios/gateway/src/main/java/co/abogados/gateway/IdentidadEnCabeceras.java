package co.abogados.gateway;

import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Pasa a los servicios quién hace la petición: X-Usuario-Id (documento) y X-Usuario-Roles, tomados del
 * token ya verificado. Primero BORRA las que traiga la petición: un cliente no puede hacerse pasar por otro
 * escribiéndolas a mano. Los servicios no están publicados, así que solo el gateway puede ponerlas.
 */
@Component
public class IdentidadEnCabeceras implements GlobalFilter, Ordered {

	static final String ID = "X-Usuario-Id";
	static final String ROLES = "X-Usuario-Roles";

	@Override
	public Mono<Void> filter(ServerWebExchange intercambio, GatewayFilterChain cadena) {
		return ReactiveSecurityContextHolder.getContext()
				.map(SecurityContext::getAuthentication)
				.filter(JwtAuthenticationToken.class::isInstance)
				.cast(JwtAuthenticationToken.class)
				.map(token -> conIdentidad(intercambio, token))
				.defaultIfEmpty(sinIdentidad(intercambio))
				.flatMap(cadena::filter);
	}

	static ServerWebExchange conIdentidad(ServerWebExchange intercambio, JwtAuthenticationToken token) {
		String roles = PoliticaAcceso.roles(token).stream().sorted().collect(Collectors.joining(","));
		return intercambio.mutate().request(r -> r.headers(h -> {
			h.remove(ID);
			h.remove(ROLES);
			h.set(ID, token.getToken().getSubject());
			h.set(ROLES, roles);
		})).build();
	}

	static ServerWebExchange sinIdentidad(ServerWebExchange intercambio) {
		return intercambio.mutate().request(r -> r.headers(h -> {
			h.remove(ID);
			h.remove(ROLES);
		})).build();
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

}
