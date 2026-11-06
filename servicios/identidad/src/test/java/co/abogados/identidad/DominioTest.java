package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.abogados.identidad.dominio.Cuenta;
import co.abogados.identidad.dominio.CuentaBloqueadaException;
import co.abogados.identidad.dominio.Refresco;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.Usuario;

/** Reglas del dominio: bloqueo por intentos, datos de las cuentas y estado de los refresh tokens. */
class DominioTest {

	static final Instant AHORA = AutenticacionTest.AHORA;

	static Usuario conFallos(int intentos, Instant bloqueadoHasta) {
		return new Usuario(1L, "u", "U", "u@firma.co", "h", true, Set.of("ABOGADO"), intentos, bloqueadoHasta);
	}

	@Test
	void losMinutosDeBloqueoSeRedondeanHaciaArriba() {
		assertThat(conFallos(0, null).minutosDeBloqueo(AHORA)).isZero();
		assertThat(conFallos(0, AHORA).bloqueado(AHORA)).isFalse();
		assertThat(conFallos(0, AHORA.plusSeconds(1)).minutosDeBloqueo(AHORA)).isEqualTo(1);
		assertThat(conFallos(0, AHORA.plusSeconds(60)).minutosDeBloqueo(AHORA)).isEqualTo(1);
		assertThat(conFallos(0, AHORA.plusSeconds(61)).minutosDeBloqueo(AHORA)).isEqualTo(2);
		assertThat(conFallos(0, AHORA.plus(Usuario.BLOQUEO)).minutosDeBloqueo(AHORA)).isEqualTo(15);
	}

	@Test
	void elQuintoFalloBloqueaYReiniciaElContador() {
		Usuario.Fallo cuarto = conFallos(3, null).registrarFallo(AHORA);
		assertThat(cuarto.intentos()).isEqualTo(4);
		assertThat(cuarto.bloquea()).isFalse();

		Usuario.Fallo quinto = conFallos(4, null).registrarFallo(AHORA);
		assertThat(quinto.intentos()).isZero();
		assertThat(quinto.bloquea()).isTrue();
		assertThat(quinto.bloqueadoHasta()).isEqualTo(AHORA.plus(Duration.ofMinutes(15)));
	}

	@Test
	void losRolesSeCopianYNoSePuedenModificarDesdeAfuera() {
		Set<String> roles = new HashSet<>(Set.of("SOCIO"));
		Usuario u = new Usuario(1L, "u", "U", "u@firma.co", "h", true, roles, 0, null);
		Cuenta c = new Cuenta(1L, "u", "U", "u@firma.co", true, roles, null, null, false);
		roles.add("ADMIN");

		assertThat(u.roles()).containsExactly("SOCIO");
		assertThat(c.roles()).containsExactly("SOCIO");
		assertThatThrownBy(() -> u.roles().add("ADMIN")).isInstanceOf(UnsupportedOperationException.class);
		assertThat(new Usuario(1L, "u", "U", "e", "h", true, null, 0, null).roles()).isEmpty();
		assertThat(new Cuenta(1L, "u", "U", "e", true, null, null, null, false).roles()).isEmpty();
	}

	@Test
	void validacionDeLosDatosDeUnaCuenta() {
		Set<String> abogado = Set.of("ABOGADO");
		assertThatCode(() -> Cuenta.validar("l.nueva_2-x", "Laura", "laura@firma.co", abogado)).doesNotThrowAnyException();

		assertThatThrownBy(() -> Cuenta.validar(null, "Laura", "laura@firma.co", abogado)).hasMessageContaining("usuario");
		assertThatThrownBy(() -> Cuenta.validar("ab", "Laura", "laura@firma.co", abogado)).hasMessageContaining("usuario");
		assertThatThrownBy(() -> Cuenta.validar("Laura", "Laura", "laura@firma.co", abogado)).hasMessageContaining("usuario");
		assertThatThrownBy(() -> Cuenta.validar("a".repeat(31), "Laura", "laura@firma.co", abogado))
				.hasMessageContaining("usuario");
		assertThatThrownBy(() -> Cuenta.validar("laura", null, "laura@firma.co", abogado)).hasMessageContaining("nombre");
		assertThatThrownBy(() -> Cuenta.validar("laura", "  ", "laura@firma.co", abogado)).hasMessageContaining("nombre");
		assertThatThrownBy(() -> Cuenta.validar("laura", "x".repeat(121), "laura@firma.co", abogado))
				.hasMessageContaining("120");
		assertThatThrownBy(() -> Cuenta.validar("laura", "Laura", null, abogado)).hasMessageContaining("correo");
		assertThatThrownBy(() -> Cuenta.validar("laura", "Laura", "laura@firma", abogado)).hasMessageContaining("correo");
		assertThatThrownBy(() -> Cuenta.validar("laura", "Laura", "la ura@firma.co", abogado)).hasMessageContaining("correo");
		assertThatThrownBy(() -> Cuenta.validar("laura", "Laura", "a".repeat(115) + "@f.com", abogado))
				.hasMessageContaining("correo");
		assertThatThrownBy(() -> Cuenta.validar("laura", "Laura", "laura@firma.co", null)).hasMessageContaining("rol");
		assertThatThrownBy(() -> Cuenta.validar("laura", "Laura", "laura@firma.co", Set.of()))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("rol");
		assertThatThrownBy(() -> Cuenta.validar("laura", "Laura", "laura@firma.co", Set.of("CLIENTE"), 0))
				.hasMessageContaining("Elija el cliente");
		assertThatCode(() -> Cuenta.validar("laura", "Laura", "laura@firma.co", Set.of("CLIENTE"), 6))
				.doesNotThrowAnyException();
	}

	@Test
	void estadoDeUnRefreshToken() {
		UUID id = UUID.randomUUID();
		Refresco nuevo = new Refresco(id, UUID.randomUUID(), 1L, AHORA.plusSeconds(10), AHORA.plusSeconds(100), null, null);
		Refresco gastado = new Refresco(id, nuevo.familia(), 1L, AHORA, AHORA.plusSeconds(100), AHORA, AHORA);

		assertThat(nuevo.usado()).isFalse();
		assertThat(nuevo.revocado()).isFalse();
		assertThat(nuevo.vencido(AHORA)).isFalse();
		assertThat(gastado.usado()).isTrue();
		assertThat(gastado.revocado()).isTrue();
		assertThat(gastado.vencido(AHORA)).isTrue();   // vence justo en su instante
	}

	@Test
	void unaSesionSinDatosOpcionalesYElMensajeDelBloqueo() {
		Sesion s = new Sesion("t", AHORA, 1L, "u", "U", Set.of("SOCIO"), true);
		assertThat(s.debeCambiarClave()).isTrue();
		assertThat(s.refresco()).isNull();
		assertThat(s.idCliente()).isNull();

		CuentaBloqueadaException e = new CuentaBloqueadaException(3);
		assertThat(e.minutos()).isEqualTo(3);
		assertThat(e.getMessage()).contains("3 minuto");
	}

}
