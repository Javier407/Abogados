package co.abogados.procesos.infraestructura.eventos;

import static co.abogados.procesos.infraestructura.persistencia.JdbcFalso.fila;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.abogados.procesos.dominio.modelo.AvisoAudiencia;
import co.abogados.procesos.dominio.modelo.AvisoTermino;
import co.abogados.procesos.dominio.modelo.ProcesoCreado;
import co.abogados.procesos.infraestructura.persistencia.JdbcFalso;

/** Contrato de los eventos que se escriben en el outbox, la tabla outbox y el envío a RabbitMQ con confirmación. */
class EventosOutboxTest {

	static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

	RelayOutboxTest.OutboxEnMemoria outbox = new RelayOutboxTest.OutboxEnMemoria();
	PublicadorEventosOutbox publicador = new PublicadorEventosOutbox(outbox, new ObjectMapper());

	@Test
	void procesoCreado() {
		publicador.procesoCreado(new ProcesoCreado(31, "11001310303220260050100", 52345678L, AHORA));

		Outbox.Pendiente p = outbox.pendientes.get(1L);
		assertThat(p.clave()).isEqualTo("proceso.creado");
		assertThat(p.cuerpo()).isEqualTo("{\"id_proceso\":31,\"radicado\":\"11001310303220260050100\","
				+ "\"id_abogado_responsable\":52345678,\"ocurrido\":\"2026-10-15T12:00:00Z\"}");
	}

	@Test
	void avisoDeTerminoUsaLaClaveDeSuTipo() {
		publicador.avisoTermino(new AvisoTermino(AvisoTermino.Tipo.VENCIDO, 9, 31, "R", "Contestar",
				LocalDate.of(2026, 10, 14), -1, 52345678L, AHORA));

		Outbox.Pendiente p = outbox.pendientes.get(1L);
		assertThat(p.clave()).isEqualTo("termino.vencido");
		assertThat(p.cuerpo()).contains("\"vence\":\"2026-10-14\"", "\"dias_habiles_restantes\":-1",
				"\"id_abogado\":52345678");
	}

	@Test
	void avisoDeAudienciaConYSinHora() {
		publicador.avisoAudiencia(new AvisoAudiencia(8, 31, "R", "Audiencia inicial", LocalDate.of(2026, 10, 16),
				LocalTime.of(9, 30), "Sala 3", 52345678L, AHORA));
		publicador.avisoAudiencia(new AvisoAudiencia(9, 31, "R", "Audiencia", LocalDate.of(2026, 10, 16), null, null,
				52345678L, AHORA));

		assertThat(outbox.pendientes.get(1L).clave()).isEqualTo("audiencia.proxima");
		assertThat(outbox.pendientes.get(1L).cuerpo()).contains("\"hora\":\"09:30\"", "\"lugar\":\"Sala 3\"");
		assertThat(outbox.pendientes.get(2L).cuerpo()).contains("\"hora\":null");
	}

	@Test
	void siNoSePuedeSerializarFallaSinEscribir() throws Exception {
		ObjectMapper roto = mock(ObjectMapper.class);
		when(roto.writeValueAsString(any())).thenThrow(new JsonProcessingException("roto") { });

		assertThatThrownBy(() -> new PublicadorEventosOutbox(outbox, roto)
				.procesoCreado(new ProcesoCreado(31, "R", 1L, AHORA)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("proceso.creado");
		assertThat(outbox.pendientes).isEmpty();
	}

	// ------------------------------------------------------------------ tabla outbox

	@Test
	void outboxJdbc() {
		JdbcFalso jdbc = new JdbcFalso().responder("enviado_en IS NULL",
				fila("id_evento", 5L, "clave", "proceso.creado", "cuerpo", "{}"));
		OutboxJdbc tabla = new OutboxJdbc(jdbc.cliente);

		tabla.agregar("proceso.creado", "{}");
		assertThat(jdbc.ultima().sql()).contains("CAST(:cuerpo AS jsonb)");
		assertThat(tabla.pendientes(50)).containsExactly(new Outbox.Pendiente(5L, "proceso.creado", "{}"));
		assertThat(jdbc.ultima().params()).containsEntry("max", 50);
		tabla.marcarEnviado(5L);
		assertThat(jdbc.ultima().sql()).contains("enviado_en = now()");
		tabla.marcarFallo(5L, "x".repeat(400));
		assertThat(jdbc.ultima().params()).containsEntry("e", "x".repeat(300));
		tabla.marcarFallo(5L, "Connection refused");
		assertThat(jdbc.ultima().params()).containsEntry("e", "Connection refused");
		tabla.marcarFallo(5L, null);
		assertThat(jdbc.ultima().params()).containsEntry("e", null);
		assertThat(tabla.purgar(Duration.ofDays(7))).isEqualTo(1);
		assertThat(jdbc.ultima().params()).containsEntry("s", 7 * 24 * 3600L);
	}

	// ------------------------------------------------------------------ envío a RabbitMQ

	@Test
	@SuppressWarnings("unchecked")
	void elEnvioEsPersistenteConSuIdYEsperaLaConfirmacion() {
		RabbitTemplate rabbit = mock(RabbitTemplate.class);
		RabbitOperations ops = mock(RabbitOperations.class);
		when(rabbit.invoke(any(RabbitOperations.OperationsCallback.class)))
				.thenAnswer(i -> ((RabbitOperations.OperationsCallback<Object>) i.getArgument(0)).doInRabbit(ops));

		new RelayOutbox.EnvioRabbit(rabbit, "gabinete.eventos").enviar("proceso.creado", "{\"a\":1}", "procesos-5");

		ArgumentCaptor<Message> mensaje = ArgumentCaptor.forClass(Message.class);
		verify(ops).send(eq("gabinete.eventos"), eq("proceso.creado"), mensaje.capture());
		verify(ops).waitForConfirmsOrDie(5_000);
		assertThat(new String(mensaje.getValue().getBody())).isEqualTo("{\"a\":1}");
		assertThat(mensaje.getValue().getMessageProperties().getMessageId()).isEqualTo("procesos-5");
		assertThat(mensaje.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		assertThat(mensaje.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
	}

	@Test
	void cadaTantasVueltasPurgaLosEnviadosAntiguos() {
		int[] purgas = { 0 };
		RelayOutboxTest.OutboxEnMemoria conPurga = new RelayOutboxTest.OutboxEnMemoria() {
			@Override
			public int purgar(Duration d) {
				purgas[0]++;
				assertThat(d).isEqualTo(Duration.ofDays(7));
				return 0;
			}
		};
		RelayOutbox relay = new RelayOutbox(conPurga, (c, j, id) -> { },
				new TransactionTemplate(mock(PlatformTransactionManager.class)));

		for (int i = 0; i < 1800; i++) {
			relay.programado();
		}
		assertThat(purgas[0]).isEqualTo(1);
	}

}
