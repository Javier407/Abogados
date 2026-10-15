package co.abogados.gabinete.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.gabinete.dominio.modelo.Abogado;
import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.Sede;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.modelo.TipoPersona;
import co.abogados.gabinete.dominio.puertos.AbogadoRepositorio;
import co.abogados.gabinete.dominio.puertos.CatalogoGabinete;
import co.abogados.gabinete.dominio.puertos.ClienteRepositorio;

/** Casos de uso de clientes y abogados con dobles en memoria. */
class ServiciosTest {

	ClientesEnMemoria clientes;
	AbogadosEnMemoria abogados;
	Map<Integer, Integer> procesosPorCliente;
	boolean procesosCaido;
	ClienteServicio clienteServicio;
	AbogadoServicio abogadoServicio;

	@BeforeEach
	void preparar() {
		clientes = new ClientesEnMemoria();
		abogados = new AbogadosEnMemoria();
		procesosPorCliente = new HashMap<>();
		Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Bogota"));
		clienteServicio = new ClienteServicio(clientes, new CatalogoFijo(), id -> {
			if (procesosCaido) throw new ServicioNoDisponibleException("procesos no responde");
			return procesosPorCliente.getOrDefault(id, 0);
		}, reloj);
		abogadoServicio = new AbogadoServicio(abogados, new CatalogoFijo());
	}

	static Cliente natural(String cedula) {
		return new Cliente(null, TipoPersona.NATURAL, TipoDocumento.CC, cedula, "María Fernanda López", null, null,
				null, "Bogotá", (short) 1, null);
	}

	static Abogado abogado(long cedula, String email, String tarjeta, short cargo) {
		return new Abogado(cedula, "Laura", "Pineda", email, null, cargo, null, (short) 1, null, null, null, tarjeta,
				BigDecimal.TEN, LocalDate.of(2020, 1, 1), true);
	}

	@Test
	void creaUnClienteConLaFechaDeHoy() {
		Cliente c = clienteServicio.crear(natural("52678123"));

		assertThat(c.idCliente()).isNotNull();
		assertThat(c.creado()).isEqualTo(LocalDate.of(2026, 10, 15));
	}

	@Test
	void noRepiteElDocumentoDeUnCliente() {
		clienteServicio.crear(natural("52678123"));

		assertThatThrownBy(() -> clienteServicio.crear(natural("52678123"))).isInstanceOf(ConflictoException.class)
				.hasMessageContaining("CC 52678123");
	}

	@Test
	void actualizarConservaLaFechaDeCreacionYPermiteSuPropioDocumento() {
		Cliente c = clienteServicio.crear(natural("52678123"));
		Cliente datos = new Cliente(null, TipoPersona.NATURAL, TipoDocumento.CC, "52678123", "María F. López",
				"mf@correo.co", null, null, "Medellín", (short) 2, null);

		Cliente actualizado = clienteServicio.actualizar(c.idCliente(), datos);

		assertThat(actualizado.ciudad()).isEqualTo("Medellín");
		assertThat(actualizado.creado()).isEqualTo(c.creado());
	}

	@Test
	void laSedeDelClienteDebeExistir() {
		Cliente enSedeInexistente = new Cliente(null, TipoPersona.NATURAL, TipoDocumento.CC, "52678123", "Ana", null,
				null, null, "Cali", (short) 9, null);

		assertThatThrownBy(() -> clienteServicio.crear(enSedeInexistente)).isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void noSeEliminaUnClienteConProcesos() {
		Cliente c = clienteServicio.crear(natural("52678123"));
		procesosPorCliente.put(c.idCliente(), 2);

		assertThatThrownBy(() -> clienteServicio.eliminar(c.idCliente())).isInstanceOf(ConflictoException.class)
				.hasMessageContaining("2 proceso(s)");
	}

	@Test
	void siProcesosNoRespondeNoSeEliminaElCliente() {
		Cliente c = clienteServicio.crear(natural("52678123"));
		procesosCaido = true;

		assertThatThrownBy(() -> clienteServicio.eliminar(c.idCliente()))
				.isInstanceOf(ServicioNoDisponibleException.class);
		assertThat(clientes.datos).containsKey(c.idCliente());
	}

	@Test
	void eliminaUnClienteSinHistoria() {
		Cliente c = clienteServicio.crear(natural("52678123"));

		clienteServicio.eliminar(c.idCliente());

		assertThatThrownBy(() -> clienteServicio.obtener(c.idCliente()))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void registraUnAbogado() {
		Abogado a = abogadoServicio.crear(abogado(1020456789L, "lpineda@firma.co", "201334", (short) 2));

		assertThat(abogadoServicio.obtener(1020456789L)).isEqualTo(a);
	}

	@Test
	void noRepiteCedulaCorreoNiTarjeta() {
		abogadoServicio.crear(abogado(1020456789L, "lpineda@firma.co", "201334", (short) 2));

		assertThatThrownBy(() -> abogadoServicio.crear(abogado(1020456789L, "otro@firma.co", "999999", (short) 2)))
				.isInstanceOf(ConflictoException.class).hasMessageContaining("cédula");
		assertThatThrownBy(() -> abogadoServicio.crear(abogado(11L, "lpineda@firma.co", "999999", (short) 2)))
				.isInstanceOf(ConflictoException.class).hasMessageContaining("correo");
		assertThatThrownBy(() -> abogadoServicio.crear(abogado(11L, "otro@firma.co", "201334", (short) 2)))
				.isInstanceOf(ConflictoException.class).hasMessageContaining("tarjeta");
	}

	@Test
	void elCargoDebeSerDeAbogado() {
		assertThatThrownBy(() -> abogadoServicio.crear(abogado(11L, "sec@firma.co", "123456", (short) 6)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es de abogado");
	}

	@Test
	void desactivarNoBorraAlAbogado() {
		abogadoServicio.crear(abogado(1020456789L, "lpineda@firma.co", "201334", (short) 2));

		Abogado inactivo = abogadoServicio.desactivar(1020456789L);

		assertThat(inactivo.activo()).isFalse();
		assertThat(abogadoServicio.listar(true)).isEmpty();
		assertThat(abogadoServicio.listar(false)).hasSize(1);
	}

	@Test
	void laCedulaNoSeCambiaAlActualizar() {
		abogadoServicio.crear(abogado(1020456789L, "lpineda@firma.co", "201334", (short) 2));

		assertThatThrownBy(() -> abogadoServicio.actualizar(1020456789L,
				abogado(99L, "lpineda@firma.co", "201334", (short) 2))).isInstanceOf(ReglaNegocioException.class);
	}

	// ----------------------------------------------------------------- dobles

	static class CatalogoFijo implements CatalogoGabinete {
		public List<Sede> sedes() {
			return List.of(new Sede((short) 1, "Sede Bogotá", "Bogotá", "Cra 7", null, true),
					new Sede((short) 2, "Sede Medellín", "Medellín", "Cra 43A", null, false));
		}
		public List<Cargo> cargos() {
			return List.of(new Cargo((short) 2, "ASOCIADO", "Abogado asociado", true),
					new Cargo((short) 6, "SECRETARIA", "Secretaria", false));
		}
		public List<AreaPractica> areas() { return List.of(); }
		public Optional<Sede> sede(short id) { return sedes().stream().filter(s -> s.idSede() == id).findFirst(); }
		public Optional<Cargo> cargo(short id) { return cargos().stream().filter(c -> c.idCargo() == id).findFirst(); }
		public Optional<AreaPractica> area(short id) { return Optional.empty(); }
	}

	static class ClientesEnMemoria implements ClienteRepositorio {
		final Map<Integer, Cliente> datos = new HashMap<>();
		int secuencia;

		public List<Cliente> listar(String texto) { return List.copyOf(datos.values()); }
		public Optional<Cliente> buscar(int id) { return Optional.ofNullable(datos.get(id)); }
		public Optional<Integer> idPorDocumento(TipoDocumento tipo, String numero) {
			return datos.values().stream()
					.filter(c -> c.tipoDocumento() == tipo && c.numeroDocumento().equals(numero))
					.map(Cliente::idCliente).findFirst();
		}
		public Cliente guardar(Cliente c) {
			Cliente guardado = c.idCliente() == null ? c.conId(++secuencia, c.creado()) : c;
			datos.put(guardado.idCliente(), guardado);
			return guardado;
		}
		public int contarHonorarios(int id) { return 0; }
		public void eliminar(int id) { datos.remove(id); }
	}

	static class AbogadosEnMemoria implements AbogadoRepositorio {
		final Map<Long, Abogado> datos = new HashMap<>();

		public List<Abogado> listar(boolean soloActivos) {
			return datos.values().stream().filter(a -> !soloActivos || a.activo()).toList();
		}
		public Optional<Abogado> buscar(long id) { return Optional.ofNullable(datos.get(id)); }
		public boolean existePersona(long id) { return datos.containsKey(id); }
		public Optional<Long> idPorEmail(String email) {
			return datos.values().stream().filter(a -> a.email().equals(email)).map(Abogado::idPersona).findFirst();
		}
		public Optional<Long> idPorTarjeta(String t) {
			return datos.values().stream().filter(a -> a.tarjetaProfesional().equals(t)).map(Abogado::idPersona)
					.findFirst();
		}
		public Abogado guardar(Abogado a) { datos.put(a.idPersona(), a); return a; }
	}

}
