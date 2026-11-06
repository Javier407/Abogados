package co.abogados.gabinete.infraestructura.persistencia;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.gabinete.dominio.modelo.Consulta;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.puertos.ConsultaRepositorio;

@Repository
public class ConsultaRepositorioJdbc implements ConsultaRepositorio {

	private static final String COLUMNAS = """
			id_consulta, fecha, nombre, tipo_documento, numero_documento, email, telefono, contraparte,
			contraparte_documento, codigo_rama, asunto, descripcion, id_abogado, estado, motivo_cierre, id_cliente,
			registrada_por
			""";

	private final JdbcClient jdbc;

	public ConsultaRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Consulta guardar(Consulta c) {
		return jdbc.sql("""
				INSERT INTO gabinete.consultas (fecha, nombre, tipo_documento, numero_documento, email, telefono,
				    contraparte, contraparte_documento, codigo_rama, asunto, descripcion, id_abogado, estado,
				    registrada_por)
				VALUES (:fecha, :nombre, :tipo, :numero, :email, :telefono, :contraparte, :contraparteDoc, :rama,
				    :asunto, :descripcion, :abogado, :estado, :por)
				RETURNING\s""" + COLUMNAS)
				.param("fecha", Date.valueOf(c.fecha()))
				.param("nombre", c.nombre())
				.param("tipo", c.tipoDocumento() == null ? null : c.tipoDocumento().name())
				.param("numero", c.numeroDocumento())
				.param("email", c.email())
				.param("telefono", c.telefono())
				.param("contraparte", c.contraparte())
				.param("contraparteDoc", c.contraparteDocumento())
				.param("rama", c.codigoRama())
				.param("asunto", c.asunto())
				.param("descripcion", c.descripcion())
				.param("abogado", c.idAbogado())
				.param("estado", c.estado().name())
				.param("por", c.registradaPor())
				.query(ConsultaRepositorioJdbc::leer).single();
	}

	@Override
	public List<Consulta> listar(Consulta.Estado estado) {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM gabinete.consultas"
				+ (estado == null ? "" : " WHERE estado = :estado") + " ORDER BY fecha DESC, id_consulta DESC")
				.params(estado == null ? java.util.Map.of() : java.util.Map.of("estado", estado.name()))
				.query(ConsultaRepositorioJdbc::leer).list();
	}

	@Override
	public Optional<Consulta> buscar(int idConsulta) {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM gabinete.consultas WHERE id_consulta = :id")
				.param("id", idConsulta).query(ConsultaRepositorioJdbc::leer).optional();
	}

	@Override
	public void cerrar(int idConsulta, Consulta.Estado estado, String motivo, Integer idCliente) {
		jdbc.sql("""
				UPDATE gabinete.consultas SET estado = :estado, motivo_cierre = :motivo, id_cliente = :cliente
				WHERE id_consulta = :id
				""").param("estado", estado.name()).param("motivo", motivo).param("cliente", idCliente)
				.param("id", idConsulta).update();
	}

	private static Consulta leer(ResultSet rs, int fila) throws SQLException {
		String tipo = rs.getString("tipo_documento");
		return new Consulta(rs.getInt("id_consulta"), rs.getDate("fecha").toLocalDate(), rs.getString("nombre"),
				tipo == null ? null : TipoDocumento.valueOf(tipo), rs.getString("numero_documento"), rs.getString("email"),
				rs.getString("telefono"), rs.getString("contraparte"), rs.getString("contraparte_documento"),
				rs.getString("codigo_rama"), rs.getString("asunto"), rs.getString("descripcion"),
				(Long) rs.getObject("id_abogado"), Consulta.Estado.valueOf(rs.getString("estado")),
				rs.getString("motivo_cierre"), (Integer) rs.getObject("id_cliente"), (Long) rs.getObject("registrada_por"));
	}

}
