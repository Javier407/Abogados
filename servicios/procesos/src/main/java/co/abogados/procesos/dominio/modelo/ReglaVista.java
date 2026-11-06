package co.abogados.procesos.dominio.modelo;

import java.time.OffsetDateTime;

/**
 * Una regla de términos tal como la ve y edita la firma (F12): además de lo que usa el sugeridor, si está activa,
 * quién la validó y cuándo, y quién la cambió por última vez. nombreValidador lo completa gabinete (puede ser null).
 */
public record ReglaVista(
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
		boolean activa,
		boolean validada,
		Long validadaPor,
		String nombreValidador,
		OffsetDateTime validadaEn,
		Long modificadaPor,
		OffsetDateTime modificadaEn) {

	public ReglaVista conValidador(String nombre) {
		return new ReglaVista(idRegla, tipoProvidencia, codigoRama, idTipo, patron, lado, descripcion, dias, norma,
				prioridad, activa, validada, validadaPor, nombre, validadaEn, modificadaPor, modificadaEn);
	}

	public ReglaDatos datos() {
		return new ReglaDatos(tipoProvidencia, codigoRama, idTipo, patron, lado, descripcion, dias, norma, prioridad, activa);
	}

}
