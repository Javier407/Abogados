package co.abogados.procesos.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.Despacho;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.modelo.Rama;
import co.abogados.procesos.dominio.modelo.TipoProceso;
import co.abogados.procesos.dominio.puertos.CatalogoRepositorio;

@Repository
public class CatalogoRepositorioJdbc implements CatalogoRepositorio {

	private static final String SELECT_TIPO =
			"SELECT id_tipo, codigo_rama, nombre, termino_contestacion_dias FROM procesos.tipos_proceso ";
	private static final String SELECT_DESPACHO = "SELECT id_despacho, nombre, ciudad, cod_dane, cod_entidad, "
			+ "cod_especialidad, cod_despacho, codigo_rama FROM procesos.despachos ";

	private final JdbcClient jdbc;

	public CatalogoRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Rama> ramas() {
		return jdbc.sql("SELECT codigo, nombre, norma FROM procesos.ramas ORDER BY nombre")
				.query((rs, i) -> new Rama(rs.getString(1), rs.getString(2), rs.getString(3)))
				.list();
	}

	@Override
	public List<TipoProceso> tipos(String rama) {
		if (rama == null || rama.isBlank()) {
			return jdbc.sql(SELECT_TIPO + "ORDER BY codigo_rama, nombre").query(this::aTipo).list();
		}
		return jdbc.sql(SELECT_TIPO + "WHERE codigo_rama = :rama ORDER BY nombre")
				.param("rama", rama.toUpperCase())
				.query(this::aTipo)
				.list();
	}

	@Override
	public List<Despacho> despachos(String rama) {
		if (rama == null || rama.isBlank()) {
			return jdbc.sql(SELECT_DESPACHO + "ORDER BY ciudad, nombre").query(this::aDespacho).list();
		}
		return jdbc.sql(SELECT_DESPACHO + "WHERE codigo_rama = :rama ORDER BY ciudad, nombre")
				.param("rama", rama.toUpperCase())
				.query(this::aDespacho)
				.list();
	}

	@Override
	public Optional<TipoProceso> tipo(short idTipo) {
		return jdbc.sql(SELECT_TIPO + "WHERE id_tipo = :id").param("id", idTipo).query(this::aTipo).optional();
	}

	@Override
	public Optional<Despacho> despacho(short idDespacho) {
		return jdbc.sql(SELECT_DESPACHO + "WHERE id_despacho = :id")
				.param("id", idDespacho)
				.query(this::aDespacho)
				.optional();
	}

	@Override
	public Optional<Despacho> despachoDeRadicado(Radicado r) {
		return jdbc.sql(SELECT_DESPACHO + """
				WHERE cod_dane = :dane AND cod_entidad = :entidad
				  AND cod_especialidad = :especialidad AND cod_despacho = :despacho
				""")
				.param("dane", r.codDane())
				.param("entidad", r.codEntidad())
				.param("especialidad", r.codEspecialidad())
				.param("despacho", r.codDespacho())
				.query(this::aDespacho)
				.optional();
	}

	private TipoProceso aTipo(ResultSet rs, int fila) throws SQLException {
		return new TipoProceso(rs.getShort("id_tipo"), rs.getString("codigo_rama"), rs.getString("nombre"),
				ProcesoRepositorioJdbc.corto(rs, "termino_contestacion_dias"));
	}

	private Despacho aDespacho(ResultSet rs, int fila) throws SQLException {
		return new Despacho(rs.getShort("id_despacho"), rs.getString("nombre"), rs.getString("ciudad"),
				rs.getString("cod_dane"), rs.getString("cod_entidad"), rs.getString("cod_especialidad"),
				rs.getString("cod_despacho"), rs.getString("codigo_rama"));
	}

}
