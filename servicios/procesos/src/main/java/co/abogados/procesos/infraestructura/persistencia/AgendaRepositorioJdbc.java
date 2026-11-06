package co.abogados.procesos.infraestructura.persistencia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.AudienciaVista;
import co.abogados.procesos.dominio.puertos.AgendaRepositorio;

@Repository
public class AgendaRepositorioJdbc implements AgendaRepositorio {

	private final JdbcClient jdbc;

	public AgendaRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<AudienciaVista> audiencias(LocalDate desde, LocalDate hasta, Long idAsistente) {
		return jdbc.sql("""
				SELECT a.id_actuacion, a.id_proceso, v.radicado, v.radicado_legible, v.asunto, a.fecha, a.hora,
				       a.descripcion, a.lugar, coalesce(a.id_persona, r.id_persona) AS id_asistente
				FROM procesos.actuaciones a
				JOIN procesos.v_procesos v ON v.id_proceso = a.id_proceso
				LEFT JOIN procesos.asignaciones r ON r.id_proceso = a.id_proceso AND r.rol = 'RESPONSABLE'
				WHERE a.tipo = 'AUDIENCIA' AND a.fecha BETWEEN :desde AND :hasta
				  AND (CAST(:asistente AS bigint) IS NULL OR coalesce(a.id_persona, r.id_persona) = :asistente)
				ORDER BY a.fecha, a.hora NULLS LAST, a.id_actuacion
				""")
				.param("desde", desde)
				.param("hasta", hasta)
				.param("asistente", idAsistente)
				.query((rs, i) -> new AudienciaVista(rs.getInt("id_actuacion"), rs.getInt("id_proceso"),
						rs.getString("radicado"), rs.getString("radicado_legible"), rs.getString("asunto"),
						rs.getObject("fecha", LocalDate.class), rs.getObject("hora", LocalTime.class),
						rs.getString("descripcion"), rs.getString("lugar"), rs.getObject("id_asistente", Long.class)))
				.list();
	}

}
