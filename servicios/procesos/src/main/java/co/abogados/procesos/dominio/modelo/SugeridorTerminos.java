package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Propone los términos que abre una providencia. Dos fuentes:
 * <ol>
 * <li>Las reglas de referencia (tipo de providencia, rama, tipo de proceso, lado del cliente y un patrón sobre
 * la clase). Son orientativas hasta que un abogado las valide.</li>
 * <li>El plazo escrito en la propia providencia ("allegue el poder en 5 días").</li>
 * </ol>
 * No crea nada: el abogado confirma cada sugerencia. No repite las que ya tienen término.
 */
public final class SugeridorTerminos {

	private static final Pattern PLAZO_ESCRITO = Pattern.compile("\\b(\\d{1,3}) dias\\b");
	private static final Set<String> LADO_DEMANDANTE = Set.of("DEMANDANTE", "ACCIONANTE", "VICTIMA");

	private SugeridorTerminos() {
	}

	/**
	 * @param diasContestacion días de contestación del tipo de proceso (para reglas sin días propios); puede ser null
	 * @param terminosCreados  descripciones de los términos que ya existen para esta providencia
	 * @param vencimiento      calcula el vencimiento de n días hábiles desde la notificación por estado
	 */
	public static List<SugerenciaTermino> sugerir(ProcesoVista proceso, ProvidenciaVista providencia,
			List<ReglaTermino> reglas, Integer diasContestacion, List<String> terminosCreados,
			IntFunction<LocalDate> vencimiento) {
		String clase = Texto.normalizar(providencia.clase());
		String lado = LADO_DEMANDANTE.contains(proceso.rolCliente()) ? "DEMANDANTE" : "DEMANDADO";
		boolean favorable = "FAVORABLE".equals(providencia.sentido());

		Set<String> vistas = new HashSet<>();
		terminosCreados.forEach(d -> vistas.add(Texto.normalizar(d)));
		List<SugerenciaTermino> sugerencias = new ArrayList<>();

		reglas.stream().sorted(Comparator.comparingInt(ReglaTermino::prioridad).thenComparingInt(ReglaTermino::idRegla))
				.filter(r -> r.tipoProvidencia().equals(providencia.tipo()))
				.filter(r -> r.codigoRama() == null || r.codigoRama().equals(proceso.codigoRama()))
				.filter(r -> r.idTipo() == null || r.idTipo() == proceso.idTipo())
				.filter(r -> r.lado() == null || r.lado().equals(lado))
				.filter(r -> Pattern.compile(r.patron()).matcher(clase).find())
				.forEach(r -> {
					Integer dias = r.dias() != null ? r.dias() : diasContestacion;
					if (dias == null || !vistas.add(Texto.normalizar(r.descripcion()))) {
						return;
					}
					// Una sentencia favorable rara vez se apela: la sugerencia baja a opcional.
					int prioridad = favorable && "SENTENCIA".equals(r.tipoProvidencia()) ? 2 : r.prioridad();
					sugerencias.add(new SugerenciaTermino(r.descripcion(), dias, providencia.fechaEstado(),
							vencimiento.apply(dias), r.norma(), "REGLA", prioridad, r.validada()));
				});

		Matcher plazo = PLAZO_ESCRITO.matcher(Texto.normalizar(providencia.decision()) + " " + clase);
		if (plazo.find()) {
			int dias = Integer.parseInt(plazo.group(1));
			String descripcion = recortar("Cumplir lo ordenado: " + providencia.clase(), 150);
			if (dias >= 1 && dias <= 365 && vistas.add(Texto.normalizar(descripcion))) {
				sugerencias.add(0, new SugerenciaTermino(descripcion, dias, providencia.fechaEstado(),
						vencimiento.apply(dias), "Plazo fijado en la providencia", "PROVIDENCIA", 1, true));
			}
		}
		sugerencias.sort(Comparator.comparingInt(SugerenciaTermino::prioridad));
		return sugerencias;
	}

	private static String recortar(String texto, int maximo) {
		return texto.length() > maximo ? texto.substring(0, maximo) : texto;
	}

}
