package co.abogados.identidad.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Cuenta para entrar al sistema. idUsuario es el número de documento: para el personal coincide con
 * gabinete.personal.id_persona, así los avisos y los términos se asocian a quien inicia sesión.
 * idCliente: solo en las cuentas con rol CLIENTE, la ficha del cliente en gabinete (el portal muestra lo suyo).
 */
public record Usuario(
		long idUsuario,
		String usuario,
		String nombre,
		String email,
		String hashClave,
		boolean activo,
		Set<String> roles,
		int intentosFallidos,
		Instant bloqueadoHasta,
		Integer idCliente) {

	/** Intentos fallidos seguidos que bloquean la cuenta. */
	public static final int MAX_INTENTOS = 5;

	/** Tiempo de bloqueo tras MAX_INTENTOS fallos. */
	public static final Duration BLOQUEO = Duration.ofMinutes(15);

	public Usuario {
		roles = roles == null ? Set.of() : Set.copyOf(roles);
	}

	public Usuario(long idUsuario, String usuario, String nombre, String email, String hashClave, boolean activo,
			Set<String> roles, int intentosFallidos, Instant bloqueadoHasta) {
		this(idUsuario, usuario, nombre, email, hashClave, activo, roles, intentosFallidos, bloqueadoHasta, null);
	}

	public boolean bloqueado(Instant ahora) {
		return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
	}

	/** Minutos (redondeados hacia arriba) que faltan para que se desbloquee. */
	public long minutosDeBloqueo(Instant ahora) {
		if (!bloqueado(ahora)) {
			return 0;
		}
		long segundos = Duration.between(ahora, bloqueadoHasta).getSeconds();
		return (segundos + 59) / 60;
	}

	/** Resultado de sumar un fallo: si llega al máximo, se bloquea y el contador vuelve a cero. */
	public Fallo registrarFallo(Instant ahora) {
		int intentos = intentosFallidos + 1;
		if (intentos >= MAX_INTENTOS) {
			return new Fallo(0, ahora.plus(BLOQUEO));
		}
		return new Fallo(intentos, null);
	}

	public record Fallo(int intentos, Instant bloqueadoHasta) {

		public boolean bloquea() {
			return bloqueadoHasta != null;
		}
	}

}
