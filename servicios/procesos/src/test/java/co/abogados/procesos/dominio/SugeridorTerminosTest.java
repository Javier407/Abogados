package co.abogados.procesos.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.ProvidenciaVista;
import co.abogados.procesos.dominio.modelo.ReglaTermino;
import co.abogados.procesos.dominio.modelo.SugerenciaTermino;
import co.abogados.procesos.dominio.modelo.SugeridorTerminos;

/** Términos sugeridos al llegar una providencia (las mismas reglas que carga la migración 005). */
class SugeridorTerminosTest {

	static final List<ReglaTermino> REGLAS = List.of(
			new ReglaTermino(1, "AUTO", null, null, "\\badmite|admisorio", "DEMANDADO", "Contestar la demanda", null,
					"Término de contestación del tipo de proceso", 1, false),
			new ReglaTermino(2, "AUTO", null, null, "inadmite", "DEMANDANTE", "Subsanar la demanda", 5, "CGP art. 90", 1, false),
			new ReglaTermino(4, "AUTO", "CIVIL", null, ".", null, "Recurso de reposición contra el auto (si procede)", 3,
					"CGP art. 318", 2, false),
			new ReglaTermino(8, "SENTENCIA", "CONSTITUCIONAL", (short) 32, ".", null,
					"Impugnar el fallo de tutela (si es desfavorable)", 3, "Decreto 2591 de 1991 art. 31", 1, false));

	static ProcesoVista proceso(short idTipo, String rama, String rolCliente) {
		return new ProcesoVista(1, "11001400301520260077700", "11001-40-03-015-2026-00777-00", idTipo, "Tipo", rama, rama,
				"Juzgado", null, "Juzgado", 0, 30, rolCliente, "Asunto", BigDecimal.ONE, "MENOR", "REPRESENTACION",
				"ACTIVO", "ADMISION", LocalDate.of(2026, 10, 1), true, 79123456L);
	}

	static ProvidenciaVista providencia(String tipo, String clase, String decision, String sentido) {
		return new ProvidenciaVista(5, 1, tipo, clase, LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15), decision,
				sentido, "MANUAL");
	}

	/** Vencimiento de juguete: el número de días sumado a la notificación (el calendario real se prueba aparte). */
	static List<SugerenciaTermino> sugerir(ProcesoVista p, ProvidenciaVista pr, Integer diasContestacion, List<String> creados) {
		return SugeridorTerminos.sugerir(p, pr, REGLAS, diasContestacion, creados, d -> pr.fechaEstado().plusDays(d));
	}

	@Test
	void autoQueAdmiteLaDemandaEnUnVerbalSugiereContestarEnVeinteDiasAlDemandado() {
		List<SugerenciaTermino> s = sugerir(proceso((short) 1, "CIVIL", "DEMANDADO"),
				providencia("AUTO", "Auto admite la demanda", null, null), 20, List.of());

		assertThat(s).extracting(SugerenciaTermino::descripcion)
				.containsExactly("Contestar la demanda", "Recurso de reposición contra el auto (si procede)");
		assertThat(s.get(0).diasHabiles()).isEqualTo(20);
		assertThat(s.get(0).fechaInicio()).isEqualTo(LocalDate.of(2026, 10, 15));      // desde la notificación por estado
		assertThat(s.get(0).validada()).isFalse();                                     // la regla está "a validar"
		assertThat(s.get(1).prioridad()).isEqualTo(2);
	}

	@Test
	void alDemandanteNoSeLeSugiereContestarYUnInadmiteNoSeConfundeConAdmite() {
		ProcesoVista demandante = proceso((short) 1, "CIVIL", "DEMANDANTE");
		assertThat(sugerir(demandante, providencia("AUTO", "Admite demanda", null, null), 20, List.of()))
				.extracting(SugerenciaTermino::descripcion).doesNotContain("Contestar la demanda");

		List<SugerenciaTermino> inadmite = sugerir(demandante, providencia("AUTO", "Auto INADMITE la demanda", null, null),
				20, List.of());
		assertThat(inadmite).extracting(SugerenciaTermino::descripcion).contains("Subsanar la demanda")
				.doesNotContain("Contestar la demanda");
	}

	@Test
	void sinDiasDeContestacionDelTipoNoSeSugiereContestar() {
		assertThat(sugerir(proceso((short) 8, "FAMILIA", "DEMANDADO"), providencia("AUTO", "Admite demanda", null, null),
				null, List.of())).isEmpty();
	}

	@Test
	void elPlazoEscritoEnLaProvidenciaSeSugierePrimero() {
		List<SugerenciaTermino> s = sugerir(proceso((short) 1, "CIVIL", "DEMANDANTE"),
				providencia("AUTO", "Auto requiere al demandante", "Allegue el poder en 5 días so pena de rechazo", null),
				20, List.of());

		assertThat(s.get(0).descripcion()).isEqualTo("Cumplir lo ordenado: Auto requiere al demandante");
		assertThat(s.get(0).diasHabiles()).isEqualTo(5);
		assertThat(s.get(0).origen()).isEqualTo("PROVIDENCIA");
		assertThat(s.get(0).validada()).isTrue();
	}

	@Test
	void noRepiteLoQueYaTieneTermino() {
		assertThat(sugerir(proceso((short) 1, "CIVIL", "DEMANDADO"), providencia("AUTO", "Admite demanda", null, null), 20,
				List.of("CONTESTAR LA DEMANDA")))
				.extracting(SugerenciaTermino::descripcion).doesNotContain("Contestar la demanda");
	}

	@Test
	void unaSentenciaFavorableDejaLaImpugnacionComoOpcional() {
		ProcesoVista tutela = proceso((short) 32, "CONSTITUCIONAL", "ACCIONANTE");
		assertThat(sugerir(tutela, providencia("SENTENCIA", "Fallo de primera instancia", null, "DESFAVORABLE"), null, List.of()))
				.singleElement().satisfies(x -> assertThat(x.prioridad()).isEqualTo(1));
		assertThat(sugerir(tutela, providencia("SENTENCIA", "Fallo de primera instancia", null, "FAVORABLE"), null, List.of()))
				.singleElement().satisfies(x -> assertThat(x.prioridad()).isEqualTo(2));
		assertThat(sugerir(proceso((short) 33, "CONSTITUCIONAL", "ACCIONANTE"),
				providencia("SENTENCIA", "Fallo", null, "DESFAVORABLE"), null, List.of())).isEmpty();   // otra acción, no tutela
	}

}
