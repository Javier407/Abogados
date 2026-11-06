package co.abogados.gabinete.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.Perito;
import co.abogados.gabinete.dominio.modelo.Smmlv;
import co.abogados.gabinete.dominio.puertos.PeritoRepositorio;
import co.abogados.gabinete.dominio.puertos.TarifaRepositorio;

class PeritoYTarifaServicioTest {

	static final Perito CLAUDIA = new Perito(3, "Claudia Ruiz", "Ingeniería civil", "Ingeniera civil", "IC-123", null,
			"cruiz@peritos.co", "Bogotá", new BigDecimal("4500000"), (short) 5);

	PeritoRepositorio peritos = mock(PeritoRepositorio.class);
	PeritoServicio servicioPeritos = new PeritoServicio(peritos);

	TarifaRepositorio tarifas = mock(TarifaRepositorio.class);
	TarifaServicio servicioTarifas = new TarifaServicio(tarifas,
			Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneId.of("America/Bogota")));

	@Test
	void crearUnPeritoIgnoraElIdQueTraiga() {
		given(peritos.guardar(any())).willAnswer(i -> ((Perito) i.getArgument(0)).conId(8));

		assertThat(servicioPeritos.crear(CLAUDIA).idPerito()).isEqualTo(8);
		verify(peritos).guardar(argThat(p -> p.idPerito() == null && p.nombre().equals("Claudia Ruiz")));
	}

	@Test
	void actualizarUnPeritoInexistenteEsUn404() {
		given(peritos.buscar(99)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicioPeritos.actualizar(99, CLAUDIA))
				.isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe el perito 99");
		verify(peritos, never()).guardar(any());
	}

	@Test
	void actualizarUsaElIdDeLaRuta() {
		given(peritos.buscar(3)).willReturn(Optional.of(CLAUDIA));
		given(peritos.guardar(any())).willAnswer(i -> i.getArgument(0));
		given(peritos.listar("civil")).willReturn(List.of(CLAUDIA));

		assertThat(servicioPeritos.actualizar(3, CLAUDIA.conId(77)).idPerito()).isEqualTo(3);
		assertThat(servicioPeritos.obtener(3)).isEqualTo(CLAUDIA);
		assertThat(servicioPeritos.listar("civil")).containsExactly(CLAUDIA);
	}

	@Test
	void reglasDelPerito() {
		assertThatThrownBy(() -> new Perito(null, "Claudia", "Civil", "Ingeniera", null, null, "no-es-correo", "Bogotá",
				null, null)).hasMessage("El correo del perito no es válido");
		assertThatThrownBy(() -> new Perito(null, "Claudia", "Civil", "Ingeniera", null, null, null, "Bogotá",
				new BigDecimal("-1"), null)).hasMessage("La tarifa del dictamen no puede ser negativa");
		assertThatThrownBy(() -> new Perito(null, "Claudia", "Civil", "Ingeniera", null, null, null, "Bogotá",
				null, (short) 0)).hasMessage("La calificación va de 1 a 5");
		assertThatThrownBy(() -> new Perito(null, " ", "Civil", "Ingeniera", null, null, null, "Bogotá", null, null))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void lasTarifasSinAnioUsanElDelRelojYElSalarioQueHaya() {
		given(tarifas.smmlv(2026)).willReturn(Optional.of(new Smmlv(2026, new BigDecimal("1750905"))));
		given(tarifas.smmlv(2031)).willReturn(Optional.of(new Smmlv(2026, new BigDecimal("1750905"))));

		servicioTarifas.listar(null);
		servicioTarifas.listar(2031);

		verify(tarifas, org.mockito.Mockito.times(2)).listar(2026, new BigDecimal("1750905"));
	}

	@Test
	void sinSalarioMinimoCargadoNoHayTarifas() {
		given(tarifas.smmlv(2026)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicioTarifas.listar(2026))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("salario mínimo");
	}

}
