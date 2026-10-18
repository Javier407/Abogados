package co.abogados.procesos.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.Portal.ResumenPublicado;
import co.abogados.procesos.dominio.puertos.ResumenRepositorio;

@Repository
public class ResumenRepositorioJdbc implements ResumenRepositorio {

	private final JdbcClient jdbc;

	public ResumenRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public ResumenPublicado guardar(int idProceso, String texto, long publicadoPor) {
		return jdbc.sql("""
				INSERT INTO procesos.resumenes_cliente (id_proceso, texto, publicado_por) VALUES (:p, :t, :u)
				RETURNING id_resumen, id_proceso, texto, publicado_por, publicado_en
				""").param("p", idProceso).param("t", texto).param("u", publicadoPor)
				.query(ResumenRepositorioJdbc::leer).single();
	}

	@Override
	public Optional<ResumenPublicado> ultimo(int idProceso) {
		return jdbc.sql("""
				SELECT id_resumen, id_proceso, texto, publicado_por, publicado_en FROM procesos.resumenes_cliente
				WHERE id_proceso = :p ORDER BY publicado_en DESC, id_resumen DESC LIMIT 1
				""").param("p", idProceso).query(ResumenRepositorioJdbc::leer).optional();
	}

	private static ResumenPublicado leer(ResultSet rs, int fila) throws SQLException {
		return new ResumenPublicado(rs.getInt("id_resumen"), rs.getInt("id_proceso"), rs.getString("texto"),
				rs.getLong("publicado_por"), rs.getTimestamp("publicado_en").toInstant());
	}

}
