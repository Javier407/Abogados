package co.abogados.identidad.dominio.puertos;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import co.abogados.identidad.dominio.Cuenta;

/** Administración de cuentas (solo la usa el administrador). */
public interface CuentasRepositorio {

	List<Cuenta> listar();

	Optional<Cuenta> buscar(long idUsuario);

	/** Ya existe una cuenta con ese documento, usuario o correo (sin contar la cuenta excluir). */
	boolean existe(long idUsuario, String usuario, String email, Long excluir);

	/** Crea la cuenta; debe cambiar la contraseña al entrar por primera vez. */
	void crear(long idUsuario, String usuario, String nombre, String email, String hashClave, Set<String> roles);

	void actualizar(long idUsuario, String usuario, String nombre, String email, boolean activo, Set<String> roles);

	/** Pone una contraseña temporal: desbloquea la cuenta y obliga a cambiarla al entrar. */
	void restablecerClave(long idUsuario, String hashClave);

	void desbloquear(long idUsuario);

	/** Cuentas activas con rol ADMIN. */
	int administradoresActivos();

}
