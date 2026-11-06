package co.abogados.procesos.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.FiltroProcesos;
import co.abogados.procesos.dominio.modelo.Proceso;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;

/**
 * Persistencia de procesos con SQL directo sobre el esquema procesos. Se usa JDBC y no JPA porque
 * el desglose del radicado son columnas calculadas por PostgreSQL y las lecturas salen de la vista v_procesos.
 */
@Repository
public class ProcesoRepositorioJdbc implements ProcesoRepositorio {

	private static final String SELECT_VISTA = """
			SELECT v.id_proceso, v.radicado, v.radicado_legible, p.id_tipo, v.tipo_proceso, v.codigo_rama, v.rama,
			       v.despacho_origen, p.id_despacho_actual, v.despacho_actual, v.instancia, v.id_cliente,
			       v.rol_cliente, v.asunto, v.cuantia, v.clase_cuantia, v.tipo_servicio, v.estado, v.etapa,
			       v.fecha_radicacion, v.vigilado, a.id_persona AS id_responsable
			FROM procesos.v_procesos v
			JOIN procesos.procesos p ON p.id_proceso = v.id_proceso
			LEFT JOIN procesos.asignaciones a ON a.id_proceso = v.id_proceso AND a.rol = 'RESPONSABLE'
			""";

	private final JdbcClient jdbc;

	public ProcesoRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<ProcesoVista> listar(FiltroProcesos f) {
		List<String> condiciones = new ArrayList<>();
		Map<String, Object> params = new HashMap<>();
		if (f.estado() != null) {
			condiciones.add("v.estado = :estado");
			params.put("estado", f.estado().name());
		}
		if (f.rama() != null && !f.rama().isBlank()) {
			condiciones.add("v.codigo_rama = :rama");
			params.put("rama", f.rama().toUpperCase());
		}
		if (f.idCliente() != null) {
			condiciones.add("v.id_cliente = :cliente");
			params.put("cliente", f.idCliente());
		}
		if (f.idAbogado() != null) {
			condiciones.add("EXISTS (SELECT 1 FROM procesos.asignaciones x WHERE x.id_proceso = v.id_proceso AND x.id_persona = :abogado)");
			params.put("abogado", f.idAbogado());
		}
		if (f.texto() != null && !f.texto().isBlank()) {
			condiciones.add("(v.radicado LIKE :digitos OR v.radicado_legible LIKE :texto OR v.asunto ILIKE :texto)");
			String texto = f.texto().strip();
			String digitos = texto.replaceAll("\\D", "");
			params.put("texto", "%" + texto + "%");
			params.put("digitos", digitos.isEmpty() ? "-" : "%" + digitos + "%");
		}
		String sql = SELECT_VISTA
				+ (condiciones.isEmpty() ? "" : "WHERE " + String.join(" AND ", condiciones) + "\n")
				+ "ORDER BY v.fecha_radicacion DESC, v.id_proceso DESC";
		return jdbc.sql(sql).params(params).query(this::aVista).list();
	}

	@Override
	public Optional<ProcesoVista> buscar(int idProceso) {
		return jdbc.sql(SELECT_VISTA + "WHERE v.id_proceso = :id")
				.param("id", idProceso)
				.query(this::aVista)
				.optional();
	}

	@Override
	public Optional<Integer> idPorRadicado(Radicado radicado) {
		return jdbc.sql("SELECT id_proceso FROM procesos.procesos WHERE radicado = :radicado")
				.param("radicado", radicado.numero())
				.query(Integer.class)
				.optional();
	}

	@Override
	public int crear(Proceso p, String nombreCliente) {
		int id = jdbc.sql("""
				INSERT INTO procesos.procesos (radicado, id_tipo, id_despacho_actual, id_cliente, rol_cliente, asunto,
				       cuantia, clase_cuantia, tipo_servicio, estado, etapa, fecha_radicacion, vigilado)
				VALUES (:radicado, :tipo, :despacho, :cliente, :rol, :asunto,
				        :cuantia, :clase, :servicio, :estado, :etapa, :fecha, :vigilado)
				RETURNING id_proceso
				""")
				.params(parametros(p))
				.query(Integer.class)
				.single();
		jdbc.sql("""
				INSERT INTO procesos.asignaciones (id_proceso, id_persona, rol, desde)
				VALUES (:id, :persona, 'RESPONSABLE', CURRENT_DATE)
				""")
				.param("id", id)
				.param("persona", p.idResponsable())
				.update();
		jdbc.sql("""
				INSERT INTO procesos.partes (id_proceso, rol, nombre, es_cliente)
				VALUES (:id, :rol, :nombre, true)
				""")
				.param("id", id)
				.param("rol", p.rolCliente().name())
				.param("nombre", nombreCliente)
				.update();
		return id;
	}

	@Override
	public void actualizar(Proceso p) {
		Map<String, Object> params = parametros(p);
		params.put("id", p.idProceso());
		jdbc.sql("""
				UPDATE procesos.procesos
				SET radicado = :radicado, id_tipo = :tipo, id_despacho_actual = :despacho, id_cliente = :cliente,
				    rol_cliente = :rol, asunto = :asunto, cuantia = :cuantia, clase_cuantia = :clase,
				    tipo_servicio = :servicio, estado = :estado, etapa = :etapa, fecha_radicacion = :fecha,
				    vigilado = :vigilado
				WHERE id_proceso = :id
				""")
				.params(params)
				.update();
		jdbc.sql("UPDATE procesos.partes SET rol = :rol WHERE id_proceso = :id AND es_cliente")
				.param("rol", p.rolCliente().name())
				.param("id", p.idProceso())
				.update();
		// Solo puede haber un responsable: el anterior pasa a apoyo antes de asignar el nuevo.
		jdbc.sql("""
				UPDATE procesos.asignaciones SET rol = 'APOYO'
				WHERE id_proceso = :id AND rol = 'RESPONSABLE' AND id_persona <> :persona
				""")
				.param("id", p.idProceso())
				.param("persona", p.idResponsable())
				.update();
		jdbc.sql("""
				INSERT INTO procesos.asignaciones (id_proceso, id_persona, rol, desde)
				VALUES (:id, :persona, 'RESPONSABLE', CURRENT_DATE)
				ON CONFLICT (id_proceso, id_persona) DO UPDATE SET rol = 'RESPONSABLE'
				""")
				.param("id", p.idProceso())
				.param("persona", p.idResponsable())
				.update();
	}

	@Override
	public int contarProvidencias(int idProceso) {
		return jdbc.sql("SELECT count(*) FROM procesos.providencias WHERE id_proceso = :id")
				.param("id", idProceso)
				.query(Integer.class)
				.single();
	}

	@Override
	public void eliminar(int idProceso) {
		jdbc.sql("DELETE FROM procesos.procesos WHERE id_proceso = :id").param("id", idProceso).update();
	}

	private static Map<String, Object> parametros(Proceso p) {
		Map<String, Object> m = new HashMap<>();
		m.put("radicado", p.radicado().numero());
		m.put("tipo", p.idTipo());
		m.put("despacho", p.idDespachoActual());
		m.put("cliente", p.idCliente());
		m.put("rol", p.rolCliente().name());
		m.put("asunto", p.asunto());
		m.put("cuantia", p.cuantia());
		m.put("clase", p.claseCuantia().name());
		m.put("servicio", p.tipoServicio().name());
		m.put("estado", p.estado().name());
		m.put("etapa", p.etapa().name());
		m.put("fecha", p.fechaRadicacion());
		m.put("vigilado", p.vigilado());
		return m;
	}

	private ProcesoVista aVista(ResultSet rs, int fila) throws SQLException {
		return new ProcesoVista(
				rs.getInt("id_proceso"),
				rs.getString("radicado"),
				rs.getString("radicado_legible"),
				rs.getShort("id_tipo"),
				rs.getString("tipo_proceso"),
				rs.getString("codigo_rama"),
				rs.getString("rama"),
				rs.getString("despacho_origen"),
				corto(rs, "id_despacho_actual"),
				rs.getString("despacho_actual"),
				rs.getInt("instancia"),
				rs.getInt("id_cliente"),
				rs.getString("rol_cliente"),
				rs.getString("asunto"),
				rs.getBigDecimal("cuantia"),
				rs.getString("clase_cuantia"),
				rs.getString("tipo_servicio"),
				rs.getString("estado"),
				rs.getString("etapa"),
				rs.getObject("fecha_radicacion", java.time.LocalDate.class),
				rs.getBoolean("vigilado"),
				rs.getObject("id_responsable") == null ? null : rs.getLong("id_responsable"));
	}

	static Short corto(ResultSet rs, String columna) throws SQLException {
		short valor = rs.getShort(columna);
		return rs.wasNull() ? null : valor;
	}

}
