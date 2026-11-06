package co.abogados.identidad.dominio;

import java.time.Instant;

/** Verificación en dos pasos de una cuenta. secretoCifrado: tal como está en la base (AES-GCM). */
public record EstadoMfa(long idUsuario, String secretoCifrado, boolean activo, Instant desde, Long ultimoPaso,
		int codigosRestantes) {
}
