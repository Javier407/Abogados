package co.abogados.gateway.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** La cadena de hashes: el cálculo es el acordado con PostgreSQL y cualquier cambio a la historia se nota. */
class CadenaTest {

	static final Acceso PRIMERO = new Acceso(Instant.parse("2026-11-08T15:04:05.123456789Z"), "1020304050", "ABOGADO",
			"GET", "/api/procesos/34", 200, 12, "100.64.0.7");
	static final Acceso SEGUNDO = new Acceso(Instant.parse("2026-11-08T15:04:06.000001Z"), null, null, "POST",
			"/api/auth/login", null, 3, null);

	// Calculados aparte (Python hashlib) con el formato de la migración 015; el despliegue los compara con
	// auditoria.huella() en PostgreSQL (db/pruebas/015-auditoria.sql).
	static final String HASH_1 = "b52537578c6831f6967363dc2b2ba34f971f72f2029a9222cb7abeaa14d455a6";
	static final String HASH_2 = "c7edd8c64f006bdb15489b2005f18368f34881bca96084a96cebefd11e37b610";

	static List<Registro> dos() {
		Registro r1 = Cadena.encadenar(0, Cadena.GENESIS, PRIMERO);
		return List.of(r1, Cadena.encadenar(r1.id(), r1.hash(), SEGUNDO));
	}

	static Cadena.Verificacion revisar(List<Registro> registros) {
		Cadena.Verificador v = new Cadena.Verificador();
		registros.forEach(v::revisar);
		return v.resultado();
	}

	@Test
	void elHashEsElAcordadoConLaBase() {
		List<Registro> r = dos();
		assertThat(r.get(0).id()).isEqualTo(1);
		assertThat(r.get(0).hashAnterior()).isEqualTo(Cadena.GENESIS);
		assertThat(r.get(0).hash()).isEqualTo(HASH_1);
		assertThat(r.get(1).hashAnterior()).isEqualTo(HASH_1);
		assertThat(r.get(1).hash()).isEqualTo(HASH_2);
	}

	@Test
	void elAccesoSeGuardaLimpioYEnMicrosegundos() {
		assertThat(PRIMERO.momento()).isEqualTo(Instant.parse("2026-11-08T15:04:05.123456Z"));
		assertThat(SEGUNDO.usuario()).isEqualTo(Acceso.ANONIMO);
		assertThat(SEGUNDO.roles()).isEmpty();
		Acceso raro = new Acceso(Instant.EPOCH, "a\nb", "R", "GET", "/api/x\r\n" + "y".repeat(600), 200, -4, " ");
		assertThat(raro.usuario()).isEqualTo("a?b");
		assertThat(raro.ruta()).hasSize(500).doesNotContain("\n");
		assertThat(raro.duracionMs()).isZero();
		assertThat(raro.ip()).isNull();
	}

	@Test
	void unaCadenaSinTocarCuadra() {
		Cadena.Verificacion v = revisar(dos());
		assertThat(v.integra()).isTrue();
		assertThat(v.revisados()).isEqualTo(2);
		assertThat(v.ultimoId()).isEqualTo(2);
		assertThat(v.ultimoHash()).isEqualTo(HASH_2);
		assertThat(revisar(List.of()).integra()).isTrue();
	}

	@Test
	void cambiarUnCampoSeNota() {
		Registro r1 = dos().get(0);
		Acceso otro = new Acceso(PRIMERO.momento(), PRIMERO.usuario(), PRIMERO.roles(), "DELETE", PRIMERO.ruta(),
				PRIMERO.estado(), PRIMERO.duracionMs(), PRIMERO.ip());
		Cadena.Verificacion v = revisar(List.of(new Registro(1, otro, r1.hashAnterior(), r1.hash()), dos().get(1)));
		assertThat(v.integra()).isFalse();
		assertThat(v.roturaEn()).isEqualTo(1);
		assertThat(v.motivo()).contains("cambió");
		assertThat(v.revisados()).isZero();
	}

	@Test
	void borrarUnRegistroSeNotaPorElHueco() {
		Registro r1 = dos().get(0);
		Registro r2 = dos().get(1);
		Registro r3 = Cadena.encadenar(2, r2.hash(), PRIMERO);
		Cadena.Verificacion v = revisar(List.of(r1, r3));
		assertThat(v.integra()).isFalse();
		assertThat(v.roturaEn()).isEqualTo(2);
		assertThat(v.motivo()).isEqualTo("Falta el registro 2");
	}

	@Test
	void rehacerUnRegistroConSuHashNoEncadenaConElSiguiente() {
		// Quien cambia un registro y recalcula su hash rompe el enlace con el siguiente
		Acceso otro = new Acceso(PRIMERO.momento(), "999", "ADMIN", "GET", "/api/usuarios", 200, 1, null);
		Registro falso = Cadena.encadenar(0, Cadena.GENESIS, otro);
		Cadena.Verificacion v = revisar(List.of(falso, dos().get(1)));
		assertThat(v.integra()).isFalse();
		assertThat(v.roturaEn()).isEqualTo(2);
		assertThat(v.motivo()).contains("no encadena");
	}

	@Test
	void rehacerTodaLaCadenaSeNotaContraUnSelloGuardado() {
		// Quien rehace toda la cadena desde cero la deja "íntegra", pero no coincide con el sello que guardó el PC
		Acceso otro = new Acceso(PRIMERO.momento(), "999", "ADMIN", "GET", "/api/usuarios", 200, 1, null);
		Registro f1 = Cadena.encadenar(0, Cadena.GENESIS, otro);
		List<Registro> rehecha = List.of(f1, Cadena.encadenar(1, f1.hash(), SEGUNDO));
		assertThat(revisar(rehecha).integra()).isTrue();

		Cadena.Verificador contraSello = new Cadena.Verificador(1, HASH_1);
		rehecha.stream().filter(r -> r.id() >= contraSello.leerDesde()).forEach(contraSello::revisar);
		Cadena.Verificacion v = contraSello.resultado();
		assertThat(v.integra()).isFalse();
		assertThat(v.roturaEn()).isEqualTo(1);
		assertThat(v.motivo()).contains("sello");
	}

	@Test
	void desdeUnSelloSoloRevisaLoNuevo() {
		Cadena.Verificador v = new Cadena.Verificador(1, HASH_1);
		assertThat(v.leerDesde()).isEqualTo(1);
		dos().forEach(v::revisar);
		Cadena.Verificacion r = v.resultado();
		assertThat(r.integra()).isTrue();
		assertThat(r.revisados()).isEqualTo(1);
		assertThat(r.ultimoHash()).isEqualTo(HASH_2);
	}

	@Test
	void cortarElFinalSeNotaPorqueElSelloYaNoEsta() {
		// Se borró el final de la tabla: al leer desde el registro 2 del sello no llega nada
		Cadena.Verificacion r = new Cadena.Verificador(2, HASH_2).resultado();
		assertThat(r.integra()).isFalse();
		assertThat(r.motivo()).contains("ya no está");
	}

}
