package co.abogados.gateway.auditoria;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Una petición a la API tal como se anota en la bitácora: quién, qué, cuándo, con qué resultado y desde dónde.
 * No lleva cuerpos ni parámetros de la URL (ahí podría ir un token).
 *
 * Los textos se limpian al crearlo: sin caracteres de control (el hash une los campos con saltos de línea) y
 * recortados al tamaño de la columna. El momento se guarda en microsegundos, la precisión de PostgreSQL.
 */
public record Acceso(Instant momento, String usuario, String roles, String metodo, String ruta, Integer estado,
		int duracionMs, String ip) {

	public static final String ANONIMO = "anonimo";

	public Acceso {
		momento = momento.truncatedTo(ChronoUnit.MICROS);
		usuario = limpio(usuario == null || usuario.isBlank() ? ANONIMO : usuario, 64);
		roles = limpio(roles == null ? "" : roles, 200);
		metodo = limpio(metodo, 10);
		ruta = limpio(ruta, 500);
		ip = ip == null || ip.isBlank() ? null : limpio(ip, 45);
		duracionMs = Math.max(0, duracionMs);
	}

	static String limpio(String texto, int maximo) {
		String t = texto.replaceAll("\\p{Cntrl}", "?");
		return t.length() <= maximo ? t : t.substring(0, maximo);
	}

}
