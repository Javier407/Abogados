package co.abogados.identidad.dominio.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import co.abogados.identidad.dominio.EstadoMfa;

public interface MfaRepositorio {

	Optional<EstadoMfa> estado(long idUsuario);

	/** Guarda un secreto nuevo sin activar (reemplaza uno pendiente). */
	void guardarPendiente(long idUsuario, String secretoCifrado);

	void activar(long idUsuario, Instant desde, long paso);

	/** Borra el secreto y los códigos de recuperación. */
	void desactivar(long idUsuario);

	/** Registra el intervalo usado solo si es posterior al último (false = ese código ya se usó: repetición). */
	boolean usarPaso(long idUsuario, long paso);

	/** Reemplaza los códigos de recuperación (sus SHA-256). */
	void guardarCodigos(long idUsuario, List<String> hashes);

	/** Gasta el código si existe y no se ha usado. */
	boolean usarCodigo(long idUsuario, String hash, Instant ahora);

	/** Cuentas con la verificación activa (para la pantalla de usuarios). */
	Set<Long> conMfa();

}
