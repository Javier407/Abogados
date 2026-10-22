package co.abogados.procesos.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.CambioRegla;
import co.abogados.procesos.dominio.modelo.ReglaDatos;
import co.abogados.procesos.dominio.modelo.ReglaTermino;
import co.abogados.procesos.dominio.modelo.ReglaVista;
import co.abogados.procesos.dominio.puertos.ReglaTerminoRepositorio;

@Repository
public class ReglaTerminoRepositorioJdbc implements ReglaTerminoRepositorio {

	private static final String COLUMNAS = """
			id_regla, tipo_providencia, codigo_rama, id_tipo, patron, lado, descripcion, dias, norma, prioridad,
			activa, validada, validada_por, validada_en, modificada_por, modificada_en
			""";

	private final JdbcClient jdbc;

	public ReglaTerminoRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<ReglaTermino> reglas() {
		return jdbc.sql("""
				SELECT id_regla, tipo_providencia, codigo_rama, id_tipo, patron, lado, descripcion, dias, norma,
				       prioridad, validada
				FROM procesos.reglas_termino WHERE activa ORDER BY prioridad, id_regla
				""")
				.query((rs, i) -> new ReglaTermino(rs.getInt("id_regla"), rs.getString("tipo_providencia"),
						rs.getString("codigo_rama"), rs.getObject("id_tipo", Short.class), rs.getString("patron"),
						rs.getString("lado"), rs.getString("descripcion"), rs.getObject("dias", Integer.class),
						rs.getString("norma"), rs.getInt("prioridad"), rs.getBoolean("validada")))
				.list();
	}

	@Override
	public Optional<Integer> diasContestacion(short idTipo) {
		return jdbc.sql("SELECT termino_contestacion_dias FROM procesos.tipos_proceso WHERE id_tipo = :id")
				.param("id", idTipo)
				.query((rs, i) -> rs.getObject(1, Integer.class))
				.list().stream().filter(d -> d != null).findFirst();    // NULL en la columna = no aplica
	}

	@Override
	public List<ReglaVista> listar() {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM procesos.reglas_termino ORDER BY tipo_providencia, prioridad, id_regla")
				.query((rs, i) -> vista(rs)).list();
	}

	@Override
	public Optional<ReglaVista> buscar(int idRegla) {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM procesos.reglas_termino WHERE id_regla = :id")
				.param("id", idRegla).query((rs, i) -> vista(rs)).optional();
	}

	@Override
	public int crear(ReglaDatos d, long usuario) {
		// id_regla es smallint sin secuencia (las reglas de F7 tienen ids fijos): se toma el siguiente.
		return jdbc.sql("""
				INSERT INTO procesos.reglas_termino (id_regla, tipo_providencia, codigo_rama, id_tipo, patron, lado,
				       descripcion, dias, norma, prioridad, activa, validada, modificada_por, modificada_en)
				VALUES ((SELECT COALESCE(MAX(id_regla), 0) + 1 FROM procesos.reglas_termino), :tipo, :rama, :idTipo,
				       :patron, :lado, :descripcion, :dias, :norma, :prioridad, :activa, false, :usuario, now())
				RETURNING id_regla
				""")
				.params(parametros(d)).param("usuario", usuario)
				.query(Integer.class).single();
	}

	@Override
	public void actualizar(int idRegla, ReglaDatos d, long usuario, boolean desvalidar) {
		jdbc.sql("""
				UPDATE procesos.reglas_termino SET tipo_providencia = :tipo, codigo_rama = :rama, id_tipo = :idTipo,
				       patron = :patron, lado = :lado, descripcion = :descripcion, dias = :dias, norma = :norma,
				       prioridad = :prioridad, activa = :activa, modificada_por = :usuario, modificada_en = now(),
				       validada = validada AND NOT :desvalidar,
				       validada_por = CASE WHEN :desvalidar THEN NULL ELSE validada_por END,
				       validada_en = CASE WHEN :desvalidar THEN NULL ELSE validada_en END
				WHERE id_regla = :id
				""")
				.params(parametros(d)).param("usuario", usuario).param("desvalidar", desvalidar).param("id", idRegla)
				.update();
	}

	@Override
	public void validar(int idRegla, long usuario) {
		jdbc.sql("""
				UPDATE procesos.reglas_termino SET validada = true, validada_por = :usuario, validada_en = now()
				WHERE id_regla = :id
				""").param("usuario", usuario).param("id", idRegla).update();
	}

	@Override
	public void desvalidar(int idRegla, long usuario) {
		jdbc.sql("""
				UPDATE procesos.reglas_termino SET validada = false, validada_por = NULL, validada_en = NULL,
				       modificada_por = :usuario, modificada_en = now()
				WHERE id_regla = :id
				""").param("usuario", usuario).param("id", idRegla).update();
	}

	@Override
	public void registrar(int idRegla, long usuario, String accion, String detalle) {
		jdbc.sql("""
				INSERT INTO procesos.reglas_termino_historial (id_regla, id_usuario, accion, detalle)
				VALUES (:id, :usuario, :accion, :detalle)
				""")
				.param("id", idRegla).param("usuario", usuario).param("accion", accion)
				.param("detalle", detalle == null || detalle.length() <= 500 ? detalle : detalle.substring(0, 500))
				.update();
	}

	@Override
	public List<CambioRegla> historial(int idRegla) {
		return jdbc.sql("""
				SELECT momento, id_usuario, accion, detalle FROM procesos.reglas_termino_historial
				WHERE id_regla = :id ORDER BY momento DESC, id_cambio DESC
				""")
				.param("id", idRegla)
				.query((rs, i) -> new CambioRegla(rs.getObject("momento", OffsetDateTime.class), rs.getLong("id_usuario"),
						null, rs.getString("accion"), rs.getString("detalle")))
				.list();
	}

	@Override
	public void cambiarContestacion(short idTipo, Integer dias) {
		jdbc.sql("UPDATE procesos.tipos_proceso SET termino_contestacion_dias = :dias WHERE id_tipo = :id")
				.param("dias", dias).param("id", idTipo).update();
	}

	private static Map<String, Object> parametros(ReglaDatos d) {
		Map<String, Object> p = new HashMap<>();
		p.put("tipo", d.tipoProvidencia());
		p.put("rama", d.codigoRama());
		p.put("idTipo", d.idTipo());
		p.put("patron", d.patron());
		p.put("lado", d.lado());
		p.put("descripcion", d.descripcion());
		p.put("dias", d.dias());
		p.put("norma", d.norma());
		p.put("prioridad", d.prioridad());
		p.put("activa", d.activa());
		return p;
	}

	private static ReglaVista vista(ResultSet rs) throws SQLException {
		return new ReglaVista(rs.getInt("id_regla"), rs.getString("tipo_providencia"), rs.getString("codigo_rama"),
				rs.getObject("id_tipo", Short.class), rs.getString("patron"), rs.getString("lado"),
				rs.getString("descripcion"), rs.getObject("dias", Integer.class), rs.getString("norma"),
				rs.getInt("prioridad"), rs.getBoolean("activa"), rs.getBoolean("validada"),
				rs.getObject("validada_por", Long.class), null, rs.getObject("validada_en", OffsetDateTime.class),
				rs.getObject("modificada_por", Long.class), rs.getObject("modificada_en", OffsetDateTime.class));
	}

}
