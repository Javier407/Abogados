package co.abogados.procesos.dominio.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Filas de los reportes para la dirección de la firma (F8). */
public final class Reportes {

	private Reportes() {
	}

	/** Carga de cada abogado responsable: procesos, términos y audiencias de los próximos 30 días. */
	public record CargaAbogado(long idAbogado, int procesosActivos, int procesosSuspendidos, int terminosPendientes,
			int terminosVencidos, int audienciasProximas) {
	}

	/**
	 * Cumplimiento de los términos que vencían en el periodo, por responsable. Los cumplidos antes de F8 no tienen
	 * fecha de cumplimiento (cumplidosSinFecha): no cuentan ni a tiempo ni tarde.
	 */
	public record CumplimientoTerminos(long idAbogado, int total, int aTiempo, int tarde, int cumplidosSinFecha,
			int vencidosSinCumplir, int pendientes) {

		/** Porcentaje a tiempo sobre los que ya se pueden juzgar (a tiempo + tarde + vencidos); null si no hay. */
		public BigDecimal porcentajeATiempo() {
			int juzgables = aTiempo + tarde + vencidosSinCumplir;
			return juzgables == 0 ? null
					: BigDecimal.valueOf(aTiempo * 100L).divide(BigDecimal.valueOf(juzgables), 1, RoundingMode.HALF_UP);
		}
	}

	/** Sentencias por rama según su sentido para el cliente. */
	public record ResultadoRama(String codigoRama, String rama, int favorables, int parciales, int desfavorables,
			int sinSentido) {

		public int total() {
			return favorables + parciales + desfavorables + sinSentido;
		}
	}

}
