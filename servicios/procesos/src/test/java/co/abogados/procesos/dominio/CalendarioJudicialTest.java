package co.abogados.procesos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.CalendarioJudicial;

/** Casos con el calendario real de 2026 (los mismos días cargados en procesos.festivos). */
class CalendarioJudicialTest {

	static final CalendarioJudicial CALENDARIO = new CalendarioJudicial(inhabiles(), LocalDate.of(2027, 12, 31));

	static Set<LocalDate> inhabiles() {
		Set<LocalDate> dias = new HashSet<>(Set.of(
				LocalDate.of(2026, 3, 30), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),   // Semana Santa
				LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3),                                // Jueves y Viernes Santo
				LocalDate.of(2026, 10, 12),                                                        // Día de la Raza
				LocalDate.of(2027, 1, 11)));                                                       // Reyes 2027
		// Vacancia judicial 20 de diciembre de 2026 al 10 de enero de 2027
		Stream.iterate(LocalDate.of(2026, 12, 20), d -> !d.isAfter(LocalDate.of(2027, 1, 10)), d -> d.plusDays(1))
				.forEach(dias::add);
		return dias;
	}

	static LocalDate dia(int mes, int dia) {
		return LocalDate.of(2026, mes, dia);
	}

	@Test
	void elConteoEmpiezaElDiaHabilSiguienteALaNotificacion() {
		// Notificado el lunes 5 de octubre: 3 días = martes, miércoles, jueves
		assertThat(CALENDARIO.sumarDiasHabiles(dia(10, 5), 3)).isEqualTo(dia(10, 8));
	}

	@Test
	void saltaFinesDeSemanaYFestivos() {
		// Viernes 9 de octubre + 1 día: sábado, domingo y lunes 12 (festivo) no cuentan
		assertThat(CALENDARIO.sumarDiasHabiles(dia(10, 9), 1)).isEqualTo(dia(10, 13));
		// Notificado el jueves 15: 3 días = viernes 16, lunes 19, martes 20
		assertThat(CALENDARIO.sumarDiasHabiles(dia(10, 15), 3)).isEqualTo(dia(10, 20));
	}

	@Test
	void semanaSantaNoCuenta() {
		// Viernes 27 de marzo + 1: toda la semana del 30 de marzo al 3 de abril es inhábil
		assertThat(CALENDARIO.sumarDiasHabiles(dia(3, 27), 1)).isEqualTo(dia(4, 6));
	}

	@Test
	void laVacanciaJudicialSuspendeLosTerminos() {
		// Viernes 18 de diciembre + 2: vacancia hasta el 10 de enero y el 11 es festivo de Reyes
		assertThat(CALENDARIO.sumarDiasHabiles(dia(12, 18), 2)).isEqualTo(LocalDate.of(2027, 1, 13));
	}

	@Test
	void cuentaLosDiasHabilesRestantesEnAmbosSentidos() {
		assertThat(CALENDARIO.diasHabilesEntre(dia(10, 15), dia(10, 20))).isEqualTo(3);
		assertThat(CALENDARIO.diasHabilesEntre(dia(10, 15), dia(10, 15))).isZero();
		// Del viernes 9 al jueves 15: cuentan martes 13, miércoles 14 y jueves 15 (el lunes 12 es festivo)
		assertThat(CALENDARIO.diasHabilesEntre(dia(10, 15), dia(10, 9))).isEqualTo(-3);
	}

	@Test
	void esHabilDistingueFinesDeSemanaYFestivos() {
		assertThat(CALENDARIO.esHabil(dia(10, 13))).isTrue();
		assertThat(CALENDARIO.esHabil(dia(10, 12))).isFalse();
		assertThat(CALENDARIO.esHabil(dia(10, 17))).isFalse();
	}

	@Test
	void noCalculaMasAllaDeLosFestivosCargados() {
		assertThatThrownBy(() -> CALENDARIO.sumarDiasHabiles(LocalDate.of(2027, 12, 28), 10))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("2027-12-31");
	}

	@Test
	void unTerminoTieneAlMenosUnDia() {
		assertThatThrownBy(() -> CALENDARIO.sumarDiasHabiles(dia(10, 15), 0)).isInstanceOf(ReglaNegocioException.class);
	}

}
