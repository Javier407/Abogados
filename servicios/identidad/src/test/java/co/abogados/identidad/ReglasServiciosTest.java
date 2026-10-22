package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.aplicacion.CuentasServicio;
import co.abogados.identidad.aplicacion.CuentasServicio.CambioCuenta;
import co.abogados.identidad.aplicacion.CuentasServicio.NuevaCuenta;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.ConflictoException;
import co.abogados.identidad.dominio.CredencialesInvalidasException;
import co.abogados.identidad.dominio.Cuenta;
import co.abogados.identidad.dominio.CuentaBloqueadaException;
import co.abogados.identidad.dominio.RecursoNoEncontradoException;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.SesionInvalidaException;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;

/** Casos límite de cuentas, inicio de sesión y sesiones que no cubren las pruebas de cada historia. */
class ReglasServiciosTest {

	static final long ADMIN = 79123456L;
	static final long SOCIA = 52345678L;

	CuentasTest.CuentasEnMemoria cuentas;
	AutenticacionTest.UsuariosEnMemoria usuarios;
	SesionesEnMemoria repoSesiones;
	AutenticacionTest.MutableReloj reloj;
	SesionesServicio sesiones;
	CuentasServicio servicio;
	AutenticacionServicio auth;

	@BeforeEach
	void preparar() {
		cuentas = new CuentasTest.CuentasEnMemoria();
		cuentas.crear(ADMIN, "arodriguez", "Andrés Rodríguez", "arodriguez@firma.co", "h", Set.of("ADMIN", "SOCIO"), null);
		cuentas.crear(SOCIA, "cgomez", "Catalina Gómez", "cgomez@firma.co", "h", Set.of("SOCIO"), null);
		usuarios = new AutenticacionTest.UsuariosEnMemoria();
		usuarios.datos.put(ADMIN, new Usuario(ADMIN, "arodriguez", "Andrés", "arodriguez@firma.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("ADMIN", "SOCIO"), 0, null));
		usuarios.datos.put(SOCIA, new Usuario(SOCIA, "cgomez", "Catalina", "cgomez@firma.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("SOCIO"), 0, null));
		repoSesiones = new SesionesEnMemoria();
		reloj = new AutenticacionTest.MutableReloj(AutenticacionTest.AHORA);
		sesiones = new SesionesServicio(repoSesiones, usuarios, new EmisorJwtRsa("", Duration.ofMinutes(15)), reloj,
				Duration.ofHours(12), Duration.ofDays(7));
		servicio = new CuentasServicio(cuentas, AutenticacionTest.CIFRADOR, sesiones);
		auth = new AutenticacionServicio(usuarios, AutenticacionTest.CIFRADOR, sesiones, reloj);
	}

	// ------------------------------------------------------------- cuentas

	@Test
	void elDocumentoDebeSerPositivoYUnaCuentaQueNoExisteEsUn404() {
		assertThatThrownBy(() -> servicio.crear(new NuevaCuenta(0, "laura", "Laura", "laura@firma.co", Set.of("ABOGADO"),
				"Temporal2026x"), ADMIN)).hasMessageContaining("documento");
		assertThatThrownBy(() -> servicio.desbloquear(5L, ADMIN)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.restablecerClave(5L, "Temporal2026x", ADMIN))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.actualizar(5L, new CambioCuenta("nadie", "Nadie", "n@firma.co",
				Set.of("SOCIO"), true), ADMIN)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void elCorreoSeGuardaEnMinusculasYNoPuedeRepetirse() {
		Cuenta c = servicio.crear(new NuevaCuenta(1020304050L, "lnueva", "  Laura Nueva ", " Laura@Firma.CO ",
				Set.of("ABOGADO"), "Temporal2026x"), ADMIN);
		assertThat(c.email()).isEqualTo("laura@firma.co");
		assertThat(c.nombre()).isEqualTo("Laura Nueva");

		assertThatThrownBy(() -> servicio.crear(new NuevaCuenta(7L, "otra", "Otra", "LAURA@firma.co", Set.of("ABOGADO"),
				"Temporal2026x"), ADMIN)).isInstanceOf(ConflictoException.class);
		assertThatThrownBy(() -> servicio.actualizar(SOCIA, new CambioCuenta("lnueva", "Catalina", "cgomez@firma.co",
				Set.of("SOCIO"), true), ADMIN)).isInstanceOf(ConflictoException.class).hasMessageContaining("Otra cuenta");
	}

	@Test
	void cambiarSoloElNombreNoCortaLasSesiones() {
		Sesion s = sesiones.abrir(usuarios.datos.get(SOCIA), "1.2.3.4");

		Cuenta c = servicio.actualizar(SOCIA, new CambioCuenta("CGomez", "Catalina Gómez Ruiz", "cgomez@firma.co",
				Set.of("SOCIO"), true), ADMIN);

		assertThat(c.nombre()).isEqualTo("Catalina Gómez Ruiz");
		assertThat(c.usuario()).isEqualTo("cgomez");
		assertThat(sesiones.revocados()).isEmpty();
		assertThat(sesiones.renovar(s.refresco(), "1.2.3.4")).isNotNull();
	}

	@Test
	void desactivarUnaCuentaCortaSusSesionesYUnAdminPuedeDesactivarAOtro() {
		Sesion s = sesiones.abrir(usuarios.datos.get(SOCIA), "1.2.3.4");

		Cuenta c = servicio.actualizar(SOCIA, new CambioCuenta("cgomez", "Catalina", "cgomez@firma.co", Set.of("SOCIO"),
				false), ADMIN);

		assertThat(c.activo()).isFalse();
		assertThat(repoSesiones.filas.values()).allSatisfy(f -> assertThat(f.motivo()).isEqualTo("CUENTA_DESACTIVADA"));
		assertThatThrownBy(() -> sesiones.renovar(s.refresco(), "1.2.3.4")).isInstanceOf(SesionInvalidaException.class);
	}

	@Test
	void unAdminInactivoNoCuentaComoUltimoAdministrador() {
		cuentas.crear(3L, "viejoadmin", "Viejo", "viejo@firma.co", "h", Set.of("ADMIN"), null);
		cuentas.actualizar(3L, "viejoadmin", "Viejo", "viejo@firma.co", false, Set.of("ADMIN"), null);

		// Reactivarlo o quitarle roles a un admin inactivo no deja a nadie sin administrador
		servicio.actualizar(3L, new CambioCuenta("viejoadmin", "Viejo", "viejo@firma.co", Set.of("SOCIO"), false), ADMIN);
		assertThat(cuentas.administradoresActivos()).isEqualTo(1);
	}

	@Test
	void enlazarUnaCuentaConOtroClienteCortaSusSesiones() {
		cuentas.crear(1032456781L, "cmendez", "Carolina", "c@correo.co", "h", Set.of("CLIENTE"), 6);
		usuarios.datos.put(1032456781L, new Usuario(1032456781L, "cmendez", "Carolina", "c@correo.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("CLIENTE"), 0, null, 6));
		Sesion s = sesiones.abrir(usuarios.datos.get(1032456781L), "1.2.3.4");

		servicio.actualizar(1032456781L, new CambioCuenta("cmendez", "Carolina", "c@correo.co", Set.of("CLIENTE"), true, 7),
				ADMIN);

		assertThatThrownBy(() -> sesiones.renovar(s.refresco(), "1.2.3.4")).isInstanceOf(SesionInvalidaException.class);
		assertThat(servicio.deCliente(7)).extracting(Cuenta::usuario).containsExactly("cmendez");
		assertThat(servicio.deCliente(6)).isEmpty();
	}

	@Test
	void desbloquearYListar() {
		cuentas.bloqueadas.add(SOCIA);

		assertThat(servicio.desbloquear(SOCIA, ADMIN).bloqueadoHasta()).isNull();
		assertThat(servicio.listar()).extracting(Cuenta::usuario).containsExactly("arodriguez", "cgomez");
	}

	@Test
	void restablecerLaClaveCortaTodasLasSesionesDeLaPersona() {
		sesiones.abrir(usuarios.datos.get(SOCIA), "1.2.3.4");
		sesiones.abrir(usuarios.datos.get(SOCIA), "5.6.7.8");
		sesiones.abrir(usuarios.datos.get(ADMIN), "9.9.9.9");

		servicio.restablecerClave(SOCIA, "OtraTemporal2026", ADMIN);

		assertThat(sesiones.revocados()).hasSize(2);
		assertThat(AutenticacionTest.CIFRADOR.coincide("OtraTemporal2026", cuentas.hashes.get(SOCIA))).isTrue();
	}

	// ------------------------------------------------------------- inicio de sesión

	@Test
	void sinUsuarioOSinClaveNoEntraYNoRevelaNada() {
		assertThatThrownBy(() -> auth.iniciarSesion(null, "Prueba2026*", "1.2.3.4"))
				.isInstanceOf(CredencialesInvalidasException.class).hasMessage("Usuario o contraseña incorrectos");
		assertThatThrownBy(() -> auth.iniciarSesion("   ", null, "1.2.3.4"))
				.isInstanceOf(CredencialesInvalidasException.class).hasMessage("Usuario o contraseña incorrectos");
		assertThatThrownBy(() -> auth.iniciarSesion("arodriguez", null, "1.2.3.4"))
				.isInstanceOf(CredencialesInvalidasException.class).hasMessage("Usuario o contraseña incorrectos");
		assertThat(usuarios.intentos).extracting(i -> i.split("\\|")[3])
				.containsExactly("NO_EXISTE", "NO_EXISTE", "CLAVE_INCORRECTA");
		assertThat(usuarios.datos.get(ADMIN).intentosFallidos()).isEqualTo(1);
	}

	@Test
	void laBitacoraRecortaUsuariosMuyLargos() {
		String largo = "x".repeat(300);
		assertThatThrownBy(() -> auth.iniciarSesion(largo, "Prueba2026*", "1.2.3.4"))
				.isInstanceOf(CredencialesInvalidasException.class);
		assertThat(usuarios.intentos).singleElement().satisfies(i -> assertThat(i.split("\\|")[0]).hasSize(120));
	}

	@Test
	void unaCuentaBloqueadaDiceCuantosMinutosFaltan() {
		usuarios.guardarFallo(ADMIN, 0, AutenticacionTest.AHORA.plusSeconds(150));

		assertThatThrownBy(() -> auth.iniciarSesion("arodriguez", "Prueba2026*", "1.2.3.4"))
				.isInstanceOfSatisfying(CuentaBloqueadaException.class, e -> assertThat(e.minutos()).isEqualTo(3));
		assertThat(usuarios.intentos).last().satisfies(i -> assertThat(i).endsWith("BLOQUEADO"));
	}

	@Test
	void laClaveNuevaDebeSerDistintaDeLaActual() {
		assertThatThrownBy(() -> auth.cambiarClave(ADMIN, "Prueba2026*", "Prueba2026*"))
				.isInstanceOf(ReglaNegocioException.class);
		usuarios.cambiarClave(ADMIN, AutenticacionTest.CIFRADOR.cifrar("Expediente2026"), AutenticacionTest.AHORA);
		assertThatThrownBy(() -> auth.cambiarClave(ADMIN, "Expediente2026", "Expediente2026"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("distinta");
		assertThatThrownBy(() -> auth.cambiarClave(ADMIN, null, "Litigio2026Seguro"))
				.isInstanceOf(CredencialesInvalidasException.class);
		assertThatThrownBy(() -> auth.yo(5L)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(auth.yo(ADMIN).usuario()).isEqualTo("arodriguez");
	}

	// ------------------------------------------------------------- sesiones

	@Test
	void cerrarSinRefreshTokenOConUnoDesconocidoNoHaceNada() {
		sesiones.abrir(usuarios.datos.get(ADMIN), "1.2.3.4");

		sesiones.cerrar(null);
		sesiones.cerrar(" ");
		sesiones.cerrar("desconocido");

		assertThat(sesiones.revocados()).isEmpty();
	}

	@Test
	void elRefreshTokenDeOtraPersonaNoSalvaNingunaSesion() {
		Sesion ajena = sesiones.abrir(usuarios.datos.get(SOCIA), "1.2.3.4");
		sesiones.abrir(usuarios.datos.get(ADMIN), "1.2.3.4");
		sesiones.abrir(usuarios.datos.get(ADMIN), "5.6.7.8");

		assertThat(sesiones.cerrarTodas(ADMIN, "CLAVE_CAMBIADA", ajena.refresco())).isEqualTo(2);
		assertThat(sesiones.cerrarTodas(ADMIN, "CLAVE_CAMBIADA", null)).isZero();
		assertThat(sesiones.renovar(ajena.refresco(), "1.2.3.4")).isNotNull();
	}

	@Test
	void unaCuentaBorradaNoRenuevaPeroUnaBloqueadaSi() {
		Sesion admin = sesiones.abrir(usuarios.datos.get(ADMIN), "1.2.3.4");
		Sesion socia = sesiones.abrir(usuarios.datos.get(SOCIA), "1.2.3.4");
		// F13: alguien falla 5 veces la contraseña del administrador; su sesión abierta no se corta por eso.
		usuarios.guardarFallo(ADMIN, 0, AutenticacionTest.AHORA.plus(Duration.ofMinutes(15)));
		usuarios.datos.remove(SOCIA);

		assertThat(sesiones.renovar(admin.refresco(), "1.2.3.4").token()).isNotBlank();
		assertThatThrownBy(() -> sesiones.renovar(socia.refresco(), "1.2.3.4")).isInstanceOf(SesionInvalidaException.class);
		assertThat(repoSesiones.filas.values()).filteredOn(f -> f.motivo() != null)
				.allSatisfy(f -> assertThat(f.motivo()).isEqualTo("CUENTA_INACTIVA")).isNotEmpty();
	}

	@Test
	void losRevocadosVencidosSeLimpian() {
		Sesion s = sesiones.abrir(usuarios.datos.get(ADMIN), "1.2.3.4");
		sesiones.cerrar(s.refresco());
		assertThat(sesiones.revocados()).hasSize(1);

		reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(16));
		sesiones.limpiar();

		assertThat(sesiones.revocados()).isEmpty();
		assertThat(repoSesiones.revocados).isEmpty();
	}

	@Test
	void unaSesionRenovadaConservaLaMarcaDeClaveTemporal() {
		usuarios.debenCambiar.add(SOCIA);
		Sesion s = sesiones.abrir(usuarios.datos.get(SOCIA), "1.2.3.4");

		assertThat(s.debeCambiarClave()).isTrue();
		assertThat(sesiones.renovar(s.refresco(), "1.2.3.4").debeCambiarClave()).isTrue();
	}

}
