package co.abogados.procesos.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.Parte;
import co.abogados.procesos.dominio.modelo.RolParte;
import co.abogados.procesos.dominio.puertos.ParteRepositorio;

@Repository
public class ParteRepositorioJdbc implements ParteRepositorio {

	private static final String SELECT = """
			SELECT id_parte, id_proceso, rol, tipo_documento, numero_documento, nombre, es_cliente, apoderado
			FROM procesos.partes
			""";

	private final JdbcClient jdbc;

	public ParteRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Parte> deProceso(int idProceso) {
		return jdbc.sql(SELECT + " WHERE id_proceso = :id ORDER BY es_cliente DESC, id_parte")
				.param("id", idProceso)
				.query(ParteRepositorioJdbc::leer)
				.list();
	}

	@Override
	public Optional<Parte> buscar(int idParte) {
		return jdbc.sql(SELECT + " WHERE id_parte = :id")
				.param("id", idParte)
				.query(ParteRepositorioJdbc::leer)
				.optional();
	}

	@Override
	public int crear(Parte p) {
		return jdbc.sql("""
				INSERT INTO procesos.partes (id_proceso, rol, tipo_documento, numero_documento, nombre, es_cliente, apoderado)
				VALUES (:proceso, :rol, :tipo, :numero, :nombre, :cliente, :apoderado)
				RETURNING id_parte
				""")
				.param("proceso", p.idProceso())
				.param("rol", p.rol().name())
				.param("tipo", p.tipoDocumento())
				.param("numero", p.numeroDocumento())
				.param("nombre", p.nombre())
				.param("cliente", p.esCliente())
				.param("apoderado", p.apoderado())
				.query(Integer.class)
				.single();
	}

	@Override
	public void actualizar(Parte p) {
		jdbc.sql("""
				UPDATE procesos.partes
				SET rol = :rol, tipo_documento = :tipo, numero_documento = :numero, nombre = :nombre, apoderado = :apoderado
				WHERE id_parte = :id
				""")
				.param("id", p.idParte())
				.param("rol", p.rol().name())
				.param("tipo", p.tipoDocumento())
				.param("numero", p.numeroDocumento())
				.param("nombre", p.nombre())
				.param("apoderado", p.apoderado())
				.update();
	}

	@Override
	public void eliminar(int idParte) {
		jdbc.sql("DELETE FROM procesos.partes WHERE id_parte = :id").param("id", idParte).update();
	}

	private static Parte leer(ResultSet rs, int fila) throws SQLException {
		return new Parte(rs.getInt("id_parte"), rs.getInt("id_proceso"), RolParte.valueOf(rs.getString("rol")),
				rs.getString("tipo_documento"), rs.getString("numero_documento"), rs.getString("nombre"),
				rs.getBoolean("es_cliente"), rs.getString("apoderado"));
	}

}
