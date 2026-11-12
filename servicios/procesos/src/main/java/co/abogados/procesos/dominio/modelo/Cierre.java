package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Cierre de un proceso: por qué terminó para la firma, cuándo y una nota. Al cerrarlo, sus términos pendientes y
 * audiencias dejan de avisar y de salir en la agenda, y deja de vigilarse.
 */
public record Cierre(Motivo motivo, LocalDate fecha, String nota) {

	public enum Motivo {
		/** Sentencia en firme. */
		SENTENCIA,
		/** Sentencia anticipada (CGP art. 278). */
		SENTENCIA_ANTICIPADA,
		CONCILIACION,
		TRANSACCION,
		/** El demandante desiste de las pretensiones (CGP art. 314). */
		DESISTIMIENTO,
		/** Se retira la demanda antes de notificarla (CGP art. 92). */
		RETIRO_DE_LA_DEMANDA,
		/** Pago total de la obligación (procesos ejecutivos). */
		PAGO,
		/** Terminó la liquidación (del crédito, de la sociedad o de la sucesión). */
		LIQUIDACION,
		/** La firma deja el caso: renunció al poder o el cliente cambió de abogado. */
		RENUNCIA_AL_PODER,
		/** Se archiva sin decisión de fondo (por ejemplo, desistimiento tácito). */
		ARCHIVO,
		OTRO
	}

	public Cierre {
		if (motivo == null) {
			throw new ReglaNegocioException("Indique por qué se cierra el proceso");
		}
		if (fecha == null) {
			throw new ReglaNegocioException("Indique la fecha del cierre");
		}
		nota = nota == null || nota.isBlank() ? null : nota.strip();
		if (nota != null && nota.length() > 300) {
			throw new ReglaNegocioException("La nota admite máximo 300 caracteres");
		}
		if (motivo == Motivo.OTRO && nota == null) {
			throw new ReglaNegocioException("Con el motivo «Otro», explique en la nota por qué se cierra");
		}
	}

	/** Un proceso archivado sin decisión queda ARCHIVADO; los demás, TERMINADO. */
	public EstadoProceso estado() {
		return motivo == Motivo.ARCHIVO ? EstadoProceso.ARCHIVADO : EstadoProceso.TERMINADO;
	}

	/** Lo que el cierre dejó sin efecto: términos que ya no avisan y audiencias que salen de la agenda. */
	public record Efectos(int terminosPendientes, int audienciasFuturas, boolean seApagoLaVigilancia) {
	}

	/** El proceso ya cerrado y lo que el cierre dejó sin efecto. */
	public record Resultado(ProcesoVista proceso, Efectos efectos) {
	}

}
