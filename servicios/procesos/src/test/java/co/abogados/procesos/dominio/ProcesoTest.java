package co.abogados.procesos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.ClaseCuantia;
import co.abogados.procesos.dominio.modelo.EstadoProceso;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.Proceso;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.modelo.RolCliente;
import co.abogados.procesos.dominio.modelo.TipoServicio;

class ProcesoTest {

	static Proceso proceso(String radicado, EstadoProceso estado, EtapaProceso etapa, ClaseCuantia clase,
			BigDecimal cuantia, LocalDate fecha) {
		return new Proceso(null, Radicado.de(radicado), (short) 1, null, 1, RolCliente.DEMANDANTE,
				"  Cobro de facturas  ", cuantia, clase, TipoServicio.REPRESENTACION, estado, etapa, fecha, true,
				79123456L);
	}

	static Proceso valido() {
		return proceso("11001310303220260012300", null, EtapaProceso.DEMANDA, ClaseCuantia.MAYOR,
				new BigDecimal("250000000"), LocalDate.of(2026, 3, 2));
	}

	@Test
	void unProcesoNuevoQuedaActivoYConElAsuntoLimpio() {
		Proceso p = valido();
		assertThat(p.estado()).isEqualTo(EstadoProceso.ACTIVO);
		assertThat(p.asunto()).isEqualTo("Cobro de facturas");
	}

	@Test
	void unProcesoTerminadoDebeEstarEnEtapaCerrado() {
		assertThatThrownBy(() -> proceso("11001310303220260012300", EstadoProceso.TERMINADO, EtapaProceso.SENTENCIA,
				ClaseCuantia.MAYOR, null, LocalDate.of(2026, 3, 2)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("CERRADO");
	}

	@Test
	void laEtapaCerradoSoloAplicaAProcesosCerrados() {
		assertThatThrownBy(() -> proceso("11001310303220260012300", EstadoProceso.ACTIVO, EtapaProceso.CERRADO,
				ClaseCuantia.MAYOR, null, LocalDate.of(2026, 3, 2)))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void unProcesoSinCuantiaNoLlevaValor() {
		assertThatThrownBy(() -> proceso("11001310303220260012300", null, EtapaProceso.DEMANDA,
				ClaseCuantia.SIN_CUANTIA, BigDecimal.TEN, LocalDate.of(2026, 3, 2)))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void enPrimeraInstanciaElAnioDelRadicadoEsElDeRadicacion() {
		assertThatThrownBy(() -> proceso("11001310303220260012300", null, EtapaProceso.DEMANDA, ClaseCuantia.MAYOR,
				null, LocalDate.of(2025, 12, 30)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no coincide");
	}

	@Test
	void enSegundaInstanciaElRadicadoConservaElAnioOriginal() {
		Proceso p = proceso("11001310303220230097701", null, EtapaProceso.RECURSO, ClaseCuantia.MAYOR, null,
				LocalDate.of(2025, 10, 21));
		assertThat(p.radicado().instancia()).isEqualTo(1);
	}

}
