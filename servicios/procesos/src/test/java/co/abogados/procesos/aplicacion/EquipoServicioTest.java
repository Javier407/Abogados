package co.abogados.procesos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.Asignacion;
import co.abogados.procesos.dominio.puertos.AsignacionRepositorio;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;

/** F11: abogados de apoyo y dependientes en el equipo del proceso. */
class EquipoServicioTest {

	static final long RESPONSABLE = 52345678L, APOYO = 1020456789L, INACTIVO = 43567890L;

	EquipoEnMemoria repo = new EquipoEnMemoria();
	EquipoServicio servicio;

	@BeforeEach
	void preparar() {
		ProcesoRepositorio procesos = mock(ProcesoRepositorio.class);
		given(procesos.buscar(34)).willReturn(Optional.of(DocumentoServicioTest.proceso(34, 6)));
		DirectorioGabinete gabinete = mock(DirectorioGabinete.class);
		given(gabinete.abogado(RESPONSABLE)).willReturn(Optional.of(new DirectorioGabinete.Abogado(RESPONSABLE, "Catalina", "Gómez", true)));
		given(gabinete.abogado(APOYO)).willReturn(Optional.of(new DirectorioGabinete.Abogado(APOYO, "Laura", "Pineda", true)));
		given(gabinete.abogado(INACTIVO)).willReturn(Optional.of(new DirectorioGabinete.Abogado(INACTIVO, "Diana", "Ochoa", false)));
		repo.agregar(34, RESPONSABLE, Asignacion.Rol.RESPONSABLE);
		servicio = new EquipoServicio(repo, procesos, gabinete);
	}

	@Test
	void agregaApoyoConSuNombreYNoLoRepite() {
		Asignacion a = servicio.agregar(34, APOYO, Asignacion.Rol.APOYO, 1L);

		assertThat(a.nombre()).isEqualTo("Laura Pineda");
		assertThat(servicio.equipo(34)).extracting(Asignacion::rol).containsExactly(Asignacion.Rol.RESPONSABLE, Asignacion.Rol.APOYO);
		assertThatThrownBy(() -> servicio.agregar(34, APOYO, Asignacion.Rol.DEPENDIENTE, 1L)).isInstanceOf(ConflictoException.class);
	}

	@Test
	void soloPersonalActivoYNuncaComoResponsable() {
		assertThatThrownBy(() -> servicio.agregar(34, INACTIVO, Asignacion.Rol.APOYO, 1L)).hasMessageContaining("inactivo");
		assertThatThrownBy(() -> servicio.agregar(34, 999L, Asignacion.Rol.APOYO, 1L)).hasMessageContaining("No existe");
		assertThatThrownBy(() -> servicio.agregar(34, APOYO, Asignacion.Rol.RESPONSABLE, 1L))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("editando el proceso");
	}

	@Test
	void seQuitaElApoyoPeroNoElResponsable() {
		servicio.agregar(34, APOYO, Asignacion.Rol.APOYO, 1L);

		servicio.quitar(34, APOYO, 1L);

		assertThat(servicio.equipo(34)).hasSize(1);
		assertThatThrownBy(() -> servicio.quitar(34, RESPONSABLE, 1L)).hasMessageContaining("no se quita");
	}

	static class EquipoEnMemoria implements AsignacionRepositorio {
		final List<Asignacion> datos = new ArrayList<>();

		public List<Asignacion> listar(int p) {
			return datos.stream().filter(a -> a.idProceso() == p).sorted((x, y) -> x.rol().compareTo(y.rol())).toList();
		}
		public Optional<Asignacion> buscar(int p, long q) {
			return datos.stream().filter(a -> a.idProceso() == p && a.idPersona() == q).findFirst();
		}
		public void agregar(int p, long q, Asignacion.Rol rol) {
			datos.add(new Asignacion(p, q, rol, LocalDate.now(), null));
		}
		public void quitar(int p, long q) {
			datos.removeIf(a -> a.idProceso() == p && a.idPersona() == q && a.rol() != Asignacion.Rol.RESPONSABLE);
		}
	}

}
