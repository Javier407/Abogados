package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jwt.SignedJWT;

import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.SesionInvalidaException;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;

/** F9: token de acceso de 15 min, refresh token de un solo uso y sesiones que se cortan desde el servidor. */
class SesionesTest {

	static final long CLIENTE = 1032456781L;

	AutenticacionTest.UsuariosEnMemoria usuarios;
	SesionesEnMemoria repo;
	AutenticacionTest.MutableReloj reloj;
	SesionesServicio sesiones;
	AutenticacionServicio auth;

	@BeforeEach
	void preparar() {
		usuarios = new AutenticacionTest.UsuariosEnMemoria();
		usuarios.datos.put(79123456L, new Usuario(79123456L, "arodriguez", "Andrés", "arodriguez@firma.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("ADMIN", "SOCIO"), 0, null));
		usuarios.datos.put(CLIENTE, new Usuario(CLIENTE, "cmendez", "Carolina Méndez", "carolina@correo.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("CLIENTE"), 0, null, 6));
		repo = new SesionesEnMemoria();
		reloj = new AutenticacionTest.MutableReloj(AutenticacionTest.AHORA);
		sesiones = new SesionesServicio(repo, usuarios, new EmisorJwtRsa("", Duration.ofMinutes(15)), reloj,
				Duration.ofHours(12), Duration.ofDays(7));
		auth = new AutenticacionServicio(usuarios, AutenticacionTest.CIFRADOR, sesiones, reloj);
	}

	@Test
	void alEntrarRecibeUnTokenCortoYUnRefreshTokenQueSoloSeGuardaComoHash() throws Exception {
		Sesion s = auth.iniciarSesion("arodriguez", "Prueba2026*", "100.64.0.5");

		assertThat(s.expira()).isEqualTo(AutenticacionTest.AHORA.plus(Duration.ofMinutes(15)));
		assertThat(s.refresco()).hasSizeGreaterThanOrEqualTo(43);
		assertThat(s.refrescoExpira()).isEqualTo(AutenticacionTest.AHORA.plus(Duration.ofHours(12)));
		assertThat(repo.filas.values()).singleElement()
				.satisfies(f -> assertThat(f.hash()).hasSize(64).doesNotContain(s.refresco()));
		assertThat(SignedJWT.parse(s.token()).getJWTClaimsSet().getJWTID()).isNotBlank();
	}

	@Test
	void elTokenDelClienteLlevaSuFicha() throws Exception {
		Sesion s = auth.iniciarSesion("cmendez", "Prueba2026*", "1.2.3.4");

		assertThat(s.idCliente()).isEqualTo(6);
		assertThat(SignedJWT.parse(s.token()).getJWTClaimsSet().getLongClaim("idCliente")).isEqualTo(6L);
	}

	@Test
	void renovarGastaElRefreshTokenYEntregaOtro() {
		Sesion s = auth.iniciarSesion("arodriguez", "Prueba2026*", "1.2.3.4");
		reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(14));

		Sesion nueva = sesiones.renovar(s.refresco(), "1.2.3.4");

		assertThat(nueva.refresco()).isNotEqualTo(s.refresco());
		assertThat(nueva.token()).isNotEqualTo(s.token());
		assertThat(nueva.expira()).isEqualTo(reloj.ahora.plus(Duration.ofMinutes(15)));
		assertThat(sesiones.renovar(nueva.refresco(), "1.2.3.4").idUsuario()).isEqualTo(79123456L);
	}

	@Test
	void usarDosVecesElMismoRefreshTokenCierraLaSesionYCortaLosTokensVigentes() {
		Sesion s = auth.iniciarSesion("arodriguez", "Prueba2026*", "1.2.3.4");
		Sesion legitima = sesiones.renovar(s.refresco(), "1.2.3.4");

		assertThatThrownBy(() -> sesiones.renovar(s.refresco(), "200.1.1.1"))
				.isInstanceOf(SesionInvalidaException.class).hasMessageContaining("seguridad");
		// La familia completa queda revocada: tampoco sirve el refresh token legítimo
		assertThatThrownBy(() -> sesiones.renovar(legitima.refresco(), "1.2.3.4")).isInstanceOf(SesionInvalidaException.class);
		assertThat(sesiones.revocados()).hasSize(2);
	}

	@Test
	void cerrarSesionInvalidaElRefreshTokenYElTokenDeAcceso() {
		Sesion s = auth.iniciarSesion("arodriguez", "Prueba2026*", "1.2.3.4");

		sesiones.cerrar(s.refresco());
		sesiones.cerrar(s.refresco());   // cerrar dos veces no es error

		assertThatThrownBy(() -> sesiones.renovar(s.refresco(), "1.2.3.4")).isInstanceOf(SesionInvalidaException.class);
		assertThat(sesiones.revocados()).hasSize(1);
	}

	@Test
	void cambiarLaClaveCierraLasDemasSesionesPeroNoLaActual() {
		Sesion pc = auth.iniciarSesion("arodriguez", "Prueba2026*", "1.2.3.4");
		Sesion celular = auth.iniciarSesion("arodriguez", "Prueba2026*", "5.6.7.8");

		auth.cambiarClave(79123456L, "Prueba2026*", "Expediente2026", pc.refresco());

		assertThat(sesiones.renovar(pc.refresco(), "1.2.3.4")).isNotNull();
		assertThatThrownBy(() -> sesiones.renovar(celular.refresco(), "5.6.7.8")).isInstanceOf(SesionInvalidaException.class);
	}

	@Test
	void unaCuentaDesactivadaNoRenueva() {
		Sesion s = auth.iniciarSesion("arodriguez", "Prueba2026*", "1.2.3.4");
		Usuario u = usuarios.datos.get(79123456L);
		usuarios.datos.put(79123456L, new Usuario(u.idUsuario(), u.usuario(), u.nombre(), u.email(), u.hashClave(), false,
				u.roles(), 0, null));

		assertThatThrownBy(() -> sesiones.renovar(s.refresco(), "1.2.3.4")).isInstanceOf(SesionInvalidaException.class);
	}

	@Test
	void ningunaSesionDuraMasDeSieteDias() {
		Sesion s = auth.iniciarSesion("arodriguez", "Prueba2026*", "1.2.3.4");
		for (int i = 1; i <= 15; i++) {   // renueva cada 11 h durante casi 7 días
			reloj.ahora = reloj.ahora.plus(Duration.ofHours(11));
			s = sesiones.renovar(s.refresco(), "1.2.3.4");
		}
		assertThat(s.refrescoExpira()).isEqualTo(AutenticacionTest.AHORA.plus(Duration.ofDays(7)));
		reloj.ahora = AutenticacionTest.AHORA.plus(Duration.ofDays(7));
		String ultimo = s.refresco();
		assertThatThrownBy(() -> sesiones.renovar(ultimo, "1.2.3.4")).isInstanceOf(SesionInvalidaException.class);
	}

	@Test
	void unRefreshTokenVencidoOInventadoNoSirve() {
		Sesion s = auth.iniciarSesion("arodriguez", "Prueba2026*", "1.2.3.4");
		reloj.ahora = reloj.ahora.plus(Duration.ofHours(12));

		assertThatThrownBy(() -> sesiones.renovar(s.refresco(), "1.2.3.4")).isInstanceOf(SesionInvalidaException.class);
		assertThatThrownBy(() -> sesiones.renovar("inventado", "1.2.3.4")).isInstanceOf(SesionInvalidaException.class);
	}

}
