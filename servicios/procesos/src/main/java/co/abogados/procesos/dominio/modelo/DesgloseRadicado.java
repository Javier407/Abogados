package co.abogados.procesos.dominio.modelo;

/**
 * Resultado de analizar un radicado sin guardarlo: sus partes, el despacho al que corresponde
 * (null si no está registrado) y el proceso que ya lo usa (null si está libre).
 */
public record DesgloseRadicado(
		String radicado,
		String radicadoLegible,
		String codDane,
		String codEntidad,
		String codEspecialidad,
		String codDespacho,
		int anio,
		int consecutivo,
		int instancia,
		Despacho despacho,
		Integer idProcesoExistente) {

	public static DesgloseRadicado de(Radicado r, Despacho despacho, Integer idProcesoExistente) {
		return new DesgloseRadicado(r.numero(), r.legible(), r.codDane(), r.codEntidad(), r.codEspecialidad(),
				r.codDespacho(), r.anio(), r.consecutivo(), r.instancia(), despacho, idProcesoExistente);
	}
}
