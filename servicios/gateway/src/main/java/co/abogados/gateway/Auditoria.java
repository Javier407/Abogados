package co.abogados.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Rastro de quién hizo qué: una línea por cada petición a la API con el usuario (documento), el método,
 * la ruta SIN parámetros (ahí podría ir un token), el resultado y la duración. Va al log del contenedor
 * con el prefijo AUDITORIA para poder filtrarlo. No registra cuerpos ni datos personales.
 */
@Component
public class Auditoria implements GlobalFilter, Ordered {

	private static final Logger log = LoggerFactory.getLogger("AUDITORIA");

	@Override
	public Mono<Void> filter(ServerWebExchange intercambio, GatewayFilterChain cadena) {
		long inicio = System.nanoTime();
		return cadena.filter(intercambio).doFinally(senal -> {
			var peticion = intercambio.getRequest();
			HttpStatusCode estado = intercambio.getResponse().getStatusCode();
			log.info("AUDITORIA usuario={} roles={} {} {} -> {} ({} ms)",
					valor(peticion.getHeaders().getFirst(IdentidadEnCabeceras.ID)),
					valor(peticion.getHeaders().getFirst(IdentidadEnCabeceras.ROLES)),
					peticion.getMethod(), peticion.getPath().value(),
					estado == null ? "-" : estado.value(), (System.nanoTime() - inicio) / 1_000_000);
		});
	}

	private static String valor(String v) {
		return v == null || v.isBlank() ? "anonimo" : v;
	}

	/** Después de IdentidadEnCabeceras, para ver la identidad ya tomada del token. */
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 20;
	}

}
