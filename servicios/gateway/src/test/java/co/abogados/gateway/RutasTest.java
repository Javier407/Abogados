package co.abogados.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest(properties = { "GABINETE_URL=http://gabinete:8080", "PROCESOS_URL=http://procesos:8080" })
class RutasTest {

	@Autowired
	RouteLocator rutas;

	@Test
	void enrutaCadaRecursoASuServicio() {
		List<Route> todas = rutas.getRoutes().collectList().block();

		assertThat(todas).extracting(Route::getId).containsExactlyInAnyOrder("gabinete", "procesos");
		assertThat(todas).filteredOn(r -> r.getId().equals("gabinete")).singleElement()
				.satisfies(r -> assertThat(r.getUri().toString()).isEqualTo("http://gabinete:8080"));
		assertThat(todas).filteredOn(r -> r.getId().equals("procesos")).singleElement()
				.satisfies(r -> assertThat(r.getUri().toString()).isEqualTo("http://procesos:8080"));
	}

}
