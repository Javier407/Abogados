package co.abogados.gabinete.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.Abogado;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.modelo.TipoPersona;

class ReglasDominioTest {

	static Cliente cliente(TipoPersona persona, TipoDocumento doc, String numero) {
		return new Cliente(null, persona, doc, numero, "  Inversiones   Andina SAS ", "", null, null, "Bogotá",
				(short) 1, null);
	}

	static Abogado abogado(String email, String tarjeta) {
		return new Abogado(1020456789L, " Laura ", "Pineda Ruiz", email, null, (short) 2, null, (short) 1, null, null,
				null, tarjeta, new BigDecimal("260000"), LocalDate.of(2018, 8, 13), true);
	}

	@Test
	void unaPersonaJuridicaSeIdentificaConNit() {
		assertThatThrownBy(() -> cliente(TipoPersona.JURIDICA, TipoDocumento.CC, "900456789"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("NIT");
		assertThatThrownBy(() -> cliente(TipoPersona.NATURAL, TipoDocumento.NIT, "900456789"))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void limpiaEspaciosYTrataLoVacioComoNoInformado() {
		Cliente c = cliente(TipoPersona.JURIDICA, TipoDocumento.NIT, " 900456789 ");
		assertThat(c.nombre()).isEqualTo("Inversiones Andina SAS");
		assertThat(c.numeroDocumento()).isEqualTo("900456789");
		assertThat(c.email()).isNull();
	}

	@Test
	void elDocumentoSoloLlevaDigitosSalvoElPasaporte() {
		assertThatThrownBy(() -> cliente(TipoPersona.JURIDICA, TipoDocumento.NIT, "900.456.789-1"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("dígito de verificación");
		assertThat(cliente(TipoPersona.NATURAL, TipoDocumento.PAS, "AB123456").numeroDocumento())
				.isEqualTo("AB123456");
	}

	@Test
	void elAbogadoNecesitaTarjetaProfesionalNumerica() {
		assertThatThrownBy(() -> abogado("lpineda@firma.co", null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("tarjeta profesional");
		assertThatThrownBy(() -> abogado("lpineda@firma.co", "TP-201")).isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void elCorreoSeValidaYSeGuardaEnMinusculas() {
		assertThat(abogado("LPineda@Firma.CO", "201334").email()).isEqualTo("lpineda@firma.co");
		assertThatThrownBy(() -> abogado("lpineda-firma.co", "201334")).isInstanceOf(ReglaNegocioException.class);
	}

}
