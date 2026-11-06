package co.abogados.procesos.dominio.modelo;

/**
 * Regla de referencia para sugerir un término cuando llega una providencia (tabla procesos.reglas_termino).
 * patron es una expresión regular sobre la clase de la providencia normalizada (Texto.normalizar).
 * lado: DEMANDANTE, DEMANDADO o null (ambos). dias null = los días de contestación del tipo de proceso.
 * validada = false mientras un abogado no la revise: la sugerencia lo dice.
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
		boolean validada) {
}
