package co.abogados.identidad.dominio.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import co.abogados.identidad.dominio.Refresco;

/** Refresh tokens (solo su hash) y lista de tokens de acceso revocados. */
public interface SesionesRepositorio {

	/** Guarda un refresh token nuevo y el token de acceso que salió con él. */
	void guardar(Refresco refresco, String hash, UUID jti, Instant jtiExpira, String ip);

	Optional<Refresco> porHash(String hash);

	/** Lo marca usado si nadie lo usó antes (false = otro lo usó primero: reutilización). */
	boolean marcarUsado(UUID id, Instant ahora);

	/** Revoca la familia y pone en la lista negra los tokens de acceso que siguen vigentes. Devuelve cuántos. */
	int revocarFamilia(UUID familia, String motivo, Instant ahora);

	/** Revoca todas las sesiones abiertas de la persona, salvo la familia indicada (puede ser null). */
	int revocarUsuario(long idUsuario, UUID salvoFamilia, String motivo, Instant ahora);

	/** jti de los tokens de acceso revocados que aún no vencen. */
	List<String> revocados(Instant ahora);

	/** ¿Ese token de acceso fue revocado? (F13: identidad también lo revisa, no solo el gateway). */
	default boolean revocado(String jti, Instant ahora) {
		return revocados(ahora).contains(jti);
	}

	/** Borra los refresh tokens y revocaciones ya vencidos. */
	void limpiar(Instant ahora);

}
