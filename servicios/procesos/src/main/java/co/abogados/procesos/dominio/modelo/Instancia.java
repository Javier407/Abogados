package co.abogados.procesos.dominio.modelo;

/**
 * El mismo asunto en otra instancia: comparte los 21 primeros dígitos del radicado y cambia el sufijo
 * (00 primera instancia, 01 segunda...).
 */
public record Instancia(int idProceso, String radicadoLegible, int instancia, String despacho, String estado) {
}
