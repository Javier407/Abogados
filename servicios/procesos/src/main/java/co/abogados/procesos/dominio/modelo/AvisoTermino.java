package co.abogados.procesos.dominio.modelo;

import java.time.Instant;
import java.time.LocalDate;

/** Eventos termino.por_vencer y termino.vencido (contrato: docs/contratos/eventos.md). */
public record AvisoTermino(
		Tipo tipo,
		int idTermino,
		int idProceso,
		String radicado,
		String descripcion,
		LocalDate vence,
		int diasHabilesRestantes,
		long idAbogado,
		Instant ocurrido) {

	public enum Tipo {
		POR_VENCER("termino.por_vencer"), VENCIDO("termino.vencido");

		private final String clave;

		Tipo(String clave) {
			this.clave = clave;
		}

		public String clave() {
			return clave;
		}
	}
}
