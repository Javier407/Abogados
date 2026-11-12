package co.abogados.procesos.dominio.modelo;

/**
 * Regla de referencia para sugerir un término cuando llega una providencia (tabla procesos.reglas_termino).
 * patron es una expresión regular sobre la clase de la providencia normalizada (Texto.normalizar).
 * lado: DEMANDANTE, DEMANDADO o null (ambos). dias null = los días de contestación del tipo de proceso.
 * validada = false mientras un abogado no la revise: la sugerencia lo dice.
 * acto: el acto procesal (CONTESTAR, SUBSANAR, REPOSICION, etc.). Entre reglas del mismo acto gana la más
 * específica (la del tipo de proceso sobre la de la rama, y esta sobre la general). momento: cuándo corre el
 * plazo, en palabras.
 */
public record ReglaTermino(
		int idRegla,
		String tipoProvidencia,
		String codigoRama,
		Short idTipo,
		String patron,
		String lado,
		String descripcion,
		Integer dias,
		String norma,
		int prioridad,
		boolean validada,
		String acto,
		String momento) {

	/** Regla sin acto ni momento (las anteriores a la migración 018). */
	public ReglaTermino(int idRegla, String tipoProvidencia, String codigoRama, Short idTipo, String patron,
			String lado, String descripcion, Integer dias, String norma, int prioridad, boolean validada) {
		this(idRegla, tipoProvidencia, codigoRama, idTipo, patron, lado, descripcion, dias, norma, prioridad,
				validada, null, null);
	}

	/** 2 = regla del tipo de proceso, 1 = de la rama, 0 = general. */
	public int especificidad() {
		return idTipo != null ? 2 : codigoRama != null ? 1 : 0;
	}
}
