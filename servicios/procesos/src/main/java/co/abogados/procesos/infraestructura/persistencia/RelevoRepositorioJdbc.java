package co.abogados.procesos.infraestructura.persistencia;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.Relevo;
import co.abogados.procesos.dominio.puertos.RelevoRepositorio;

/**
 * Relevo del responsable sobre procesos.asignaciones. El historial (procesos.asignaciones_historial) lo escribe el
 * disparador de la migración 019; aquí solo se le dice el motivo, la nota y el autor con variables que viven lo que
 * dura la transacción (set_config con is_local = true).
 */
@Repository
public class RelevoRepositorioJdbc implements RelevoRepositorio {

	private final JdbcClient jdbc;

	public RelevoRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Relevo.Resultado relevar(int idProceso, Long idAnterior, Relevo.Solicitud s, long autor) {
		jdbc.sql("""
				SELECT set_config('procesos.motivo_relevo', :motivo, true),
				       set_config('procesos.nota_relevo', :nota, true),
				       set_config('procesos.autor_relevo', :autor, true)
				""")
				.param("motivo", s.motivo().name())
				.param("nota", s.nota() == null ? "" : s.nota())
				.param("autor", Long.toString(autor))
				.query((rs, i) -> rs.getString(1))
				.list();

		// Solo puede haber un responsable: primero sale (o baja a apoyo) el anterior y luego entra el nuevo.
		if (idAnterior != null) {
			if (s.anteriorQuedaDeApoyo()) {
				jdbc.sql("UPDATE procesos.asignaciones SET rol = 'APOYO' WHERE id_proceso = :p AND id_persona = :anterior")
						.param("p", idProceso).param("anterior", idAnterior).update();
			} else {
				jdbc.sql("DELETE FROM procesos.asignaciones WHERE id_proceso = :p AND id_persona = :anterior")
						.param("p", idProceso).param("anterior", idAnterior).update();
			}
		}
		jdbc.sql("""
				INSERT INTO procesos.asignaciones (id_proceso, id_persona, rol, desde)
				VALUES (:p, :nuevo, 'RESPONSABLE', CURRENT_DATE)
				ON CONFLICT (id_proceso, id_persona) DO UPDATE SET rol = 'RESPONSABLE', desde = CURRENT_DATE
				""")
				.param("p", idProceso).param("nuevo", s.idNuevoResponsable()).update();

		int terminos = 0;
		int audiencias = 0;
		if (s.trasladarPendientes() && idAnterior != null) {
			terminos = jdbc.sql("""
					UPDATE procesos.terminos SET id_responsable = :nuevo
					WHERE id_proceso = :p AND estado = 'PENDIENTE' AND id_responsable = :anterior
					""")
					.param("p", idProceso).param("nuevo", s.idNuevoResponsable()).param("anterior", idAnterior).update();
			audiencias = jdbc.sql("""
					UPDATE procesos.actuaciones SET id_persona = :nuevo
					WHERE id_proceso = :p AND tipo = 'AUDIENCIA' AND fecha >= CURRENT_DATE AND id_persona = :anterior
					""")
					.param("p", idProceso).param("nuevo", s.idNuevoResponsable()).param("anterior", idAnterior).update();
		}
		return new Relevo.Resultado(idAnterior, s.idNuevoResponsable(), terminos, audiencias);
	}

	@Override
	public List<Relevo.Periodo> historial(int idProceso) {
		return jdbc.sql("""
				SELECT id_cambio, id_persona, rol, desde, hasta, motivo, nota, registrado_por, cerrado_por
				FROM procesos.asignaciones_historial
				WHERE id_proceso = :p
				ORDER BY hasta IS NOT NULL, coalesce(hasta, desde) DESC,
				         CASE rol WHEN 'RESPONSABLE' THEN 0 WHEN 'APOYO' THEN 1 ELSE 2 END, id_cambio DESC
				""")
				.param("p", idProceso)
				.query((rs, i) -> new Relevo.Periodo(rs.getLong("id_cambio"), rs.getLong("id_persona"), rs.getString("rol"),
						rs.getObject("desde", LocalDate.class), rs.getObject("hasta", LocalDate.class),
						rs.getString("motivo"), rs.getString("nota"), rs.getObject("registrado_por", Long.class),
						rs.getObject("cerrado_por", Long.class), null))
				.list();
	}

}
