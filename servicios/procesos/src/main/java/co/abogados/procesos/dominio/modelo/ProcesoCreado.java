package co.abogados.procesos.dominio.modelo;

import java.time.Instant;

/** Evento proceso.creado: el servicio vigilancia empieza a consultar el radicado. */
public record ProcesoCreado(int idProceso, String radicado, long idAbogadoResponsable, Instant ocurrido) {
}
