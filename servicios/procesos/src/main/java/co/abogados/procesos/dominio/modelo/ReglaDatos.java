package co.abogados.procesos.dominio.modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lo que se escribe al crear o editar una regla de términos (F12). */
public record ReglaDatos(
		String tipoProvidencia,
		String codigoRama,
		Short idTipo,
		String patron,
		String lado,
		String descripcion,
		Integer dias,
		String norma,
		int prioridad,
		boolean activa) {

	public ReglaDatos conRama(String rama) {
		return new ReglaDatos(tipoProvidencia, rama, idTipo, patron, lado, descripcion, dias, norma, prioridad, activa);
	}

	/**
	 * Cambios de contenido respecto a la versión anterior, para el historial ("días: 3 → 5"). No cuenta activa:
	 * activar o desactivar una regla no cambia lo que dice.
	 */
	public List<String> cambiosDesde(ReglaDatos antes) {
		List<String> cambios = new ArrayList<>();
		comparar(cambios, "providencia", antes.tipoProvidencia, tipoProvidencia);
		comparar(cambios, "rama", antes.codigoRama, codigoRama);
		comparar(cambios, "tipo de proceso", antes.idTipo, idTipo);
		comparar(cambios, "patrón", antes.patron, patron);
		comparar(cambios, "lado", antes.lado, lado);
		comparar(cambios, "descripción", antes.descripcion, descripcion);
		comparar(cambios, "días", antes.dias, dias);
		comparar(cambios, "norma", antes.norma, norma);
		comparar(cambios, "prioridad", antes.prioridad, prioridad);
		return cambios;
	}

	private static void comparar(List<String> cambios, String campo, Object antes, Object ahora) {
		if (!Objects.equals(antes, ahora)) {
			cambios.add(campo + ": " + texto(antes) + " → " + texto(ahora));
		}
	}

	private static String texto(Object valor) {
		return valor == null ? "(vacío)" : valor instanceof String s ? "«" + s + "»" : valor.toString();
	}

}
