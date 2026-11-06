package co.abogados.procesos.infraestructura.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** F9: si RabbitMQ está caído, los eventos esperan en el outbox y salen en orden cuando vuelve. */
class RelayOutboxTest {

	static class OutboxEnMemoria implements Outbox {
		final Map<Long, Pendiente> pendientes = new LinkedHashMap<>();
		final Map<Long, String> fallos = new LinkedHashMap<>();
		long siguiente = 1;

		public void agregar(String clave, String cuerpo) { pendientes.put(siguiente, new Pendiente(siguiente++, clave, cuerpo)); }
		public List<Pendiente> pendientes(int max) { return pendientes.values().stream().limit(max).toList(); }
		public void marcarEnviado(long id) { pendientes.remove(id); }
		public void marcarFallo(long id, String error) { fallos.put(id, error); }
		public int purgar(Duration d) { return 0; }
	}

	static class RabbitDeMentira implements RelayOutbox.Envio {
		boolean caido;
		final List<String> recibidos = new ArrayList<>();

		public void enviar(String clave, String cuerpo, String id) {
			if (caido) {
				throw new IllegalStateException("Connection refused");
			}
			recibidos.add(id + " " + clave);
		}
	}

	final OutboxEnMemoria outbox = new OutboxEnMemoria();
	final RabbitDeMentira rabbit = new RabbitDeMentira();
	final RelayOutbox relay = new RelayOutbox(outbox, rabbit,
			new TransactionTemplate(Mockito.mock(PlatformTransactionManager.class)));

	@Test
	void conRabbitCaidoNadaSePierdeYAlVolverSaleEnOrden() {
		PublicadorEventosOutbox publicador = new PublicadorEventosOutbox(outbox, new com.fasterxml.jackson.databind.ObjectMapper());
		publicador.resumenPublicado(new co.abogados.procesos.dominio.modelo.Portal.ResumenAvisado(1, 34,
				"11001-31-03-032-2026-00501-00", 6, java.time.Instant.parse("2026-10-18T15:00:00Z")));
		outbox.agregar("termino.por_vencer", "{\"id_termino\":7}");
		rabbit.caido = true;

		assertThat(relay.enviarPendientes()).isZero();
		assertThat(relay.enviarPendientes()).isZero();
		assertThat(outbox.pendientes).hasSize(2);
		assertThat(outbox.fallos).containsKey(1L);

		rabbit.caido = false;
		assertThat(relay.enviarPendientes()).isEqualTo(2);
		assertThat(rabbit.recibidos).containsExactly("procesos-1 portal.resumen_publicado", "procesos-2 termino.por_vencer");
		assertThat(outbox.pendientes).isEmpty();
	}

	@Test
	void elCuerpoConservaElContratoDelEvento() {
		new PublicadorEventosOutbox(outbox, new com.fasterxml.jackson.databind.ObjectMapper())
				.resumenPublicado(new co.abogados.procesos.dominio.modelo.Portal.ResumenAvisado(3, 34, "R", 6,
						java.time.Instant.parse("2026-10-18T15:00:00Z")));

		assertThat(outbox.pendientes.get(1L).cuerpo()).isEqualTo(
				"{\"id_resumen\":3,\"id_proceso\":34,\"radicado\":\"R\",\"id_cliente\":6,\"ocurrido\":\"2026-10-18T15:00:00Z\"}");
	}

}
