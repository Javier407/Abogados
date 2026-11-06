package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

/** tipo: FESTIVO, VACANCIA o SEMANA_SANTA. */
public record DiaInhabil(LocalDate fecha, String descripcion, String tipo) {
}
