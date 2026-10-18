package co.abogados.identidad.dominio;

import java.time.Instant;
import java.util.Set;

/**
 * Cuenta vista por el administrador (sin el hash de la contraseña).
 * idUsuario es el documento: para el personal coincide con gabinete.personal.id_persona.
 */
public record Cuenta(
		long idUsuario,
		String usuario,
		String nombre,
		String email,
		boolean activo,
		Set<String> roles,
		Instant bloqueadoHasta,
		Instant ultimoIngreso,
		boolean debeCambiarClave) {

	/** Roles que existen en identidad.roles. */
	public static final Set<String> ROLES = Set.of("ADMIN", "SOCIO", "ABOGADO", "DEPENDIENTE", "ADMINISTRATIVO", "CLIENTE");

	public Cuenta {
		roles = roles == null ? Set.of() : Set.copyOf(roles);
	}

	/** Valida los datos editables de una cuenta (los mismos al crearla y al cambiarla). */
	public static void validar(String usuario, String nombre, String email, Set<String> roles) {
		if (usuario == null || !usuario.matches("[a-z0-9._-]{3,30}")) {
			throw new ReglaNegocioException("El usuario debe tener de 3 a 30 letras minúsculas, números, punto, guion o guion bajo");
		}
		if (nombre == null || nombre.isBlank() || nombre.strip().length() > 120) {
			throw new ReglaNegocioException("El nombre es obligatorio (máximo 120 caracteres)");
		}
		if (email == null || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || email.length() > 120) {
			throw new ReglaNegocioException("El correo no es válido");
		}
		if (roles == null || roles.isEmpty()) {
			throw new ReglaNegocioException("Asigne al menos un rol");
		}
		for (String r : roles) {
			if (!ROLES.contains(r)) {
				throw new ReglaNegocioException("Rol desconocido: " + r);
			}
		}
		if (roles.contains("CLIENTE") && roles.size() > 1) {
			throw new ReglaNegocioException("Un cliente no puede tener también roles del personal");
		}
	}

}
