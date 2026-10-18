package co.abogados.procesos.infraestructura.persistencia;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.Reportes.CargaAbogado;
import co.abogados.procesos.dominio.modelo.Reportes.CumplimientoTerminos;
import co.abogados.procesos.dominio.modelo.Reportes.ResultadoRama;
import co.abogados.procesos.dominio.puertos.ReporteRepositorio;

@Repository
public class ReporteRepositorioJdbc implements ReporteRepositorio {

	private final JdbcClient jdbc;

	public ReporteRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<CargaAbogado> carga(LocalDate hoy) {
		return jdbc.sql("""
				WITH personas AS (
				    SELECT id_persona FROM procesos.asignaciones WHERE rol = 'RESPONSABLE'
				    UNION SELECT id_responsable FROM procesos.terminos WHERE estado = 'PENDIENTE'
				), carga_procesos AS (
				    SELECT a.id_persona,
				           count(*) FILTER (WHERE p.estado = 'ACTIVO') AS activos,
				           count(*) FILTER (WHERE p.estado = 'SUSPENDIDO') AS suspendidos
				    FROM procesos.asignaciones a JOIN procesos.procesos p ON p.id_proceso = a.id_proceso
				    WHERE a.rol = 'RESPONSABLE' GROUP BY a.id_persona
				), terminos AS (
				    SELECT id_responsable AS id_persona, count(*) AS pendientes,
				           count(*) FILTER (WHERE fecha_vencimiento < :hoy) AS vencidos
				    FROM procesos.terminos WHERE estado = 'PENDIENTE' GROUP BY id_responsable
				), audiencias AS (
				    SELECT coalesce(ac.id_persona, r.id_persona) AS id_persona, count(*) AS proximas
				    FROM procesos.actuaciones ac
				    LEFT JOIN procesos.asignaciones r ON r.id_proceso = ac.id_proceso AND r.rol = 'RESPONSABLE'
				    WHERE ac.tipo = 'AUDIENCIA' AND ac.fecha BETWEEN :hoy AND :hoy + 30
				    GROUP BY 1
				)
				SELECT pe.id_persona, coalesce(p.activos, 0) AS activos, coalesce(p.suspendidos, 0) AS suspendidos,
				       coalesce(t.pendientes, 0) AS pendientes, coalesce(t.vencidos, 0) AS vencidos,
				       coalesce(au.proximas, 0) AS audiencias
				FROM personas pe
				LEFT JOIN carga_procesos p ON p.id_persona = pe.id_persona
				LEFT JOIN terminos t ON t.id_persona = pe.id_persona
				LEFT JOIN audiencias au ON au.id_persona = pe.id_persona
				ORDER BY coalesce(p.activos, 0) DESC, coalesce(t.pendientes, 0) DESC
				""")
				.param("hoy", hoy)
				.query((rs, i) -> new CargaAbogado(rs.getLong("id_persona"), rs.getInt("activos"), rs.getInt("suspendidos"),
						rs.getInt("pendientes"), rs.getInt("vencidos"), rs.getInt("audiencias")))
				.list();
	}

	@Override
	public List<CumplimientoTerminos> cumplimiento(LocalDate desde, LocalDate hasta, LocalDate hoy) {
		return jdbc.sql("""
				SELECT id_responsable, count(*) AS total,
				       count(*) FILTER (WHERE estado = 'CUMPLIDO' AND cumplido_en <= fecha_vencimiento) AS a_tiempo,
				       count(*) FILTER (WHERE estado = 'CUMPLIDO' AND cumplido_en > fecha_vencimiento) AS tarde,
				       count(*) FILTER (WHERE estado = 'CUMPLIDO' AND cumplido_en IS NULL) AS sin_fecha,
				       count(*) FILTER (WHERE estado = 'PENDIENTE' AND fecha_vencimiento < :hoy) AS vencidos,
				       count(*) FILTER (WHERE estado = 'PENDIENTE' AND fecha_vencimiento >= :hoy) AS pendientes
				FROM procesos.terminos
				WHERE fecha_vencimiento BETWEEN :desde AND :hasta
				GROUP BY id_responsable
				ORDER BY id_responsable
				""")
				.param("desde", desde)
				.param("hasta", hasta)
				.param("hoy", hoy)
				.query((rs, i) -> new CumplimientoTerminos(rs.getLong("id_responsable"), rs.getInt("total"),
						rs.getInt("a_tiempo"), rs.getInt("tarde"), rs.getInt("sin_fecha"), rs.getInt("vencidos"),
						rs.getInt("pendientes")))
				.list();
	}

	@Override
	public List<ResultadoRama> resultados(LocalDate desde, LocalDate hasta) {
		return jdbc.sql("""
				SELECT r.codigo, r.nombre,
				       count(pr.id_providencia) FILTER (WHERE pr.sentido = 'FAVORABLE') AS favorables,
				       count(pr.id_providencia) FILTER (WHERE pr.sentido = 'PARCIAL') AS parciales,
				       count(pr.id_providencia) FILTER (WHERE pr.sentido = 'DESFAVORABLE') AS desfavorables,
				       count(pr.id_providencia) FILTER (WHERE pr.id_providencia IS NOT NULL AND pr.sentido IS NULL) AS sin_sentido
				FROM procesos.ramas r
				LEFT JOIN procesos.tipos_proceso tp ON tp.codigo_rama = r.codigo
				LEFT JOIN procesos.procesos p ON p.id_tipo = tp.id_tipo
				LEFT JOIN procesos.providencias pr ON pr.id_proceso = p.id_proceso AND pr.tipo = 'SENTENCIA'
				     AND pr.fecha_providencia BETWEEN :desde AND :hasta
				GROUP BY r.codigo, r.nombre
				ORDER BY r.nombre
				""")
				.param("desde", desde)
				.param("hasta", hasta)
				.query((rs, i) -> new ResultadoRama(rs.getString("codigo"), rs.getString("nombre"), rs.getInt("favorables"),
						rs.getInt("parciales"), rs.getInt("desfavorables"), rs.getInt("sin_sentido")))
				.list();
	}

}
