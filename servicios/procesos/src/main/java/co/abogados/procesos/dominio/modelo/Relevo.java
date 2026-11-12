package co.abogados.procesos.dominio.modelo;

import java.time.Instant;
import java.time.LocalDate;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Relevo del abogado responsable de un proceso: otro abogado lo recibe (porque el anterior renunció al poder, se lo
 * revocaron, lo sustituyó, se fue de la firma, o la firma reorganizó el trabajo). Queda el motivo y el historial.
 */
public final class Relevo {

	private Relevo() {
	}

	/** Por qué deja el proceso el abogado anterior. */
	public enum Motivo {
		/** El abogado renuncia al poder (CGP art. 76). */
		RENUNCIA,
		/** El cliente le revoca el poder. */
		REVOCATORIA,
		/** El abogado sustituye el poder en otro (CGP art. 75). */
		SUSTITUCION,
		/** Otro abogado continúa el proceso porque el anterior no puede seguir (muerte, incapacidad, sanción). */
		SUCESION,
		/** El abogado deja de trabajar en la firma. */
		RETIRO_DE_LA_FIRMA,
		/** La firma redistribuye la carga de trabajo. */
		REORGANIZACION
	}

	/**
	 * @param anteriorQuedaDeApoyo true = el anterior sigue en el equipo como apoyo; false = sale del proceso
	 * @param trasladarPendientes  true = sus términos pendientes y audiencias futuras pasan al nuevo responsable
	 */
	public record Solicitud(long idNuevoResponsable, Motivo motivo, String nota, boolean anteriorQuedaDeApoyo,
			boolean trasladarPendientes) {

		public Solicitud {
			if (idNuevoResponsable <= 0) {
				throw new ReglaNegocioException("Elija el abogado que recibe el proceso");
			}
			if (motivo == null) {
				throw new ReglaNegocioException("Indique el motivo del cambio");
			}
			nota = nota == null || nota.isBlank() ? null : nota.strip();
			if (nota != null && nota.length() > 300) {
				throw new ReglaNegocioException("La nota admite máximo 300 caracteres");
			}
		}
	}

	/** Lo que pasó: quién entregó, quién recibió y cuánto trabajo pendiente cambió de manos. */
	public record Resultado(Long idAnterior, long idNuevo, int terminosTrasladados, int audienciasTrasladadas) {
	}

	/**
	 * Un periodo de una persona en el equipo de un proceso. hasta null = sigue. motivo y nota explican por qué
	 * terminó. nombre viene de gabinete (null si gabinete no respondió).
	 */
	public record Periodo(long idCambio, long idPersona, String rol, LocalDate desde, LocalDate hasta, String motivo,
			String nota, Long registradoPor, Long cerradoPor, String nombre) {

		public Periodo conNombre(String nombre) {
			return new Periodo(idCambio, idPersona, rol, desde, hasta, motivo, nota, registradoPor, cerradoPor, nombre);
		}
	}

	/** Evento proceso.reasignado: notificaciones avisa al abogado que recibe el proceso y al que lo entrega. */
	public record Reasignado(int idProceso, String radicado, String asunto, Long idAnterior, long idNuevo, String motivo,
			int terminosTrasladados, Instant ocurrido) {
	}

}
