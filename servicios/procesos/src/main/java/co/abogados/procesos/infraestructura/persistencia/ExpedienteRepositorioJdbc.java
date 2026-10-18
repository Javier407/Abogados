package co.abogados.procesos.infraestructura.persistencia;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.EventoExpediente;
import co.abogados.procesos.dominio.modelo.Instancia;
import co.abogados.procesos.dominio.puertos.ExpedienteRepositorio;

@Repository
public class ExpedienteRepositorioJdbc implements ExpedienteRepositorio {

	private final JdbcClient jdbc;

	public ExpedienteRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<EventoExpediente> lineaDeTiempo(int idProceso) {
		return jdbc.sql("""
				SELECT a.fecha, 'ACTUACION' AS categoria, a.tipo, a.id_actuacion AS id, a.descripcion AS titulo,
				       nullif(concat_ws(' · ', CASE WHEN pr.id_providencia IS NOT NULL THEN 'Contra: ' || pr.clase END,
				                 to_char(a.hora, 'HH24:MI'), a.lugar), '') AS detalle,
				       'FIRMA' AS origen
				FROM procesos.actuaciones a
				LEFT JOIN procesos.providencias pr ON pr.id_providencia = a.id_providencia
				WHERE a.id_proceso = :id
				UNION ALL
				SELECT p.fecha_estado, 'PROVIDENCIA', p.tipo, p.id_providencia, p.clase,
				       concat_ws(' · ', p.decision, CASE p.sentido WHEN 'FAVORABLE' THEN 'Favorable al cliente'
				                                                  WHEN 'DESFAVORABLE' THEN 'Desfavorable al cliente'
				                                                  WHEN 'PARCIAL' THEN 'Parcialmente favorable' END),
				       p.fuente
				FROM procesos.providencias p
				WHERE p.id_proceso = :id
				UNION ALL
				SELECT t.fecha_vencimiento, 'TERMINO', t.estado, t.id_termino, t.descripcion,
				       t.dias_habiles || ' días hábiles desde ' || to_char(t.fecha_inicio, 'DD/MM/YYYY'), NULL
				FROM procesos.terminos t
				WHERE t.id_proceso = :id
				ORDER BY 1 DESC, 2, 4 DESC
				""")
				.param("id", idProceso)
				.query((rs, i) -> new EventoExpediente(rs.getObject(1, LocalDate.class), rs.getString("categoria"),
						rs.getString("tipo"), rs.getInt("id"), rs.getString("titulo"), rs.getString("detalle"),
						rs.getString("origen")))
				.list();
	}

	@Override
	public List<Instancia> otrasInstancias(int idProceso) {
		return jdbc.sql("""
				SELECT v.id_proceso, v.radicado_legible, v.instancia, v.despacho_actual, v.estado
				FROM procesos.v_procesos v
				WHERE substr(v.radicado, 1, 21) = (SELECT substr(radicado, 1, 21) FROM procesos.procesos WHERE id_proceso = :id)
				  AND v.id_proceso <> :id
				ORDER BY v.instancia
				""")
				.param("id", idProceso)
				.query((rs, i) -> new Instancia(rs.getInt("id_proceso"), rs.getString("radicado_legible"),
						rs.getInt("instancia"), rs.getString("despacho_actual"), rs.getString("estado")))
				.list();
	}

	@Override
	public void avanzarEtapa(int idProceso, EtapaProceso etapa) {
		jdbc.sql("UPDATE procesos.procesos SET etapa = :etapa WHERE id_proceso = :id AND etapa <> 'CERRADO'")
				.param("etapa", etapa.name())
				.param("id", idProceso)
				.update();
	}

}
