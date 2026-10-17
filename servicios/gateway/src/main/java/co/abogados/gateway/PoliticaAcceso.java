package co.abogados.gateway;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import reactor.core.publisher.Mono;

/**
 * Qué roles pueden usar cada ruta de la API. Se aplica la PRIMERA regla que coincide (método y ruta);
 * si ninguna coincide, se niega. Los roles vienen del token (claim "roles", con prefijo ROL_).
 *
 * El cliente (rol CLIENTE) por ahora solo ve su propio perfil: el portal de clientes es trabajo futuro.
 */
@Component
public class PoliticaAcceso implements ReactiveAuthorizationManager<AuthorizationContext> {

	static final Set<String> TODOS = Set.of("ADMIN", "SOCIO", "ABOGADO", "DEPENDIENTE", "ADMINISTRATIVO", "CLIENTE");
	static final Set<String> INTERNOS = Set.of("ADMIN", "SOCIO", "ABOGADO", "DEPENDIENTE", "ADMINISTRATIVO");
	static final Set<String> JURIDICO = Set.of("ADMIN", "SOCIO", "ABOGADO");
	static final Set<String> JURIDICO_Y_DEPENDIENTES = Set.of("ADMIN", "SOCIO", "ABOGADO", "DEPENDIENTE");
	static final Set<String> DIRECCION = Set.of("ADMIN", "SOCIO");

	/** Método null = cualquiera. */
	record Regla(HttpMethod metodo, PathPattern ruta, Set<String> roles) {
		boolean aplica(HttpMethod m, PathContainer p) {
			return (metodo == null || metodo.equals(m)) && ruta.matches(p);
		}
	}

	private static final PathPatternParser PARSER = new PathPatternParser();

	private static Regla r(HttpMethod metodo, String ruta, Set<String> roles) {
		return new Regla(metodo, PARSER.parse(ruta), roles);
	}

	private static final HttpMethod GET = HttpMethod.GET;

	static final List<Regla> REGLAS = List.of(
			// identidad: el propio perfil y la propia contraseña
			r(GET, "/api/auth/yo", TODOS),
			r(HttpMethod.POST, "/api/auth/clave", TODOS),
			// gabinete
			r(GET, "/api/clientes/**", INTERNOS),
			r(null, "/api/clientes/**", Set.of("ADMIN", "SOCIO", "ABOGADO", "ADMINISTRATIVO")),
			r(GET, "/api/abogados/**", INTERNOS),
			r(null, "/api/abogados/**", DIRECCION),
			r(GET, "/api/gabinete/**", INTERNOS),
			r(GET, "/api/peritos/**", INTERNOS),
			r(null, "/api/peritos/**", JURIDICO),
			r(GET, "/api/honorarios/**", Set.of("ADMIN", "SOCIO", "ABOGADO", "ADMINISTRATIVO")),
			r(null, "/api/honorarios/**", Set.of("ADMIN", "SOCIO", "ADMINISTRATIVO")),
			// procesos, términos y calendario
			r(GET, "/api/procesos/**", INTERNOS),
			r(HttpMethod.POST, "/api/procesos/*/terminos", JURIDICO_Y_DEPENDIENTES),
			r(HttpMethod.POST, "/api/procesos/*/actuaciones", JURIDICO_Y_DEPENDIENTES),   // el dependiente radica memoriales
			r(null, "/api/procesos/**", JURIDICO),
			r(GET, "/api/terminos/**", INTERNOS),
			r(null, "/api/terminos/**", JURIDICO_Y_DEPENDIENTES),
			r(GET, "/api/catalogos/**", INTERNOS),
			r(GET, "/api/calendario/**", INTERNOS),
			// vigilancia, avisos y asistente
			r(null, "/api/vigilancia/**", JURIDICO_Y_DEPENDIENTES),
			r(null, "/api/notificaciones/**", INTERNOS),   // cada quien solo los suyos: lo valida notificaciones
			r(null, "/api/asistente/**", JURIDICO));

	@Override
	public Mono<AuthorizationDecision> check(Mono<Authentication> autenticacion, AuthorizationContext contexto) {
		HttpMethod metodo = contexto.getExchange().getRequest().getMethod();
		PathContainer ruta = contexto.getExchange().getRequest().getPath().pathWithinApplication();
		return autenticacion
				.filter(Authentication::isAuthenticated)
				.map(a -> new AuthorizationDecision(permitido(metodo, ruta, roles(a))))
				.defaultIfEmpty(new AuthorizationDecision(false));
	}

	static boolean permitido(HttpMethod metodo, PathContainer ruta, Set<String> roles) {
		return REGLAS.stream().filter(regla -> regla.aplica(metodo, ruta)).findFirst()
				.map(regla -> regla.roles().stream().anyMatch(roles::contains))
				.orElse(false);
	}

	static Set<String> roles(Authentication a) {
		return a.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.filter(r -> r.startsWith("ROL_")).map(r -> r.substring(4))
				.collect(java.util.stream.Collectors.toSet());
	}

}
