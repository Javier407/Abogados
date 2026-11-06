package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.aplicacion.MfaServicio;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.ConflictoException;
import co.abogados.identidad.dominio.CredencialesInvalidasException;
import co.abogados.identidad.dominio.CuentaBloqueadaException;
import co.abogados.identidad.dominio.EstadoMfa;
import co.abogados.identidad.dominio.MfaRequeridoException;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.Totp;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.MfaRepositorio;
import co.abogados.identidad.infraestructura.CifradorAesGcm;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;

/** F13: verificación en dos pasos con códigos TOTP, códigos de recuperación y desafíos del inicio de sesión. */
class MfaTest {

	static final long ADMIN = 79123456L, ABOGADA = 1019087654L;
	static final String CLAVE = "Prueba2026*";

	AutenticacionTest.UsuariosEnMemoria usuarios;
	AutenticacionTest.MutableReloj reloj;
	MfaEnMemoria repo;
	SesionesEnMemoria sesiones;
	CifradorAesGcm cifrador = new CifradorAesGcm("");
	MfaServicio mfa;
	AutenticacionServicio auth;

	@BeforeEach
	void preparar() {
		usuarios = new AutenticacionTest.UsuariosEnMemoria();
		usuarios.datos.put(ADMIN, new Usuario(ADMIN, "arodriguez", "Andrés Rodríguez", "a@firma.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("ADMIN", "SOCIO"), 0, null));
		usuarios.datos.put(ABOGADA, new Usuario(ABOGADA, "crojas", "Carolina Rojas", "c@firma.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("ABOGADO"), 0, null));
		reloj = new AutenticacionTest.MutableReloj(AutenticacionTest.AHORA);
		repo = new MfaEnMemoria();
		sesiones = new SesionesEnMemoria();
		armar("ADMIN");
	}

	void armar(String obligatorios) {
		SesionesServicio s = new SesionesServicio(sesiones, usuarios, new EmisorJwtRsa("", Duration.ofMinutes(15)), reloj,
				Duration.ofHours(12), Duration.ofDays(7));
		mfa = new MfaServicio(repo, usuarios, s, cifrador, AutenticacionTest.CIFRADOR, reloj, obligatorios);
		auth = new AutenticacionServicio(usuarios, AutenticacionTest.CIFRADOR, s, reloj, mfa);
	}

	String codigoActual(long id, int pasosDespues) {
		byte[] secreto = cifrador.descifrar(repo.filas.get(id).secretoCifrado());
		return Totp.codigo(secreto, Totp.paso(reloj.ahora) + pasosDespues);
	}

	/** Activa la verificación de la abogada y devuelve sus códigos de recuperación. */
	List<String> activarAbogada() {
		mfa.iniciar(ABOGADA);
		return mfa.activar(ABOGADA, codigoActual(ABOGADA, 0));
	}

	@Test
	void elCodigoTotpCoincideConLosVectoresDelRfc6238() {
		byte[] secreto = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
		assertThat(Totp.codigo(secreto, Totp.paso(Instant.ofEpochSecond(59)))).isEqualTo("287082");
		assertThat(Totp.codigo(secreto, Totp.paso(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
		assertThat(Totp.codigo(secreto, Totp.paso(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
		assertThat(Totp.base32(secreto)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
		// Un intervalo de tolerancia hacia atrás y hacia adelante; dos no.
		Instant t = Instant.ofEpochSecond(1111111109);
		assertThat(Totp.verificar(secreto, "081804", t.plusSeconds(30), null)).isPresent();
		assertThat(Totp.verificar(secreto, "081804", t.plusSeconds(61), null)).isEmpty();
		assertThat(Totp.verificar(secreto, "081804", t, Totp.paso(t))).isEmpty();     // ya usado
		assertThat(Totp.verificar(secreto, "08180", t, null)).isEmpty();
	}

	@Test
	void sinVerificacionActivaLaContrasenaAbreLaSesionComoAntes() {
		assertThat(auth.iniciarSesion("crojas", CLAVE, "1.2.3.4").token()).isNotBlank();
	}

	@Test
	void activarlaPideUnCodigoYEntregaDiezCodigosDeRecuperacion() {
		MfaServicio.Inicio inicio = mfa.iniciar(ABOGADA);
		assertThat(inicio.uri()).startsWith("otpauth://totp/Gesti%C3%B3n%20Jur%C3%ADdica%3Acrojas?secret=" + inicio.secreto())
				.contains("issuer=Gesti%C3%B3n%20Jur%C3%ADdica");
		assertThat(repo.filas.get(ABOGADA).secretoCifrado()).startsWith("v1:").doesNotContain(inicio.secreto());
		assertThatThrownBy(() -> mfa.activar(ABOGADA, "000000")).isInstanceOf(ReglaNegocioException.class);

		List<String> codigos = mfa.activar(ABOGADA, codigoActual(ABOGADA, 0));

		assertThat(codigos).hasSize(10).allMatch(c -> c.matches("[a-z2-9]{4}-[a-z2-9]{4}"));
		assertThat(codigos.stream().distinct()).hasSize(10);
		assertThat(mfa.estado(ABOGADA)).isEqualTo(new MfaServicio.Estado(true, reloj.ahora, 10, false));
		assertThatThrownBy(() -> mfa.iniciar(ABOGADA)).isInstanceOf(ConflictoException.class);
	}

	@Test
	void conLaVerificacionActivaLaContrasenaSolaNoAbreLaSesion() {
		activarAbogada();
		reloj.ahora = reloj.ahora.plusSeconds(60);
		int sesionesAntes = sesiones.filas.size();

		MfaRequeridoException e = catchMfa(() -> auth.iniciarSesion("crojas", CLAVE, "1.2.3.4"));
		assertThat(e.configurar()).isFalse();
		assertThat(sesiones.filas).hasSize(sesionesAntes);

		Sesion s = mfa.completar(e.desafio(), codigoActual(ABOGADA, 0), "1.2.3.4");
		assertThat(s.idUsuario()).isEqualTo(ABOGADA);
		assertThat(usuarios.intentos).last().asString().startsWith("crojas|1019087654|true");
		// El mismo código no sirve dos veces, aunque siga dentro de su intervalo.
		MfaRequeridoException otra = catchMfa(() -> auth.iniciarSesion("crojas", CLAVE, "1.2.3.4"));
		assertThatThrownBy(() -> mfa.completar(otra.desafio(), codigoActual(ABOGADA, 0), "1.2.3.4"))
				.isInstanceOf(CredencialesInvalidasException.class);
	}

	@Test
	void cincoCodigosIncorrectosBloqueanLaCuenta() {
		activarAbogada();
		MfaRequeridoException e = catchMfa(() -> auth.iniciarSesion("crojas", CLAVE, "1.2.3.4"));
		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> mfa.completar(e.desafio(), "123456", "1.2.3.4"))
					.isInstanceOf(CredencialesInvalidasException.class);
		}
		assertThatThrownBy(() -> mfa.completar(e.desafio(), "123456", "1.2.3.4")).isInstanceOf(CuentaBloqueadaException.class);
		assertThatThrownBy(() -> mfa.completar(e.desafio(), codigoActual(ABOGADA, 1), "1.2.3.4"))
				.isInstanceOf(CuentaBloqueadaException.class);
		assertThat(usuarios.intentos).anyMatch(i -> i.endsWith("|false|MFA_INCORRECTO"));
	}

	@Test
	void unCodigoDeRecuperacionSirveUnaSolaVez() {
		List<String> codigos = activarAbogada();
		MfaRequeridoException e = catchMfa(() -> auth.iniciarSesion("crojas", CLAVE, "1.2.3.4"));

		assertThat(mfa.completar(e.desafio(), codigos.get(3).toUpperCase().replace("-", " "), "1.2.3.4").token()).isNotBlank();
		assertThatThrownBy(() -> mfa.completar(e.desafio(), codigos.get(3), "1.2.3.4"))
				.isInstanceOf(CredencialesInvalidasException.class);
		assertThat(mfa.estado(ABOGADA).codigosRestantes()).isEqualTo(9);
		assertThat(mfa.regenerarCodigos(ABOGADA, CLAVE)).hasSize(10).doesNotContainAnyElementsOf(codigos);
		assertThat(mfa.estado(ABOGADA).codigosRestantes()).isEqualTo(10);
	}

	@Test
	void unDesafioAlteradoVencidoODeOtraClaseNoSirve() {
		activarAbogada();
		MfaRequeridoException e = catchMfa(() -> auth.iniciarSesion("crojas", CLAVE, "1.2.3.4"));
		String alterado = e.desafio().replaceFirst("^.", e.desafio().startsWith("A") ? "B" : "A");

		assertThatThrownBy(() -> mfa.completar(alterado, codigoActual(ABOGADA, 0), "1.2.3.4"))
				.isInstanceOf(CredencialesInvalidasException.class).hasMessageContaining("venció");
		assertThatThrownBy(() -> mfa.completar("basura", "123456", "1.2.3.4")).isInstanceOf(CredencialesInvalidasException.class);
		assertThatThrownBy(() -> mfa.iniciarConDesafio(e.desafio())).isInstanceOf(CredencialesInvalidasException.class);
		reloj.ahora = reloj.ahora.plusSeconds(301);
		assertThatThrownBy(() -> mfa.completar(e.desafio(), codigoActual(ABOGADA, 0), "1.2.3.4"))
				.isInstanceOf(CredencialesInvalidasException.class);
	}

	@Test
	void elRolObligatorioDebeActivarlaAlEntrarYNoPuedeDesactivarla() {
		MfaRequeridoException e = catchMfa(() -> auth.iniciarSesion("arodriguez", CLAVE, "1.2.3.4"));
		assertThat(e.configurar()).isTrue();
		assertThatThrownBy(() -> mfa.completar(e.desafio(), "123456", "1.2.3.4")).isInstanceOf(CredencialesInvalidasException.class);

		mfa.iniciarConDesafio(e.desafio());
		MfaServicio.Activacion a = mfa.activarConDesafio(e.desafio(), codigoActual(ADMIN, 0), "1.2.3.4");

		assertThat(a.sesion().idUsuario()).isEqualTo(ADMIN);
		assertThat(a.codigos()).hasSize(10);
		assertThat(mfa.estado(ADMIN).obligatorio()).isTrue();
		assertThatThrownBy(() -> mfa.desactivar(ADMIN, CLAVE, codigoActual(ADMIN, 1)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("exige");
	}

	@Test
	void desactivarlaPideContrasenaYCodigo() {
		activarAbogada();
		assertThatThrownBy(() -> mfa.desactivar(ABOGADA, "otra", codigoActual(ABOGADA, 1)))
				.isInstanceOf(CredencialesInvalidasException.class);
		assertThatThrownBy(() -> mfa.desactivar(ABOGADA, CLAVE, "000000")).isInstanceOf(CredencialesInvalidasException.class);
		mfa.desactivar(ABOGADA, CLAVE, codigoActual(ABOGADA, 1));
		assertThat(mfa.estado(ABOGADA).activo()).isFalse();
		assertThat(auth.iniciarSesion("crojas", CLAVE, "1.2.3.4").token()).isNotBlank();
	}

	@Test
	void elAdministradorLaReiniciaYCortaLasSesiones() {
		activarAbogada();
		reloj.ahora = reloj.ahora.plusSeconds(30);
		MfaRequeridoException e = catchMfa(() -> auth.iniciarSesion("crojas", CLAVE, "1.2.3.4"));
		Sesion abierta = mfa.completar(e.desafio(), codigoActual(ABOGADA, 0), "1.2.3.4");
		assertThat(mfa.conMfa()).containsExactly(ABOGADA);

		mfa.reiniciar(ADMIN, ABOGADA);

		assertThat(mfa.conMfa()).isEmpty();
		assertThat(sesiones.revocados).isNotEmpty();
		assertThat(sesiones.filas.values()).filteredOn(f -> f.motivo() != null)
				.extracting(SesionesEnMemoria.Fila::motivo).containsOnly("MFA_REINICIADO");
		assertThat(abierta.token()).isNotBlank();
	}

	@Test
	void elSecretoCifradoNoSeLeeConOtraLlave() {
		String guardado = cifrador.cifrar(new byte[] { 1, 2, 3 });
		assertThat(cifrador.descifrar(guardado)).containsExactly(1, 2, 3);
		assertThat(cifrador.cifrar(new byte[] { 1, 2, 3 })).isNotEqualTo(guardado);     // IV distinto cada vez
		CifradorAesGcm otra = new CifradorAesGcm(java.util.Base64.getEncoder().encodeToString(new byte[32]));
		assertThatThrownBy(() -> otra.descifrar(guardado)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new CifradorAesGcm("YWJj")).isInstanceOf(IllegalStateException.class);
	}

	static MfaRequeridoException catchMfa(Runnable r) {
		try {
			r.run();
		} catch (MfaRequeridoException e) {
			return e;
		}
		throw new AssertionError("Se esperaba que pidiera el segundo paso");
	}

	// ------------------------------------------------------------------ doble

	static class MfaEnMemoria implements MfaRepositorio {
		final Map<Long, EstadoMfa> filas = new HashMap<>();
		final Map<Long, Map<String, Boolean>> codigos = new HashMap<>();   // hash -> usado

		public Optional<EstadoMfa> estado(long id) {
			return Optional.ofNullable(filas.get(id)).map(e -> new EstadoMfa(id, e.secretoCifrado(), e.activo(), e.desde(),
					e.ultimoPaso(), (int) codigos.getOrDefault(id, Map.of()).values().stream().filter(u -> !u).count()));
		}

		public void guardarPendiente(long id, String secreto) {
			if (filas.containsKey(id) && filas.get(id).activo()) return;
			filas.put(id, new EstadoMfa(id, secreto, false, null, null, 0));
		}

		public void activar(long id, Instant desde, long paso) {
			filas.put(id, new EstadoMfa(id, filas.get(id).secretoCifrado(), true, desde, paso, 0));
		}

		public void desactivar(long id) {
			filas.remove(id);
			codigos.remove(id);
		}

		public boolean usarPaso(long id, long paso) {
			EstadoMfa e = filas.get(id);
			if (e == null || !e.activo() || (e.ultimoPaso() != null && e.ultimoPaso() >= paso)) return false;
			filas.put(id, new EstadoMfa(id, e.secretoCifrado(), true, e.desde(), paso, 0));
			return true;
		}

		public void guardarCodigos(long id, List<String> hashes) {
			codigos.put(id, hashes.stream().collect(Collectors.toMap(h -> h, h -> false)));
		}

		public boolean usarCodigo(long id, String hash, Instant ahora) {
			Map<String, Boolean> c = codigos.getOrDefault(id, new HashMap<>());
			if (!Boolean.FALSE.equals(c.get(hash))) return false;
			c.put(hash, true);
			return true;
		}

		public Set<Long> conMfa() {
			return filas.values().stream().filter(EstadoMfa::activo).map(EstadoMfa::idUsuario).collect(Collectors.toSet());
		}
	}

}
