package co.abogados.identidad.dominio.puertos;

import java.time.Instant;
import java.util.Optional;

import co.abogados.identidad.dominio.Usuario;

public interface UsuarioRepositorio {

	/** Busca por nombre de usuario o por correo, sin distinguir mayúsculas. */
	Optional<Usuario> porUsuarioOEmail(String texto);

	Optional<Usuario> porId(long idUsuario);

	void guardarFallo(long idUsuario, int intentos, Instant bloqueadoHasta);

	void guardarIngreso(long idUsuario, Instant ahora);

	/** Cambia la contraseña y quita la obligación de cambiarla (la persona ya puso una propia). */
	void cambiarClave(long idUsuario, String hashClave, Instant ahora);

	/** La cuenta es nueva o el administrador restableció la contraseña: debe cambiarla al entrar. */
	boolean debeCambiarClave(long idUsuario);

	/** Bitácora de intentos: usuario escrito, cuenta (si existe), resultado, motivo e IP. */
	void registrarIntento(String usuario, Long idUsuario, boolean exitoso, String motivo, String ip);

}
