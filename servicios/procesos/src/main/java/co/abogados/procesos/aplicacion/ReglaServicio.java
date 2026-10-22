package co.abogados.procesos.aplicacion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.procesos.dominio.modelo.CambioRegla;
import co.abogados.procesos.dominio.modelo.ReglaDatos;
import co.abogados.procesos.dominio.modelo.ReglaVista;
import co.abogados.procesos.dominio.modelo.Texto;
import co.abogados.procesos.dominio.modelo.TipoProceso;
import co.abogados.procesos.dominio.puertos.CatalogoRepositorio;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ReglaTerminoRepositorio;

/**
 * Reglas de términos (F12): las ve el personal; las crea, edita y valida un socio (lo exige el gateway y el recurso).
 * - Validar es la firma de un abogado: "revisé la regla y la norma". La sugerencia lo muestra.
 * - Cambiar lo que dice una regla (patrón, días, norma…) la deja otra vez sin validar. Activarla o desactivarla, no.
 * - Cambiar los días de contestación de un tipo de proceso deja sin validar las reglas que los usan (las que no
 *   tienen días propios).
 * - Cada cambio queda en el historial con quién y cuándo.
 */
@Service
@Transactional
public class ReglaServicio {

	private static final Logger log = LoggerFactory.getLogger(ReglaServicio.class);
	private static final Set<String> PROVIDENCIAS = Set.of("AUTO", "SENTENCIA");
	private static final Set<String> LADOS = Set.of("DEMANDANTE", "DEMANDADO");

	private final ReglaTerminoRepositorio reglas;
	private final CatalogoRepositorio catalogos;
	private final DirectorioGabinete gabinete;

	public ReglaServicio(ReglaTerminoRepositorio reglas, CatalogoRepositorio catalogos, DirectorioGabinete gabinete) {
		this.reglas = reglas;
		this.catalogos = catalogos;
		this.gabinete = gabinete;
	}

	@Transactional(readOnly = true)
	public List<ReglaVista> listar() {
		Map<Long, String> nombres = new HashMap<>();
		return reglas.listar().stream()
				.map(r -> r.validadaPor() == null ? r : r.conValidador(nombre(r.validadaPor(), nombres)))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<CambioRegla> historial(int idRegla) {
		existente(idRegla);
		Map<Long, String> nombres = new HashMap<>();
		return reglas.historial(idRegla).stream().map(c -> c.conNombre(nombre(c.idUsuario(), nombres))).toList();
	}

	public ReglaVista crear(ReglaDatos datos, long usuario) {
		ReglaDatos d = revisar(datos);
		int id = reglas.crear(d, usuario);
		reglas.registrar(id, usuario, "CREADA", d.descripcion());
		log.info("Regla de términos {} creada por {}", id, usuario);
		return existente(id);
	}

	public ReglaVista actualizar(int idRegla, ReglaDatos datos, long usuario) {
		ReglaVista antes = existente(idRegla);
		ReglaDatos d = revisar(datos);
		List<String> cambios = d.cambiosDesde(antes.datos());
		if (cambios.isEmpty() && d.activa() == antes.activa()) {
			return antes;
		}
		reglas.actualizar(idRegla, d, usuario, !cambios.isEmpty());
		if (!cambios.isEmpty()) {
			reglas.registrar(idRegla, usuario, "EDITADA", String.join("; ", cambios)
					+ (antes.validada() ? ". Queda sin validar." : ""));
		}
		if (d.activa() != antes.activa()) {
			reglas.registrar(idRegla, usuario, d.activa() ? "ACTIVADA" : "DESACTIVADA", null);
		}
		log.info("Regla de términos {} actualizada por {}: {}", idRegla, usuario, cambios);
		return existente(idRegla);
	}

	public ReglaVista validar(int idRegla, long usuario) {
		ReglaVista r = existente(idRegla);
		if (r.validada()) {
			throw new ConflictoException("La regla ya está validada"
					+ (r.validadaEn() != null ? " desde el " + r.validadaEn().toLocalDate() : ""));
		}
		if (!r.activa()) {
			throw new ReglaNegocioException("Active la regla antes de validarla");
		}
		reglas.validar(idRegla, usuario);
		reglas.registrar(idRegla, usuario, "VALIDADA", r.norma());
		log.info("Regla de términos {} validada por {}", idRegla, usuario);
		return existente(idRegla).conValidador(nombre(usuario, new HashMap<>()));
	}

	/**
	 * Días de contestación de un tipo de proceso (null = no aplica). Las reglas sin días propios que se aplican a
	 * ese tipo quedan sin validar. Devuelve cuántas.
	 */
	public int cambiarContestacion(short idTipo, Integer dias, long usuario) {
		TipoProceso tipo = catalogos.tipo(idTipo)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el tipo de proceso " + idTipo));
		if (dias != null && (dias < 1 || dias > 365)) {
			throw new ReglaNegocioException("Los días de contestación van de 1 a 365 (o vacío si no aplica)");
		}
		Integer antes = tipo.terminoContestacionDias() == null ? null : tipo.terminoContestacionDias().intValue();
		if (java.util.Objects.equals(antes, dias)) {
			return 0;
		}
		reglas.cambiarContestacion(idTipo, dias);
		String detalle = "Días de contestación de «" + tipo.nombre() + "»: " + (antes == null ? "no aplica" : antes)
				+ " → " + (dias == null ? "no aplica" : dias);
		int afectadas = 0;
		for (ReglaVista r : reglas.listar()) {
			boolean usaContestacion = r.dias() == null
					&& (r.codigoRama() == null || r.codigoRama().equals(tipo.codigoRama()))
					&& (r.idTipo() == null || r.idTipo() == idTipo);
			if (usaContestacion) {
				if (r.validada()) {
					reglas.desvalidar(r.idRegla(), usuario);
				}
				reglas.registrar(r.idRegla(), usuario, "EDITADA", detalle + (r.validada() ? ". Queda sin validar." : ""));
				afectadas++;
			}
		}
		log.info("{} por {} ({} regla(s) afectada(s))", detalle, usuario, afectadas);
		return afectadas;
	}

	/** ¿El patrón encuentra algo en la clase de una providencia? Se compara con el texto normalizado, como al sugerir. */
	@Transactional(readOnly = true)
	public Prueba probar(String patron, String clase) {
		String normalizada = Texto.normalizar(clase);
		return new Prueba(compilar(patron).matcher(normalizada).find(), normalizada);
	}

	public record Prueba(boolean coincide, String claseNormalizada) {
	}

	// ------------------------------------------------------------------

	/** Valida los datos y completa la rama a partir del tipo de proceso. */
	private ReglaDatos revisar(ReglaDatos d) {
		if (d.tipoProvidencia() == null || !PROVIDENCIAS.contains(d.tipoProvidencia())) {
			throw new ReglaNegocioException("La regla se aplica a un AUTO o a una SENTENCIA");
		}
		if (d.lado() != null && !LADOS.contains(d.lado())) {
			throw new ReglaNegocioException("El lado es DEMANDANTE, DEMANDADO o vacío (ambos)");
		}
		if (d.prioridad() != 1 && d.prioridad() != 2) {
			throw new ReglaNegocioException("La prioridad es 1 (principal) o 2 (opcional, «si procede»)");
		}
		if (d.dias() != null && (d.dias() < 1 || d.dias() > 365)) {
			throw new ReglaNegocioException("Los días van de 1 a 365 (vacío = los de contestación del tipo de proceso)");
		}
		if (blanco(d.descripcion()) || blanco(d.norma())) {
			throw new ReglaNegocioException("Escriba la descripción del término y la norma que lo fija");
		}
		compilar(d.patron());
		String rama = d.codigoRama();
		if (rama != null && catalogos.ramas().stream().noneMatch(r -> r.codigo().equals(rama))) {
			throw new ReglaNegocioException("No existe la rama " + rama);
		}
		if (d.idTipo() != null) {
			TipoProceso tipo = catalogos.tipo(d.idTipo())
					.orElseThrow(() -> new ReglaNegocioException("No existe el tipo de proceso " + d.idTipo()));
			if (rama != null && !rama.equals(tipo.codigoRama())) {
				throw new ReglaNegocioException("El tipo «" + tipo.nombre() + "» no es de la rama " + rama);
			}
			return d.conRama(tipo.codigoRama());
		}
		return d;
	}

	private static Pattern compilar(String patron) {
		if (blanco(patron) || patron.length() > 200) {
			throw new ReglaNegocioException("Escriba el patrón (hasta 200 caracteres); «.» se aplica a todas las providencias");
		}
		try {
			return Pattern.compile(patron);
		} catch (PatternSyntaxException e) {
			throw new ReglaNegocioException("El patrón no es una expresión regular válida: " + e.getDescription());
		}
	}

	private static boolean blanco(String s) {
		return s == null || s.isBlank();
	}

	private ReglaVista existente(int idRegla) {
		return reglas.buscar(idRegla)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la regla " + idRegla));
	}

	/** Si gabinete no responde, se muestra sin nombre (la cédula sigue en la respuesta). */
	private String nombre(long idPersona, Map<Long, String> cache) {
		return cache.computeIfAbsent(idPersona, id -> {
			try {
				return gabinete.abogado(id).map(p -> (p.nombres() + " " + p.apellidos()).strip()).orElse(null);
			} catch (ServicioNoDisponibleException e) {
				return null;
			}
		});
	}

}
