package co.abogados.procesos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.Radicado;

class RadicadoTest {

	@Test
	void desglosaLasSietePartes() {
		Radicado r = new Radicado("11001310303220230097701");

		assertThat(r.codDane()).isEqualTo("11001");
		assertThat(r.codEntidad()).isEqualTo("31");
		assertThat(r.codEspecialidad()).isEqualTo("03");
		assertThat(r.codDespacho()).isEqualTo("032");
		assertThat(r.anio()).isEqualTo(2023);
		assertThat(r.consecutivo()).isEqualTo(977);
		assertThat(r.instancia()).isEqualTo(1);
		assertThat(r.prefijoDespacho()).isEqualTo("110013103032");
		assertThat(r.legible()).isEqualTo("11001-31-03-032-2023-00977-01");
	}

	@Test
	void aceptaElFormatoConGuionesYEspacios() {
		assertThat(Radicado.de(" 11001-31-03-032-2024-00458-00 ").numero()).isEqualTo("11001310303220240045800");
		assertThat(Radicado.de("11001.31.03.032.2024.00458.00").numero()).isEqualTo("11001310303220240045800");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "1100131030322024004580", "110013103032202400458000", "11001310303220240045A00" })
	void rechazaLoQueNoSonVeintitresDigitos(String numero) {
		assertThatThrownBy(() -> new Radicado(numero))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("23 dígitos");
	}

	@Test
	void rechazaNulo() {
		assertThatThrownBy(() -> Radicado.de(null)).isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void rechazaUnAnioFueraDeRango() {
		assertThatThrownBy(() -> new Radicado("11001310303218900045800"))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("1890");
	}

}
