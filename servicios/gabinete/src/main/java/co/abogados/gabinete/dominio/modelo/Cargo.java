package co.abogados.gabinete.dominio.modelo;

/** esAbogado: el cargo exige tarjeta profesional. */
public record Cargo(short idCargo, String codigo, String nombre, boolean esAbogado) {
}
