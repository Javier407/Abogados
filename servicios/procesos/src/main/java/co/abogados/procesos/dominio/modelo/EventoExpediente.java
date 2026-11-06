package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

/**
 * Una entrada de la línea de tiempo del proceso: una actuación de la firma, una providencia del juez o
 * un término. categoria = ACTUACION | PROVIDENCIA | TERMINO; tipo es el subtipo (DEMANDA, AUTO, PENDIENTE...).
 */
public record EventoExpediente(
		LocalDate fecha,
		String categoria,
		String tipo,
		int id,
		String titulo,
		String detalle,
		String origen) {
}
