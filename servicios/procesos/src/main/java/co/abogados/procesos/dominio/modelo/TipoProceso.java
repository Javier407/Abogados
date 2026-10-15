package co.abogados.procesos.dominio.modelo;

/** terminoContestacionDias: término de referencia en días hábiles (null = no aplica). */
public record TipoProceso(short idTipo, String codigoRama, String nombre, Short terminoContestacionDias) {
}
