package co.abogados.procesos.infraestructura.persistencia;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.Cierre;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.puertos.CierreRepositorio;

/**
 * Cierre y reapertura sobre procesos.procesos (columnas de la migración 019). Los términos y las audiencias no se
 * tocan: la agenda y los avisos ya dejan fuera los de procesos cerrados, y vuelven solos si el proceso se reabre.
 */
@Repository
public class CierreRepositorioJdbc implements CierreRepositorio {

	private final JdbcClient jdbc;

	public CierreRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Cierre.Efectos cerrar(int idProceso, Cierre c, long autor) {
		int terminos = jdbc.sql("SELECT count(*) FROM procesos.terminos WHERE id_proceso = :p AND estado = 'PENDIENTE'")
				.param("p", idProceso).query(Integer.class).single();
		int audiencias = jdbc.sql("""
				SELECT count(*) FROM procesos.actuaciones
				WHERE id_proceso = :p AND tipo = 'AUDIENCIA' AND fecha >= CURRENT_DATE
				""")
				.param("p", idProceso).query(Integer.class).single();
		boolean vigilado = jdbc.sql("SELECT vigilado FROM procesos.procesos WHERE id_proceso = :p")
				.param("p", idProceso).query(Boolean.class).single();
		jdbc.sql("""
				UPDATE procesos.procesos
				SET etapa_antes_cierre = etapa, estado = :estado, etapa = 'CERRADO', fecha_cierre = :fecha,
				    motivo_cierre = :motivo, nota_cierre = :nota, cerrado_por = :autor, vigilado = false
				WHERE id_proceso = :p
				""")
				.param("p", idProceso)
				.param("estado", c.estado().name())
				.param("fecha", c.fecha())
				.param("motivo", c.motivo().name())
				.param("nota", c.nota())
				.param("autor", autor)
				.update();
		return new Cierre.Efectos(terminos, audiencias, vigilado);
	}

	@Override
	public void reabrir(int idProceso, EtapaProceso etapa) {
		jdbc.sql("""
				UPDATE procesos.procesos
				SET estado = 'ACTIVO', etapa = :etapa, fecha_cierre = NULL, motivo_cierre = NULL, nota_cierre = NULL,
				    cerrado_por = NULL, etapa_antes_cierre = NULL
				WHERE id_proceso = :p
				""")
				.param("p", idProceso)
				.param("etapa", etapa.name())
				.update();
	}

}
