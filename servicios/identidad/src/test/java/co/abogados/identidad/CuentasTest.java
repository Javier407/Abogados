package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.aplicacion.CuentasServicio;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.aplicacion.CuentasServicio.CambioCuenta;
import co.abogados.identidad.aplicacion.CuentasServicio.NuevaCuenta;
import co.abogados.identidad.dominio.ConflictoException;
import co.abogados.identidad.dominio.Cuenta;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.CuentasRepositorio;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;

/** F8: el administrador gestiona las cuentas; nunca queda la firma sin administrador. */
class CuentasTest {

	static final long ADMIN = 79123456L;

	CuentasEnMemoria cuentas;
	CuentasServicio servicio;
	AutenticacionTest.UsuariosEnMemoria usuarios;
	SesionesEnMemoria sesiones;
	AutenticacionTest.MutableReloj reloj;
	SesionesServicio servicioSesiones;

	@BeforeEach
	void preparar() {
		cuentas = new CuentasEnMemoria();
		cuentas.crear(ADMIN, "arodriguez", "Andrés Rodríguez", "arodriguez@firma.co", "h", Set.of("ADMIN", "SOCIO"), null);
		cuentas.crear(52345678L, "cgomez", "Catalina Gómez", "cgomez@firma.co", "h", Set.of("SOCIO"), null);
		usuarios = new AutenticacionTest.UsuariosEnMemoria();
		sesiones = new SesionesEnMemoria();
		reloj = new AutenticacionTest.MutableReloj(AutenticacionTest.AHORA);
		servicioSesiones = new SesionesServicio(sesiones, usuarios, new EmisorJwtRsa("", Duration.ofMinutes(15)), reloj,
				Duration.ofHours(12), Duration.ofDays(7));
		servicio = new CuentasServicio(cuentas, AutenticacionTest.CIFRADOR, servicioSesiones);
	}

	NuevaCuenta nueva(String usuario, String clave) {
		return new NuevaCuenta(1020304050L, usuario, "Laura Nueva", "laura@firma.co", Set.of("ABOGADO"), clave);
	}

	@Test
	void creaLaCuentaConClaveCifradaYObligaACambiarla() {
		Cuenta c = servicio.crear(nueva("  LNueva ", "Temporal2026x"), ADMIN);

		assertThat(c.usuario()).isEqualTo("lnueva");
		assertThat(c.debeCambiarClave()).isTrue();
		assertThat(cuentas.hashes.get(1020304050L)).startsWith("$2").isNotEqualTo("Temporal2026x");
		assertThat(AutenticacionTest.CIFRADOR.coincide("Temporal2026x", cuentas.hashes.get(1020304050L))).isTrue();
	}

	@Test
	void validaDatosRolesYContrasena() {
		assertThatThrownBy(() -> servicio.crear(nueva("a b", "Temporal2026x"), ADMIN)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> servicio.crear(nueva("laura", "corta1"), ADMIN)).hasMessageContaining("10 caracteres");
		assertThatThrownBy(() -> servicio.crear(nueva("laura", "Prueba2026*"), ADMIN)).hasMessageContaining("pruebas");
		assertThatThrownBy(() -> servicio.crear(new NuevaCuenta(5L, "laura", "Laura", "laura@firma.co",
				Set.of("JEFE"), "Temporal2026x"), ADMIN)).hasMessageContaining("Rol desconocido");
		assertThatThrownBy(() -> servicio.crear(new NuevaCuenta(5L, "laura", "Laura", "laura@firma.co",
				Set.of("CLIENTE", "ABOGADO"), "Temporal2026x"), ADMIN)).hasMessageContaining("cliente");
		assertThatThrownBy(() -> servicio.crear(new NuevaCuenta(6L, "cgomez", "Otra", "otra@firma.co",
				Set.of("ABOGADO"), "Temporal2026x"), ADMIN)).isInstanceOf(ConflictoException.class);
	}

	@Test
	void nadiePuedeQuitarseElRolDeAdministradorNiDesactivarse() {
		assertThatThrownBy(() -> servicio.actualizar(ADMIN, new CambioCuenta("arodriguez", "Andrés", "arodriguez@firma.co",
				Set.of("SOCIO"), true), ADMIN)).hasMessageContaining("a sí mismo");
		assertThatThrownBy(() -> servicio.actualizar(ADMIN, new CambioCuenta("arodriguez", "Andrés", "arodriguez@firma.co",
				Set.of("ADMIN", "SOCIO"), false), ADMIN)).hasMessageContaining("a sí mismo");
	}

	@Test
	void siempreQuedaAlMenosUnAdministradorActivo() {
		servicio.actualizar(52345678L, new CambioCuenta("cgomez", "Catalina Gómez", "cgomez@firma.co",
				Set.of("ADMIN", "SOCIO"), true), ADMIN);
		// Con dos administradores, cgomez puede quitarle el rol a arodriguez...
		servicio.actualizar(ADMIN, new CambioCuenta("arodriguez", "Andrés", "arodriguez@firma.co", Set.of("SOCIO"), true),
				52345678L);
		// ...pero ya nadie puede dejar a la firma sin administrador
		cuentas.crear(3L, "otroadmin", "Otro", "otro@firma.co", "h", Set.of("ABOGADO"), null);
		assertThatThrownBy(() -> servicio.actualizar(52345678L, new CambioCuenta("cgomez", "Catalina", "cgomez@firma.co",
				Set.of("SOCIO"), true), 3L)).hasMessageContaining("al menos un administrador");
	}

	@Test
	void restablecerLaClaveDesbloqueaYObligaACambiarla() {
		cuentas.bloqueadas.add(52345678L);
		Cuenta c = servicio.restablecerClave(52345678L, "OtraTemporal2026", ADMIN);

		assertThat(c.debeCambiarClave()).isTrue();
		assertThat(cuentas.bloqueadas).doesNotContain(52345678L);
		assertThatThrownBy(() -> servicio.restablecerClave(52345678L, "cgomez123456", ADMIN)).hasMessageContaining("usuario");
	}

	@Test
	void alEntrarConClaveTemporalLaSesionPideCambiarlaHastaQueSeCambie() {
		usuarios.datos.put(ADMIN, new Usuario(ADMIN, "arodriguez", "Andrés", "arodriguez@firma.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("ADMIN"), 0, null));
		usuarios.debenCambiar.add(ADMIN);
		AutenticacionServicio auth = new AutenticacionServicio(usuarios, AutenticacionTest.CIFRADOR,
				new SesionesServicio(new SesionesEnMemoria(), usuarios, new EmisorJwtRsa("", Duration.ofHours(8)), reloj,
						Duration.ofHours(12), Duration.ofDays(7)), reloj);

		assertThat(auth.iniciarSesion("arodriguez", "Prueba2026*", "1.2.3.4").debeCambiarClave()).isTrue();
		auth.cambiarClave(ADMIN, "Prueba2026*", "Expediente2026");
		assertThat(auth.iniciarSesion("arodriguez", "Expediente2026", "1.2.3.4").debeCambiarClave()).isFalse();
	}

	@Test
	void unaCuentaDeClienteSeEnlazaConSuFicha() {
		assertThatThrownBy(() -> servicio.crear(new NuevaCuenta(1032456781L, "cmendez", "Carolina", "c@correo.co",
				Set.of("CLIENTE"), "Temporal2026x", null), ADMIN)).hasMessageContaining("Elija el cliente");
		assertThatThrownBy(() -> servicio.crear(new NuevaCuenta(1032456781L, "cmendez", "Carolina", "c@correo.co",
				Set.of("ABOGADO"), "Temporal2026x", 6), ADMIN)).hasMessageContaining("Solo las cuentas de cliente");

		Cuenta c = servicio.crear(new NuevaCuenta(1032456781L, "cmendez", "Carolina", "c@correo.co", Set.of("CLIENTE"),
				"Temporal2026x", 6), ADMIN);

		assertThat(c.idCliente()).isEqualTo(6);
		assertThat(servicio.deCliente(6)).extracting(Cuenta::usuario).containsExactly("cmendez");
	}

	@Test
	void desactivarOCambiarRolesCortaLasSesionesAbiertas() {
		usuarios.datos.put(52345678L, new Usuario(52345678L, "cgomez", "Catalina", "cgomez@firma.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("SOCIO"), 0, null));
		String refresco = servicioSesiones.abrir(usuarios.datos.get(52345678L), "1.2.3.4").refresco();

		servicio.actualizar(52345678L, new CambioCuenta("cgomez", "Catalina Gómez", "cgomez@firma.co",
				Set.of("ABOGADO"), true), ADMIN);

		assertThat(servicioSesiones.revocados()).hasSize(1);
		assertThatThrownBy(() -> servicioSesiones.renovar(refresco, "1.2.3.4"))
				.isInstanceOf(co.abogados.identidad.dominio.SesionInvalidaException.class);
	}

	// ----------------------------------------------------------------- doble

	static class CuentasEnMemoria implements CuentasRepositorio {
		final Map<Long, Cuenta> datos = new HashMap<>();
		final Map<Long, String> hashes = new HashMap<>();
		final List<Long> bloqueadas = new ArrayList<>();

		public List<Cuenta> listar() { return datos.values().stream().sorted(Comparator.comparing(Cuenta::nombre)).toList(); }
		public Optional<Cuenta> buscar(long id) {
			Cuenta c = datos.get(id);
			return c == null ? Optional.empty() : Optional.of(new Cuenta(c.idUsuario(), c.usuario(), c.nombre(), c.email(),
					c.activo(), c.roles(), bloqueadas.contains(id) ? Instant.MAX : null, null, c.debeCambiarClave(),
					c.idCliente()));
		}
		public boolean existe(long id, String usuario, String email, Long excluir) {
			return datos.values().stream().filter(c -> excluir == null || c.idUsuario() != excluir)
					.anyMatch(c -> c.idUsuario() == id || c.usuario().equals(usuario) || c.email().equals(email));
		}
		public void crear(long id, String usuario, String nombre, String email, String hash, Set<String> roles,
				Integer cliente) {
			datos.put(id, new Cuenta(id, usuario, nombre, email, true, roles, null, null, true, cliente));
			hashes.put(id, hash);
		}
		public void actualizar(long id, String usuario, String nombre, String email, boolean activo, Set<String> roles,
				Integer cliente) {
			Cuenta c = datos.get(id);
			datos.put(id, new Cuenta(id, usuario, nombre, email, activo, roles, null, null, c.debeCambiarClave(), cliente));
		}
		public List<Cuenta> deCliente(int idCliente) {
			return datos.values().stream().filter(c -> c.activo() && Integer.valueOf(idCliente).equals(c.idCliente())).toList();
		}
		public void restablecerClave(long id, String hash) {
			hashes.put(id, hash);
			bloqueadas.remove(id);
			Cuenta c = datos.get(id);
			datos.put(id, new Cuenta(id, c.usuario(), c.nombre(), c.email(), c.activo(), c.roles(), null, null, true,
					c.idCliente()));
		}
		public void desbloquear(long id) { bloqueadas.remove(id); }
		public int administradoresActivos() {
			return (int) datos.values().stream().filter(c -> c.activo() && c.roles().contains("ADMIN")).count();
		}
	}

}
