package co.abogados.procesos.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.FiltroTerminos;
import co.abogados.procesos.dominio.modelo.Termino;
import co.abogados.procesos.dominio.modelo.TerminoVista;
import co.abogados.procesos.dominio.puertos.TerminoRepositorio;

/** Términos sobre la vista v_terminos, que calcula los días hábiles restantes y la situación de hoy. */
@Repository
public class TerminoRepositorioJdbc implements TerminoRepositorio {

	private static final String SELECT_VISTA = """
			SELECT t.id_termino, t.id_proceso, v.radicado, v.radicado_legible, v.rama, v.asunto, t.id_providencia,
			       t.descripcion, t.fecha_inicio, t.dias_habiles, t.fecha_vencimiento, t.estado, t.id_responsable,
			       t.dias_habiles_restantes, t.situacion
			FROM procesos.v_terminos t
			JOIN procesos.v_procesos v ON v.id_proceso = t.id_proceso
			""";

	private final JdbcClient jdbc;

	public TerminoRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<TerminoVista> listar(FiltroTerminos f) {
		List<String> condiciones = new ArrayList<>();
		Map<String, Object> params = new HashMap<>();
		if (f.situacion() != null) {
			condiciones.add("t.situacion = :situacion");
			params.put("situacion", f.situacion().name());
		}
		if (f.idResponsable() != null) {
			condiciones.add("t.id_responsable = :responsable");
			params.put("responsable", f.idResponsable());
		}
		if (f.idProceso() != null) {
			condiciones.add("t.id_proceso = :proceso");
			params.put("proceso", f.idProceso());
		}
		if (f.desde() != null) {
			condiciones.add("t.fecha_vencimiento >= :desde");
			params.put("desde", f.desde());
		}
		if (f.hasta() != null) {
			condiciones.add("t.fecha_vencimiento <= :hasta");
			params.put("hasta", f.hasta());
		}
		String sql = SELECT_VISTA
				+ (condiciones.isEmpty() ? "" : "WHERE " + String.join(" AND ", condiciones) + "\n")
				+ "ORDER BY t.fecha_vencimiento, t.id_termino";
		return jdbc.sql(sql).params(params).query(this::aVista).list();
	}

	@Override
	public Optional<TerminoVista> buscar(int idTermino) {
		return jdbc.sql(SELECT_VISTA + "WHERE t.id_termino = :id").param("id", idTermino).query(this::aVista).optional();
	}

	@Override
	public int crear(Termino t) {
		Map<String, Object> p = new HashMap<>();
		p.put("proceso", t.idProceso());
		p.put("providencia", t.idProvidencia());
		p.put("descripcion", t.descripcion());
		p.put("inicio", t.fechaInicio());
		p.put("dias", t.diasHabiles());
		p.put("vence", t.fechaVencimiento());
		p.put("estado", t.estado().name());
		p.put("responsable", t.idResponsable());
		return jdbc.sql("""
				INSERT INTO procesos.terminos (id_proceso, id_providencia, descripcion, fecha_inicio, dias_habiles,
				       fecha_vencimiento, estado, id_responsable)
				VALUES (:proceso, :providencia, :descripcion, :inicio, :dias, :vence, :estado, :responsable)
				RETURNING id_termino
				""")
				.params(p)
				.query(Integer.class)
				.single();
	}

	@Override
	public void marcarCumplido(int idTermino) {
		jdbc.sql("UPDATE procesos.terminos SET estado = 'CUMPLIDO' WHERE id_termino = :id").param("id", idTermino).update();
	}

	@Override
	public void eliminar(int idTermino) {
		jdbc.sql("DELETE FROM procesos.terminos WHERE id_termino = :id").param("id", idTermino).update();
	}

	@Override
	public boolean providenciaDelProceso(int idProvidencia, int idProceso) {
		return jdbc.sql("SELECT count(*) FROM procesos.providencias WHERE id_providencia = :p AND id_proceso = :proc")
				.param("p", idProvidencia)
				.param("proc", idProceso)
				.query(Integer.class)
				.single() > 0;
	}

	@Override
	public List<TerminoVista> paraAvisar(int diasAviso, int diasRecordarVencidos) {
		return jdbc.sql(SELECT_VISTA + """
				WHERE t.estado = 'PENDIENTE' AND t.dias_habiles_restantes BETWEEN :minimo AND :maximo
				ORDER BY t.fecha_vencimiento, t.id_termino
				""")
				.param("minimo", -diasRecordarVencidos)
				.param("maximo", diasAviso)
				.query(this::aVista)
				.list();
	}

	private TerminoVista aVista(ResultSet rs, int fila) throws SQLException {
		return new TerminoVista(
				rs.getInt("id_termino"),
				rs.getInt("id_proceso"),
				rs.getString("radicado"),
				rs.getString("radicado_legible"),
				rs.getString("rama"),
				rs.getString("asunto"),
				rs.getObject("id_providencia", Integer.class),
				rs.getString("descripcion"),
				rs.getObject("fecha_inicio", LocalDate.class),
				rs.getInt("dias_habiles"),
				rs.getObject("fecha_vencimiento", LocalDate.class),
				rs.getString("estado"),
				rs.getLong("id_responsable"),
				rs.getInt("dias_habiles_restantes"),
				rs.getString("situacion"));
	}

}
