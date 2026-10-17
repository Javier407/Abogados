package co.abogados.procesos.dominio.modelo;

/** Posible conflicto: la contraparte coincide con un cliente de la firma. Es un aviso, no un bloqueo. */
public record ConflictoInteres(int idCliente, String nombre, String numeroDocumento, String motivo) {
}
