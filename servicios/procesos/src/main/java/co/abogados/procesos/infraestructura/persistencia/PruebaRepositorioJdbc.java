package co.abogados.procesos.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.Prueba;
import co.abogados.procesos.dominio.puertos.PruebaRepositorio;

@Repository
public class PruebaRepositorioJdbc implements PruebaRepositorio {

	private static final String SELECT = """
			SELECT id_prueba, id_proceso, tipo, descripcion, id_perito, estado, fecha_entrega, id_termino
			FROM procesos.pruebas
			""";

	private final JdbcClient jdbc;

	public PruebaRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Prueba> deProceso(int idProceso) {
		return jdbc.sql(SELECT + " WHERE id_proceso = :id ORDER BY id_prueba")
				.param("id", idProceso)
				.query(PruebaRepositorioJdbc::leer)
				.list();
	}

	@Override
	public Optional<Prueba> buscar(int idPrueba) {
		return jdbc.sql(SELECT + " WHERE id_prueba = :id")
				.param("id", idPrueba)
				.query(PruebaRepositorioJdbc::leer)
				.optional();
	}

	@Override
	public int crear(Prueba p) {
		return jdbc.sql("""
				INSERT INTO procesos.pruebas (id_proceso, tipo, descripcion, id_perito, estado, fecha_entrega, id_termino)
				VALUES (:proceso, :tipo, :descripcion, :perito, :estado, :entrega, :termino)
				RETURNING id_prueba
				""")
				.param("proceso", p.idProceso())
				.param("tipo", p.tipo().name())
				.param("descripcion", p.descripcion())
				.param("perito", p.idPerito())
				.param("estado", p.estado().name())
				.param("entrega", p.fechaEntrega())
				.param("termino", p.idTermino())
				.query(Integer.class)
				.single();
	}

	@Override
	public void actualizar(Prueba p) {
		jdbc.sql("""
				UPDATE procesos.pruebas
				SET tipo = :tipo, descripcion = :descripcion, id_perito = :perito, estado = :estado,
				    fecha_entrega = :entrega, id_termino = :termino
				WHERE id_prueba = :id
				""")
				.param("id", p.idPrueba())
				.param("tipo", p.tipo().name())
				.param("descripcion", p.descripcion())
				.param("perito", p.idPerito())
				.param("estado", p.estado().name())
				.param("entrega", p.fechaEntrega())
				.param("termino", p.idTermino())
				.update();
	}

	@Override
	public void eliminar(int idPrueba) {
		jdbc.sql("DELETE FROM procesos.pruebas WHERE id_prueba = :id").param("id", idPrueba).update();
	}

	private static Prueba leer(ResultSet rs, int fila) throws SQLException {
		return new Prueba(rs.getInt("id_prueba"), rs.getInt("id_proceso"), Prueba.Tipo.valueOf(rs.getString("tipo")),
				rs.getString("descripcion"), rs.getObject("id_perito", Integer.class),
				Prueba.Estado.valueOf(rs.getString("estado")), rs.getObject("fecha_entrega", LocalDate.class),
				rs.getObject("id_termino", Integer.class));
	}

}
