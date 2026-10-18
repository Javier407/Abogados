package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;

import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.dominio.CredencialesInvalidasException;
import co.abogados.identidad.dominio.CuentaBloqueadaException;
import co.abogados.identidad.dominio.PoliticaClave;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.UsuarioRepositorio;
import co.abogados.identidad.infraestructura.CifradorBCrypt;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;

/** Inicio de sesión con BCrypt real, un emisor de tokens con llave temporal y la base en memoria. */
class AutenticacionTest {

	static final Instant AHORA = Instant.parse("2026-10-16T15:00:00Z");
	static final CifradorBCrypt CIFRADOR = new CifradorBCrypt();
	static final String HASH_PRUEBA = CIFRADOR.cifrar("Prueba2026*");

	UsuariosEnMemoria repo;
	EmisorJwtRsa emisor;
	MutableReloj reloj;
	AutenticacionServicio servicio;

	@BeforeEach
	void preparar() {
		repo = new UsuariosEnMemoria();
		repo.datos.put(79123456L, new Usuario(79123456L, "arodriguez", "Andrés Felipe Rodríguez Mejía",
				"arodriguez@firma.co", HASH_PRUEBA, true, Set.of("ADMIN", "SOCIO"), 0, null));
		repo.datos.put(52987654L, new Usuario(52987654L, "gpardo", "Gloria Pardo", "gpardo@firma.co", HASH_PRUEBA, false,
				Set.of("ADMINISTRATIVO"), 0, null));
		emisor = new EmisorJwtRsa("", Duration.ofHours(8));
		reloj = new MutableReloj(AHORA);
		servicio = new AutenticacionServicio(repo, CIFRADOR, new co.abogados.identidad.aplicacion.SesionesServicio(
				new SesionesEnMemoria(), repo, emisor, reloj, Duration.ofHours(12), Duration.ofDays(7)), reloj);
	}

	@Test
	void ingresaConUsuarioOCorreoYRecibeUnTokenFirmadoConSusRoles() throws Exception {
		Sesion s = servicio.iniciarSesion("  ARodriguez ", "Prueba2026*", "100.64.0.5");
		Sesion porCorreo = servicio.iniciarSesion("arodriguez@firma.co", "Prueba2026*", "100.64.0.5");

		assertThat(porCorreo.idUsuario()).isEqualTo(79123456L);
		assertThat(s.expira()).isEqualTo(AHORA.plus(Duration.ofHours(8)));
		SignedJWT jwt = SignedJWT.parse(s.token());
		RSAKey publica = (RSAKey) JWKSet.parse(emisor.jwks()).getKeys().get(0);
		assertThat(jwt.verify(new RSASSAVerifier(publica))).isTrue();
		assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
		assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("79123456");
		assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactlyInAnyOrder("ADMIN", "SOCIO");
		assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("gestion-juridica");
		assertThat(repo.intentos).last().satisfies(i -> assertThat(i).startsWith("arodriguez@firma.co|79123456|true"));
	}

	@Test
	void elJwksSoloPublicaLaLlavePublica() {
		Map<String, Object> jwks = emisor.jwks();
		@SuppressWarnings("unchecked")
		Map<String, Object> llave = ((List<Map<String, Object>>) jwks.get("keys")).get(0);
		assertThat(llave).containsKeys("n", "e", "kid").doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
	}

	@Test
	void elMensajeNoRevelaSiFallaElUsuarioOLaClave() {
		assertThatThrownBy(() -> servicio.iniciarSesion("noexiste", "Prueba2026*", null))
				.isInstanceOf(CredencialesInvalidasException.class).hasMessage("Usuario o contraseña incorrectos");
		assertThatThrownBy(() -> servicio.iniciarSesion("arodriguez", "otra", null))
				.isInstanceOf(CredencialesInvalidasException.class).hasMessage("Usuario o contraseña incorrectos");
		assertThat(repo.intentos).extracting(i -> i.split("\\|")[3]).containsExactly("NO_EXISTE", "CLAVE_INCORRECTA");
	}

	@Test
	void cincoFallosBloqueanQuinceMinutosAunqueLuegoAcierte() {
		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> servicio.iniciarSesion("arodriguez", "mala", null))
					.isInstanceOf(CredencialesInvalidasException.class);
		}
		assertThatThrownBy(() -> servicio.iniciarSesion("arodriguez", "mala", null))
				.isInstanceOf(CuentaBloqueadaException.class).hasMessageContaining("15 minuto");
		assertThatThrownBy(() -> servicio.iniciarSesion("arodriguez", "Prueba2026*", null))
				.isInstanceOf(CuentaBloqueadaException.class);

		reloj.ahora = AHORA.plus(Duration.ofMinutes(16));
		assertThat(servicio.iniciarSesion("arodriguez", "Prueba2026*", null).idUsuario()).isEqualTo(79123456L);
		assertThat(repo.datos.get(79123456L).intentosFallidos()).isZero();
	}

	@Test
	void unIngresoExitosoReiniciaElContador() {
		assertThatThrownBy(() -> servicio.iniciarSesion("arodriguez", "mala", null));
		servicio.iniciarSesion("arodriguez", "Prueba2026*", null);
		assertThat(repo.datos.get(79123456L).intentosFallidos()).isZero();
	}

	@Test
	void unaCuentaInactivaNoEntra() {
		assertThatThrownBy(() -> servicio.iniciarSesion("gpardo", "Prueba2026*", null))
				.isInstanceOf(CredencialesInvalidasException.class).hasMessageContaining("desactivada");
	}

	@Test
	void cambiarClaveExigeLaActualYCumplirLaPolitica() {
		assertThatThrownBy(() -> servicio.cambiarClave(79123456L, "mala", "NuevaClave2026"))
				.isInstanceOf(CredencialesInvalidasException.class);
		assertThatThrownBy(() -> servicio.cambiarClave(79123456L, "Prueba2026*", "corta1"))
				.isInstanceOf(ReglaNegocioException.class);

		servicio.cambiarClave(79123456L, "Prueba2026*", "Litigio2026Seguro");

		assertThat(CIFRADOR.coincide("Litigio2026Seguro", repo.datos.get(79123456L).hashClave())).isTrue();
		assertThat(servicio.iniciarSesion("arodriguez", "Litigio2026Seguro", null)).isNotNull();
	}

	@Test
	void politicaDeContrasenas() {
		Usuario u = repo.datos.get(79123456L);
		assertThatThrownBy(() -> PoliticaClave.validar("solamenteletras", u)).hasMessageContaining("letras y números");
		assertThatThrownBy(() -> PoliticaClave.validar("arodriguez2026", u)).hasMessageContaining("usuario");
		assertThatThrownBy(() -> PoliticaClave.validar("Clave79123456", u)).hasMessageContaining("documento");
		assertThatThrownBy(() -> PoliticaClave.validar("Prueba2026*", u)).hasMessageContaining("pruebas");
		assertThatThrownBy(() -> PoliticaClave.validar("a1".repeat(40), u)).hasMessageContaining("72");
		PoliticaClave.validar("Expediente2026", u);
	}

	// ----------------------------------------------------------------- dobles

	static class MutableReloj extends Clock {
		Instant ahora;

		MutableReloj(Instant ahora) { this.ahora = ahora; }
		public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
		public Clock withZone(java.time.ZoneId zone) { return this; }
		public Instant instant() { return ahora; }
	}

	static class UsuariosEnMemoria implements UsuarioRepositorio {
		final Map<Long, Usuario> datos = new HashMap<>();
		final List<String> intentos = new ArrayList<>();

		public Optional<Usuario> porUsuarioOEmail(String t) {
			return datos.values().stream()
					.filter(u -> u.usuario().equalsIgnoreCase(t) || u.email().equalsIgnoreCase(t)).findFirst();
		}
		public Optional<Usuario> porId(long id) { return Optional.ofNullable(datos.get(id)); }
		public void guardarFallo(long id, int n, Instant hasta) {
			Usuario u = datos.get(id);
			datos.put(id, new Usuario(id, u.usuario(), u.nombre(), u.email(), u.hashClave(), u.activo(), u.roles(), n, hasta,
					u.idCliente()));
		}
		public void guardarIngreso(long id, Instant ahora) { guardarFallo(id, 0, null); }
		public void cambiarClave(long id, String hash, Instant ahora) {
			Usuario u = datos.get(id);
			datos.put(id, new Usuario(id, u.usuario(), u.nombre(), u.email(), hash, u.activo(), u.roles(),
					u.intentosFallidos(), u.bloqueadoHasta(), u.idCliente()));
			debenCambiar.remove(id);
		}
		final java.util.Set<Long> debenCambiar = new java.util.HashSet<>();

		public boolean debeCambiarClave(long id) { return debenCambiar.contains(id); }
		public void registrarIntento(String usuario, Long id, boolean ok, String motivo, String ip) {
			intentos.add(usuario + "|" + id + "|" + ok + "|" + motivo);
		}
	}

}
