package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

/** Evento actuacion.detectada que publica el servicio vigilancia (contrato: docs/contratos/eventos.md). */
public record ActuacionDetectada(String radicado, int idProceso, String referencia, LocalDate fecha, String tipo,
		String actuacion, String anotacion, String fuente) {
}
