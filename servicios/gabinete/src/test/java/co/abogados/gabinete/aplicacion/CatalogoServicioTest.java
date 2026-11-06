package co.abogados.gabinete.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Sede;
import co.abogados.gabinete.dominio.puertos.CatalogoEdicion;
import co.abogados.gabinete.dominio.puertos.CatalogoGabinete;

/** F11: la dirección administra sedes, cargos y áreas sin romper las reglas de la firma. */
class CatalogoServicioTest {

	static final Sede BOGOTA = new Sede((short) 1, "Sede Bogotá", "Bogotá", "Calle 93 # 11-26", null, true);
	static final Sede MEDELLIN = new Sede((short) 2, "Sede Medellín", "Medellín", "Carrera 43A # 1-50", null, false);

	CatalogoGabinete catalogo = mock(CatalogoGabinete.class);
	CatalogoEdicion edicion = mock(CatalogoEdicion.class);
	CatalogoServicio servicio;

	@BeforeEach
	void preparar() {
		given(catalogo.sede((short) 1)).willReturn(Optional.of(BOGOTA));
		given(catalogo.sede((short) 2)).willReturn(Optional.of(MEDELLIN));
		given(catalogo.cargos()).willReturn(List.of(new Cargo((short) 1, "SOCIO", "Socio", true)));
		given(catalogo.areas()).willReturn(List.of(new AreaPractica((short) 1, "CIVIL", "Civil", 79123456L)));
		given(edicion.guardarSede(any(), any())).willAnswer(i -> i.getArgument(1));
		given(edicion.guardarCargo(any(), any())).willAnswer(i -> i.getArgument(1));
		given(edicion.guardarArea(any(), any())).willAnswer(i -> i.getArgument(1));
		servicio = new CatalogoServicio(catalogo, edicion);
	}

	@Test
	void marcarOtraSedeComoPrincipalQuitaLaMarcaALaAnterior() {
		servicio.guardarSede((short) 2, new Sede((short) 0, " Sede  Medellín ", "Medellín", "Carrera 43A", null, true), 1L);

		var orden = inOrder(edicion);
		orden.verify(edicion).desmarcarPrincipales();
		orden.verify(edicion).guardarSede(eq((short) 2), org.mockito.ArgumentMatchers.argThat(s -> s.nombre().equals("Sede Medellín")));
	}

	@Test
	void laSedePrincipalNoSeDesmarcaNiSeBorra() {
		assertThatThrownBy(() -> servicio.guardarSede((short) 1, new Sede((short) 0, "Sede Bogotá", "Bogotá", "Calle 93", null, false), 1L))
				.hasMessageContaining("marque como principal la otra");
		assertThatThrownBy(() -> servicio.eliminarSede((short) 1, 1L)).isInstanceOf(ConflictoException.class);
		verify(edicion, never()).eliminarSede((short) 1);
		assertThatThrownBy(() -> servicio.guardarSede(null, new Sede((short) 0, "", "Cali", "x", null, false), 1L))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void losCodigosVanEnMayusculasYNoSeRepiten() {
		assertThat(servicio.guardarCargo(null, new Cargo((short) 0, "practicante", "Practicante", false), 1L).codigo())
				.isEqualTo("PRACTICANTE");
		assertThatThrownBy(() -> servicio.guardarCargo(null, new Cargo((short) 0, "socio", "Otro", true), 1L))
				.isInstanceOf(ConflictoException.class);
		assertThatThrownBy(() -> servicio.guardarCargo(null, new Cargo((short) 0, "x 1", "Raro", false), 1L))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void elDirectorDelAreaDebeSerPersonalActivo() {
		given(edicion.personalActivo(52345678L)).willReturn(true);

		assertThat(servicio.guardarArea(null, new AreaPractica((short) 0, "tributario", "Tributario", 52345678L), 1L)
				.codigoRama()).isEqualTo("TRIBUTARIO");
		assertThatThrownBy(() -> servicio.guardarArea(null, new AreaPractica((short) 0, "MINERO", "Minero", 999L), 1L))
				.hasMessageContaining("director");
		assertThatThrownBy(() -> servicio.guardarArea(null, new AreaPractica((short) 0, "CIVIL", "Otra civil", 52345678L), 1L))
				.isInstanceOf(ConflictoException.class);
	}

}
