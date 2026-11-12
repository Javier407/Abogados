package co.abogados.gateway.auditoria;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Consulta de la bitácora para el administrador (PoliticaAcceso: solo GET y solo ADMIN). La atiende el propio
 * gateway: no hay ruta hacia un servicio. Leer la bitácora también queda anotado en ella.
 *
 * - GET /api/auditoria/registros?usuario&metodo&ruta&fallos&desde&hasta&antesDe&limite — los más recientes primero;
 *   desde y hasta son fechas (aaaa-mm-dd, hora de Colombia, ambas incluidas); antesDe pide la página siguiente.
 * - GET /api/auditoria/registros/{id} — uno, con sus hashes (para comparar con un sello guardado).
 * - GET /api/auditoria/verificacion?desde&hash — revisa la cadena completa, o desde un sello ya conocido.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaRecurso {

	static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
	private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

	private final Bitacora bitacora;
	private final Semaphore revisando = new Semaphore(1);

	public AuditoriaRecurso(Bitacora bitacora) {
		this.bitacora = bitacora;
	}

	public record Pagina(List<Registro> registros, Long siguiente) {
	}

	public record Revision(Cadena.Verificacion cadena, Bitacora.Estado escritura, Instant momento) {
	}

	@GetMapping("/registros")
	public Mono<ResponseEntity<Object>> registros(@RequestParam(required = false) String usuario,
			@RequestParam(required = false) String metodo, @RequestParam(required = false) String ruta,
			@RequestParam(defaultValue = "false") boolean fallos, @RequestParam(required = false) String desde,
			@RequestParam(required = false) String hasta, @RequestParam(required = false) Long antesDe,
			@RequestParam(defaultValue = "50") int limite) {
		Instant inicio;
		Instant fin;
		try {
			inicio = desde == null || desde.isBlank() ? null : LocalDate.parse(desde).atStartOfDay(COLOMBIA).toInstant();
			fin = hasta == null || hasta.isBlank() ? null
					: LocalDate.parse(hasta).plusDays(1).atStartOfDay(COLOMBIA).toInstant();
		} catch (DateTimeParseException e) {
			return Mono.just(error(HttpStatus.BAD_REQUEST, "Las fechas van como aaaa-mm-dd"));
		}
		Bitacora.Filtro filtro = new Bitacora.Filtro(vacioANulo(usuario), vacioANulo(metodo), vacioANulo(ruta), fallos,
				inicio, fin, antesDe, limite);
		return bloqueante(() -> {
			List<Registro> encontrados = bitacora.buscar(filtro);
			Long siguiente = encontrados.size() < filtro.limite() ? null : encontrados.get(encontrados.size() - 1).id();
			return ResponseEntity.ok(new Pagina(encontrados, siguiente));
		});
	}

	@GetMapping("/registros/{id}")
	public Mono<ResponseEntity<Object>> registro(@PathVariable long id) {
		return bloqueante(() -> bitacora.registro(id).<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElseGet(() -> error(HttpStatus.NOT_FOUND, "No existe el registro " + id)));
	}

	@GetMapping("/verificacion")
	public Mono<ResponseEntity<Object>> verificacion(@RequestParam(defaultValue = "0") long desde,
			@RequestParam(required = false) String hash) {
		if (desde < 0 || (desde > 0 && (hash == null || !HASH.matcher(hash).matches()))) {
			return Mono.just(error(HttpStatus.BAD_REQUEST,
					"Para revisar desde un sello se necesitan su número y su hash (64 caracteres hexadecimales)"));
		}
		if (!revisando.tryAcquire()) {
			return Mono.just(error(HttpStatus.TOO_MANY_REQUESTS, "Ya hay una revisión de la cadena en curso"));
		}
		return bloqueante(() -> ResponseEntity.ok((Object) new Revision(bitacora.verificar(desde, hash),
				bitacora.estado(), Instant.now())))
				.doFinally(s -> revisando.release());
	}

	/** La base se consulta con JDBC: fuera de los hilos del servidor reactivo. */
	private static Mono<ResponseEntity<Object>> bloqueante(Callable<ResponseEntity<Object>> trabajo) {
		return Mono.fromCallable(trabajo).subscribeOn(Schedulers.boundedElastic())
				.onErrorResume(IllegalStateException.class,
						e -> Mono.just(error(HttpStatus.SERVICE_UNAVAILABLE, "La bitácora no responde; intente de nuevo")));
	}

	private static ResponseEntity<Object> error(HttpStatus estado, String mensaje) {
		return ResponseEntity.status(estado).body(Map.of("error", mensaje));
	}

	private static String vacioANulo(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

}
