package co.abogados.procesos.infraestructura.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.ReglaTermino;
import co.abogados.procesos.dominio.puertos.ReglaTerminoRepositorio;

@Repository
public class ReglaTerminoRepositorioJdbc implements ReglaTerminoRepositorio {

	private final JdbcClient jdbc;

	public ReglaTerminoRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<ReglaTermino> reglas() {
		return jdbc.sql("""
				SELECT id_regla, tipo_providencia, codigo_rama, id_tipo, patron, lado, descripcion, dias, norma,
				       prioridad, validada
				FROM procesos.reglas_termino ORDER BY prioridad, id_regla
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

}
