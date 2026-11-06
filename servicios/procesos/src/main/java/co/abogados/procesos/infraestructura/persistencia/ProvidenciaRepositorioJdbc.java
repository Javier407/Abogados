package co.abogados.procesos.infraestructura.persistencia;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.ActuacionDetectada;
import co.abogados.procesos.dominio.modelo.NuevaProvidencia;
import co.abogados.procesos.dominio.modelo.ProvidenciaVista;
import co.abogados.procesos.dominio.puertos.ProvidenciaRepositorio;

@Repository
public class ProvidenciaRepositorioJdbc implements ProvidenciaRepositorio {

	private final JdbcClient jdbc;

	public ProvidenciaRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<ProvidenciaVista> deProceso(int idProceso) {
		return jdbc.sql("""
				SELECT id_providencia, id_proceso, tipo, clase, fecha_providencia, fecha_estado, decision, sentido, fuente
				FROM procesos.providencias WHERE id_proceso = :id
				ORDER BY fecha_estado DESC, id_providencia DESC
				""")
				.param("id", idProceso)
				.query((rs, i) -> new ProvidenciaVista(rs.getInt("id_providencia"), rs.getInt("id_proceso"),
						rs.getString("tipo"), rs.getString("clase"), rs.getObject("fecha_providencia", LocalDate.class),
						rs.getObject("fecha_estado", LocalDate.class), rs.getString("decision"), rs.getString("sentido"),
						rs.getString("fuente")))
				.list();
	}

	@Override
	public boolean existeReferencia(String referenciaExterna) {
		return jdbc.sql("SELECT count(*) FROM procesos.providencias WHERE referencia_externa = :r")
				.param("r", referenciaExterna)
				.query(Integer.class)
				.single() > 0;
	}

	@Override
	public int registrarDetectada(int idProceso, ActuacionDetectada a) {
		String clase = recortar(a.actuacion(), 200);
		String anotacion = a.anotacion() == null || a.anotacion().isBlank() ? null : recortar(a.anotacion(), 1000);
		return jdbc.sql("""
				INSERT INTO procesos.providencias (id_proceso, tipo, clase, fecha_providencia, fecha_estado, decision,
				       fuente, referencia_externa)
				VALUES (:proceso, :tipo, :clase, :fecha, :fecha, :decision, 'VIGILANCIA', :referencia)
				RETURNING id_providencia
				""")
				.param("proceso", idProceso)
				.param("tipo", "SENTENCIA".equals(a.tipo()) ? "SENTENCIA" : "AUTO")
				.param("clase", clase)
				.param("fecha", a.fecha())
				.param("decision", anotacion)
				.param("referencia", a.referencia())
				.query(Integer.class)
				.single();
	}

	@Override
	public int registrarManual(int idProceso, NuevaProvidencia p, Long registradoPor) {
		return jdbc.sql("""
				INSERT INTO procesos.providencias (id_proceso, tipo, clase, fecha_providencia, fecha_estado, decision,
				       sentido, fuente, registrado_por)
				VALUES (:proceso, :tipo, :clase, :fecha, :estado, :decision, :sentido, 'MANUAL', :quien)
				RETURNING id_providencia
				""")
				.param("proceso", idProceso)
				.param("tipo", p.tipo())
				.param("clase", p.clase())
				.param("fecha", p.fechaProvidencia())
				.param("estado", p.fechaEstado())
				.param("decision", p.decision())
				.param("sentido", p.sentido())
				.param("quien", registradoPor)
				.query(Integer.class)
				.single();
	}

	private static String recortar(String texto, int maximo) {
		return texto.length() > maximo ? texto.substring(0, maximo) : texto;
	}

}
