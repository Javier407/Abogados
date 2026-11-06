package co.abogados.procesos.dominio.modelo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Días en que corren los términos judiciales: lunes a viernes que no sean festivo, vacancia judicial
 * ni Semana Santa. Hace las mismas cuentas que las funciones SQL procesos.sumar_dias_habiles y
 * procesos.dias_habiles_entre.
 *
 * Solo responde hasta {@code cubreHasta}: más allá no hay festivos cargados y un fin de semana
 * largo pasaría como hábil, así que se rechaza en lugar de dar una fecha equivocada.
 */
public final class CalendarioJudicial {

	private final Set<LocalDate> inhabiles;
	private final LocalDate cubreHasta;

	public CalendarioJudicial(Set<LocalDate> inhabiles, LocalDate cubreHasta) {
		this.inhabiles = Set.copyOf(inhabiles);
		this.cubreHasta = cubreHasta;
	}

	public LocalDate cubreHasta() {
		return cubreHasta;
	}

	public boolean esHabil(LocalDate dia) {
		DayOfWeek d = dia.getDayOfWeek();
		return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY && !inhabiles.contains(dia);
	}

	/**
	 * Fecha en que vence un término de n días hábiles notificado el día {@code desde}:
	 * el conteo empieza el día hábil siguiente.
	 */
	public LocalDate sumarDiasHabiles(LocalDate desde, int dias) {
		if (dias <= 0) {
			throw new ReglaNegocioException("El término debe tener al menos un día hábil");
		}
		LocalDate d = desde;
		int contados = 0;
		while (contados < dias) {
			d = d.plusDays(1);
			verificarCobertura(d);
			if (esHabil(d)) {
				contados++;
			}
		}
		return d;
	}

	/** Días hábiles en el intervalo (desde, hasta]. Negativo si hasta es anterior a desde. */
	public int diasHabilesEntre(LocalDate desde, LocalDate hasta) {
		if (hasta.isBefore(desde)) {
			return -diasHabilesEntre(hasta, desde);
		}
		verificarCobertura(hasta);
		int n = 0;
		for (LocalDate d = desde.plusDays(1); !d.isAfter(hasta); d = d.plusDays(1)) {
			if (esHabil(d)) {
				n++;
			}
		}
		return n;
	}

	private void verificarCobertura(LocalDate dia) {
		if (dia.isAfter(cubreHasta)) {
			throw new ReglaNegocioException("El calendario judicial solo tiene festivos cargados hasta "
					+ cubreHasta + "; agregue los del año siguiente en procesos.festivos");
		}
	}

}
