package co.abogados.identidad.dominio;

import java.time.Instant;
import java.util.UUID;

/**
 * Un refresh token guardado (solo su hash). Pertenece a una familia: la sesión abierta en un inicio de sesión.
 * Cada renovación lo marca usado y entrega otro de la misma familia.
 */
public record Refresco(UUID id, UUID familia, long idUsuario, Instant expira, Instant familiaExpira, Instant usadoEn,
		Instant revocadoEn) {

	public boolean revocado() {
		return revocadoEn != null;
	}

	public boolean usado() {
		return usadoEn != null;
	}

	public boolean vencido(Instant ahora) {
		return !expira.isAfter(ahora);
	}

}
