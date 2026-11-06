package co.abogados.procesos.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.Asignacion;
import co.abogados.procesos.dominio.puertos.AsignacionRepositorio;

@Repository
public class AsignacionRepositorioJdbc implements AsignacionRepositorio {

	private final JdbcClient jdbc;

	public AsignacionRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Asignacion> listar(int idProceso) {
		return jdbc.sql("""
				SELECT id_proceso, id_persona, rol, desde FROM procesos.asignaciones WHERE id_proceso = :p
				ORDER BY CASE rol WHEN 'RESPONSABLE' THEN 0 WHEN 'APOYO' THEN 1 ELSE 2 END, desde, id_persona
				""").param("p", idProceso).query(AsignacionRepositorioJdbc::leer).list();
	}

	@Override
	public Optional<Asignacion> buscar(int idProceso, long idPersona) {
		return jdbc.sql("SELECT id_proceso, id_persona, rol, desde FROM procesos.asignaciones WHERE id_proceso = :p AND id_persona = :q")
				.param("p", idProceso).param("q", idPersona).query(AsignacionRepositorioJdbc::leer).optional();
	}

	@Override
	public void agregar(int idProceso, long idPersona, Asignacion.Rol rol) {
		jdbc.sql("INSERT INTO procesos.asignaciones (id_proceso, id_persona, rol, desde) VALUES (:p, :q, :rol, CURRENT_DATE)")
				.param("p", idProceso).param("q", idPersona).param("rol", rol.name()).update();
	}

	@Override
	public void quitar(int idProceso, long idPersona) {
		jdbc.sql("DELETE FROM procesos.asignaciones WHERE id_proceso = :p AND id_persona = :q AND rol <> 'RESPONSABLE'")
				.param("p", idProceso).param("q", idPersona).update();
	}

	private static Asignacion leer(ResultSet rs, int fila) throws SQLException {
		return new Asignacion(rs.getInt("id_proceso"), rs.getLong("id_persona"), Asignacion.Rol.valueOf(rs.getString("rol")),
				rs.getDate("desde").toLocalDate(), null);
	}

}
