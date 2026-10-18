package co.abogados.identidad.dominio;

import java.time.Instant;
import java.util.Set;

/**
 * Token emitido al iniciar sesión y los datos que la interfaz necesita mostrar.
 * debeCambiarClave: la cuenta es nueva o el administrador restableció la contraseña; la interfaz lleva a
 * "Mi cuenta" hasta que la persona ponga una propia.
 */
public record Sesion(String token, Instant expira, long idUsuario, String usuario, String nombre, Set<String> roles,
		boolean debeCambiarClave) {

	public Sesion(String token, Instant expira, long idUsuario, String usuario, String nombre, Set<String> roles) {
		this(token, expira, idUsuario, usuario, nombre, roles, false);
	}

	public Sesion conCambioDeClave(boolean debe) {
		return new Sesion(token, expira, idUsuario, usuario, nombre, roles, debe);
	}

}
