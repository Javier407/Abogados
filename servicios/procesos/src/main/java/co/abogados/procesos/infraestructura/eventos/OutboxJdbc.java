package co.abogados.procesos.infraestructura.eventos;

import java.time.Duration;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxJdbc implements Outbox {

	private final JdbcClient jdbc;

	public OutboxJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void agregar(String clave, String cuerpoJson) {
		jdbc.sql("INSERT INTO procesos.outbox (clave, cuerpo) VALUES (:clave, CAST(:cuerpo AS jsonb))")
				.param("clave", clave).param("cuerpo", cuerpoJson).update();
	}

	@Override
	public List<Pendiente> pendientes(int maximo) {
		return jdbc.sql("""
				SELECT id_evento, clave, cuerpo::text AS cuerpo FROM procesos.outbox
				WHERE enviado_en IS NULL ORDER BY id_evento LIMIT :max FOR UPDATE SKIP LOCKED
				""").param("max", maximo)
				.query((rs, i) -> new Pendiente(rs.getLong("id_evento"), rs.getString("clave"), rs.getString("cuerpo")))
				.list();
	}

	@Override
	public void marcarEnviado(long id) {
		jdbc.sql("UPDATE procesos.outbox SET enviado_en = now(), intentos = intentos + 1, ultimo_error = NULL WHERE id_evento = :id")
				.param("id", id).update();
	}

	@Override
	public void marcarFallo(long id, String error) {
		jdbc.sql("UPDATE procesos.outbox SET intentos = intentos + 1, ultimo_error = :e WHERE id_evento = :id")
				.param("e", error == null ? null : error.length() > 300 ? error.substring(0, 300) : error)
				.param("id", id).update();
	}

	@Override
	public int purgar(Duration antiguedad) {
		return jdbc.sql("DELETE FROM procesos.outbox WHERE enviado_en < now() - make_interval(secs => :s)")
				.param("s", antiguedad.toSeconds()).update();
	}

}
