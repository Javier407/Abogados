package co.abogados.procesos.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.Documento;
import co.abogados.procesos.dominio.puertos.DocumentoRepositorio;

@Repository
public class DocumentoRepositorioJdbc implements DocumentoRepositorio {

	private static final String COLUMNAS = """
			id_documento, id_proceso, archivo, nombre, tipo_mime, tamano, sha256, categoria, descripcion,
			visible_cliente, subido_por, subido
			""";

	private final JdbcClient jdbc;

	public DocumentoRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Documento guardar(Documento d) {
		return jdbc.sql("""
				INSERT INTO procesos.documentos (id_proceso, archivo, nombre, tipo_mime, tamano, sha256, categoria,
				                                 descripcion, visible_cliente, subido_por, subido)
				VALUES (:proceso, :archivo, :nombre, :mime, :tamano, :sha, :categoria, :descripcion, :visible, :por, :subido)
				RETURNING\s""" + COLUMNAS)
				.param("proceso", d.idProceso())
				.param("archivo", d.archivo())
				.param("nombre", d.nombre())
				.param("mime", d.tipoMime())
				.param("tamano", d.tamano())
				.param("sha", d.sha256())
				.param("categoria", d.categoria().name())
				.param("descripcion", d.descripcion())
				.param("visible", d.visibleCliente())
				.param("por", d.subidoPor())
				.param("subido", Timestamp.from(d.subido()))
				.query(DocumentoRepositorioJdbc::leer).single();
	}

	@Override
	public List<Documento> listar(int idProceso, boolean soloVisibles) {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM procesos.documentos WHERE id_proceso = :p"
				+ (soloVisibles ? " AND visible_cliente" : "") + " ORDER BY subido DESC, id_documento DESC")
				.param("p", idProceso).query(DocumentoRepositorioJdbc::leer).list();
	}

	@Override
	public Optional<Documento> buscar(int idProceso, int idDocumento) {
		return jdbc.sql("SELECT " + COLUMNAS + " FROM procesos.documentos WHERE id_proceso = :p AND id_documento = :d")
				.param("p", idProceso).param("d", idDocumento).query(DocumentoRepositorioJdbc::leer).optional();
	}

	@Override
	public void actualizar(int idDocumento, Documento.Categoria categoria, String descripcion, boolean visibleCliente) {
		jdbc.sql("""
				UPDATE procesos.documentos SET categoria = :c, descripcion = :d, visible_cliente = :v
				WHERE id_documento = :id
				""").param("c", categoria.name()).param("d", descripcion).param("v", visibleCliente)
				.param("id", idDocumento).update();
	}

	@Override
	public void eliminar(int idDocumento) {
		jdbc.sql("DELETE FROM procesos.documentos WHERE id_documento = :id").param("id", idDocumento).update();
	}

	private static Documento leer(ResultSet rs, int fila) throws SQLException {
		return new Documento(rs.getInt("id_documento"), rs.getInt("id_proceso"), rs.getObject("archivo", UUID.class),
				rs.getString("nombre"), rs.getString("tipo_mime"), rs.getLong("tamano"), rs.getString("sha256"),
				Documento.Categoria.valueOf(rs.getString("categoria")), rs.getString("descripcion"),
				rs.getBoolean("visible_cliente"), rs.getLong("subido_por"), rs.getTimestamp("subido").toInstant());
	}

}
