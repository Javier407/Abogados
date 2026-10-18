package co.abogados.procesos.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.modelo.Reportes.CumplimientoTerminos;
import co.abogados.procesos.dominio.modelo.Reportes.ResultadoRama;

class ReportesTest {

	@Test
	void elPorcentajeATiempoSoloCuentaLoQueYaSePuedeJuzgar() {
		// 6 a tiempo, 1 tarde, 1 vencido sin cumplir; los sin fecha y los pendientes no cuentan
		CumplimientoTerminos c = new CumplimientoTerminos(79123456L, 12, 6, 1, 2, 1, 2);
		assertThat(c.porcentajeATiempo()).isEqualByComparingTo("75.0");
		assertThat(new CumplimientoTerminos(1L, 2, 0, 0, 1, 0, 1).porcentajeATiempo()).isNull();
	}

	@Test
	void elTotalDeResultadosSumaTodasLasSentencias() {
		assertThat(new ResultadoRama("CIVIL", "Civil y comercial", 3, 1, 2, 1).total()).isEqualTo(7);
	}

}
