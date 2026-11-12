package co.abogados.gateway.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * De punta a punta en el gateway: cada petición a /api queda en la bitácora, también las rechazadas, a nombre de
 * quien la hizo; solo el administrador la consulta; y la revisión detecta un registro alterado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
		"GABINETE_URL=http://127.0.0.1:9", "PROCESOS_URL=http://127.0.0.1:9", "IDENTIDAD_URL=http://127.0.0.1:9" })
@AutoConfigureWebTestClient(timeout = "30s")
@DirtiesContext
class AuditoriaTest {

	@Autowired
	WebTestClient web;

	@Autowired
	Bitacora bitacora;

	BitacoraEnMemoria memoria;

	@BeforeEach
	void preparar() {
		memoria = (BitacoraEnMemoria) bitacora;
	}

	static JwtMutator como(String documento, String rol) {
		return mockJwt().jwt(j -> j.subject(documento)).authorities(new SimpleGrantedAuthority("ROL_" + rol));
	}

	/** El registro se anota al terminar la respuesta: puede llegar un instante después que ella. */
	Registro ultimoCon(String ruta) throws InterruptedException {
		long hasta = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < hasta) {
			Optional<Registro> r = memoria.todos().stream().filter(x -> x.acceso().ruta().equals(ruta))
					.reduce((a, b) -> b);
			if (r.isPresent()) {
				return r.get();
			}
			Thread.sleep(10);
		}
		throw new AssertionError("No quedó en la bitácora: " + ruta);
	}

	@Test
	void sinTokenQuedaComoAnonimoConSuIpYSinParametros() throws Exception {
		web.get().uri("/api/procesos/77?access_token=secreto&q=x").header("X-Real-IP", "100.101.1.2").exchange()
				.expectStatus().isUnauthorized();
		Acceso a = ultimoCon("/api/procesos/77").acceso();
		assertThat(a.usuario()).isEqualTo(Acceso.ANONIMO);
		assertThat(a.estado()).isEqualTo(401);
		assertThat(a.metodo()).isEqualTo("GET");
		assertThat(a.ip()).isEqualTo("100.101.1.2");
		assertThat(a.ruta()).doesNotContain("secreto");
	}

	@Test
	void loNegadoQuedaANombreDeQuienLoIntento() throws Exception {
		web.mutateWith(como("1020304050", "ABOGADO")).get().uri("/api/reportes/carga").exchange()
				.expectStatus().isForbidden();
		Acceso a = ultimoCon("/api/reportes/carga").acceso();
		assertThat(a.usuario()).isEqualTo("1020304050");
		assertThat(a.roles()).isEqualTo("ABOGADO");
		assertThat(a.estado()).isEqualTo(403);
	}

	@Test
	void soloElAdministradorConsultaLaBitacoraYNadieLaCambia() throws Exception {
		web.mutateWith(como("1020304050", "ABOGADO")).get().uri("/api/reportes/resultados").exchange();
		ultimoCon("/api/reportes/resultados");

		web.mutateWith(como("79000111", "ADMIN")).get()
				.uri("/api/auditoria/registros?usuario=1020304050&ruta=REPORTES/resultados&limite=5").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.registros[0].acceso.usuario").isEqualTo("1020304050")
				.jsonPath("$.registros[0].acceso.ruta").isEqualTo("/api/reportes/resultados")
				.jsonPath("$.registros[0].hash").value(h -> assertThat((String) h).hasSize(64));

		web.mutateWith(como("80111222", "SOCIO")).get().uri("/api/auditoria/registros").exchange()
				.expectStatus().isForbidden();
		web.mutateWith(como("79000111", "ADMIN")).delete().uri("/api/auditoria/registros/1").exchange()
				.expectStatus().isForbidden();
		web.mutateWith(como("79000111", "ADMIN")).get().uri("/api/auditoria/registros?desde=08-11-2026").exchange()
				.expectStatus().isBadRequest();
		web.mutateWith(como("79000111", "ADMIN")).get().uri("/api/auditoria/registros/999999").exchange()
				.expectStatus().isNotFound();

		// Consultar la bitácora también queda anotado
		assertThat(ultimoCon("/api/auditoria/registros").acceso().usuario()).isEqualTo("79000111");
	}

	@Test
	void laRevisionDetectaUnRegistroAlterado() throws Exception {
		web.get().uri("/api/procesos/1").exchange();
		web.get().uri("/api/procesos/2").exchange();
		ultimoCon("/api/procesos/2");

		web.mutateWith(como("79000111", "ADMIN")).get().uri("/api/auditoria/verificacion").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.cadena.integra").isEqualTo(true)
				.jsonPath("$.escritura.almacen").isEqualTo("memoria");

		// Alguien con acceso a la base cambia la ruta de un registro (sin poder recalcular toda la cadena)
		List<Registro> todos = memoria.todos();
		int posicion = todos.indexOf(ultimoCon("/api/procesos/1"));
		Registro r = todos.get(posicion);
		Acceso a = r.acceso();
		memoria.alterar(posicion, new Registro(r.id(), new Acceso(a.momento(), a.usuario(), a.roles(), a.metodo(),
				"/api/procesos/999", a.estado(), a.duracionMs(), a.ip()), r.hashAnterior(), r.hash()));

		web.mutateWith(como("79000111", "ADMIN")).get().uri("/api/auditoria/verificacion").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.cadena.integra").isEqualTo(false)
				.jsonPath("$.cadena.roturaEn").isEqualTo((int) r.id());

		web.mutateWith(como("79000111", "ADMIN")).get().uri("/api/auditoria/verificacion?desde=3&hash=no-es-hash")
				.exchange().expectStatus().isBadRequest();
		memoria.alterar(posicion, r);   // deja la cadena como estaba para las demás pruebas
	}

}
