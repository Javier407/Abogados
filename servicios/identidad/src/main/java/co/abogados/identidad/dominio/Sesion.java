package co.abogados.identidad.dominio;

import java.time.Instant;
import java.util.Set;

/**
 * Lo que recibe la interfaz al iniciar o renovar la sesión.
 * - token: token de acceso (15 min). expira: su vencimiento.
 * - refresco / refrescoExpira: refresh token de un solo uso. Viaja en una cookie HttpOnly, nunca en el cuerpo.
 * - debeCambiarClave: la cuenta es nueva o el administrador restableció la contraseña; mientras tanto el token
 *   solo sirve para cambiarla (el gateway niega lo demás).
 * - idCliente: cuentas del portal del cliente.
 */
public record Sesion(String token, Instant expira, long idUsuario, String usuario, String nombre, Set<String> roles,
		boolean debeCambiarClave, Integer idCliente, String refresco, Instant refrescoExpira) {

	public Sesion(String token, Instant expira, long idUsuario, String usuario, String nombre, Set<String> roles,
			boolean debeCambiarClave) {
		this(token, expira, idUsuario, usuario, nombre, roles, debeCambiarClave, null, null, null);
	}

	public Sesion(String token, Instant expira, long idUsuario, String usuario, String nombre, Set<String> roles) {
		this(token, expira, idUsuario, usuario, nombre, roles, false);
	}

}
