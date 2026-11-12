package co.abogados.procesos.dominio.modelo;

/**
 * Un plazo de referencia del tipo de proceso: qué hay que hacer, en cuántos días hábiles, cuándo corre y qué norma
 * lo fija. opcional = «si procede» (recursos). validada = lo revisó un socio. propio = la regla es de este tipo de
 * proceso (no heredada de la rama ni general).
 */
public record PlazoProceso(String descripcion, int diasHabiles, String norma, String momento, String tipoProvidencia,
		boolean opcional, boolean validada, boolean propio) {
}
