package co.abogados.procesos.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.Actuacion;
import co.abogados.procesos.dominio.modelo.TipoActuacion;
import co.abogados.procesos.dominio.puertos.ActuacionRepositorio;

@Repository
public class ActuacionRepositorioJdbc implements ActuacionRepositorio {

	private static final String SELECT = """
			SELECT id_actuacion, id_proceso, fecha, tipo, descripcion, id_persona, id_providencia, documento_url,
			       registrado_por
			FROM procesos.actuaciones
			""";

	private final JdbcClient jdbc;

	public ActuacionRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Actuacion> deProceso(int idProceso) {
		return jdbc.sql(SELECT + " WHERE id_proceso = :id ORDER BY fecha DESC, id_actuacion DESC")
				.param("id", idProceso)
				.query(ActuacionRepositorioJdbc::leer)
				.list();
	}

	@Override
	public Optional<Actuacion> buscar(int idActuacion) {
		return jdbc.sql(SELECT + " WHERE id_actuacion = :id")
				.param("id", idActuacion)
				.query(ActuacionRepositorioJdbc::leer)
				.optional();
	}

	@Override
	public int crear(Actuacion a) {
		return jdbc.sql("""
				INSERT INTO procesos.actuaciones (id_proceso, fecha, tipo, descripcion, id_persona, id_providencia,
				       documento_url, registrado_por)
				VALUES (:proceso, :fecha, :tipo, :descripcion, :persona, :providencia, :documento, :quien)
				RETURNING id_actuacion
				""")
				.param("proceso", a.idProceso())
				.param("fecha", a.fecha())
				.param("tipo", a.tipo().name())
				.param("descripcion", a.descripcion())
				.param("persona", a.idPersona())
				.param("providencia", a.idProvidencia())
				.param("documento", a.documentoUrl())
				.param("quien", a.registradoPor())
				.query(Integer.class)
				.single();
	}

	@Override
	public void eliminar(int idActuacion) {
		jdbc.sql("DELETE FROM procesos.actuaciones WHERE id_actuacion = :id").param("id", idActuacion).update();
	}

	private static Actuacion leer(ResultSet rs, int fila) throws SQLException {
		return new Actuacion(rs.getInt("id_actuacion"), rs.getInt("id_proceso"), rs.getObject("fecha", LocalDate.class),
				TipoActuacion.valueOf(rs.getString("tipo")), rs.getString("descripcion"),
				rs.getObject("id_persona", Long.class), rs.getObject("id_providencia", Integer.class),
				rs.getString("documento_url"), rs.getObject("registrado_por", Long.class));
	}

}
