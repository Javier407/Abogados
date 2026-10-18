package co.abogados.identidad.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.identidad.dominio.Cuenta;
import co.abogados.identidad.dominio.puertos.CuentasRepositorio;

@Repository
public class CuentasRepositorioJdbc implements CuentasRepositorio {

	private static final String SELECT = """
			SELECT u.id_usuario, u.usuario, u.nombre, u.email, u.activo, u.bloqueado_hasta, u.ultimo_ingreso,
			       u.debe_cambiar_clave, u.id_cliente, coalesce(string_agg(r.codigo, ',' ORDER BY r.id_rol), '') AS roles
			FROM identidad.usuarios u
			LEFT JOIN identidad.usuario_rol ur ON ur.id_usuario = u.id_usuario
			LEFT JOIN identidad.roles r ON r.id_rol = ur.id_rol
			""";
	private static final String AGRUPAR = """
			GROUP BY u.id_usuario, u.usuario, u.nombre, u.email, u.activo, u.bloqueado_hasta, u.ultimo_ingreso,
			         u.debe_cambiar_clave, u.id_cliente
			""";

	private final JdbcClient jdbc;

	public CuentasRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Cuenta> listar() {
		return jdbc.sql(SELECT + AGRUPAR + "ORDER BY u.nombre").query(CuentasRepositorioJdbc::leer).list();
	}

	@Override
	public Optional<Cuenta> buscar(long idUsuario) {
		return jdbc.sql(SELECT + "WHERE u.id_usuario = :id\n" + AGRUPAR).param("id", idUsuario)
				.query(CuentasRepositorioJdbc::leer).optional();
	}

	@Override
	public List<Cuenta> deCliente(int idCliente) {
		return jdbc.sql(SELECT + "WHERE u.id_cliente = :c AND u.activo\n" + AGRUPAR).param("c", idCliente)
				.query(CuentasRepositorioJdbc::leer).list();
	}

	@Override
	public boolean existe(long idUsuario, String usuario, String email, Long excluir) {
		return jdbc.sql("""
				SELECT count(*) FROM identidad.usuarios
				WHERE (id_usuario = :id OR lower(usuario) = :usuario OR lower(email) = :email)
				  AND (CAST(:excluir AS bigint) IS NULL OR id_usuario <> :excluir)
				""")
				.param("id", idUsuario)
				.param("usuario", usuario)
				.param("email", email)
				.param("excluir", excluir)
				.query(Integer.class).single() > 0;
	}

	@Override
	public void crear(long idUsuario, String usuario, String nombre, String email, String hashClave, Set<String> roles,
			Integer idCliente) {
		jdbc.sql("""
				INSERT INTO identidad.usuarios (id_usuario, usuario, nombre, email, password_hash, debe_cambiar_clave, id_cliente)
				VALUES (:id, :usuario, :nombre, :email, :hash, true, :cliente)
				""")
				.param("cliente", idCliente)
				.param("id", idUsuario)
				.param("usuario", usuario)
				.param("nombre", nombre)
				.param("email", email)
				.param("hash", hashClave)
				.update();
		asignarRoles(idUsuario, roles);
	}

	@Override
	public void actualizar(long idUsuario, String usuario, String nombre, String email, boolean activo, Set<String> roles,
			Integer idCliente) {
		jdbc.sql("""
				UPDATE identidad.usuarios
				SET usuario = :usuario, nombre = :nombre, email = :email, activo = :activo, id_cliente = :cliente
				WHERE id_usuario = :id
				""")
				.param("cliente", idCliente)
				.param("id", idUsuario)
				.param("usuario", usuario)
				.param("nombre", nombre)
				.param("email", email)
				.param("activo", activo)
				.update();
		jdbc.sql("DELETE FROM identidad.usuario_rol WHERE id_usuario = :id").param("id", idUsuario).update();
		asignarRoles(idUsuario, roles);
	}

	@Override
	public void restablecerClave(long idUsuario, String hashClave) {
		jdbc.sql("""
				UPDATE identidad.usuarios
				SET password_hash = :hash, debe_cambiar_clave = true, intentos_fallidos = 0, bloqueado_hasta = NULL
				WHERE id_usuario = :id
				""")
				.param("hash", hashClave)
				.param("id", idUsuario)
				.update();
	}

	@Override
	public void desbloquear(long idUsuario) {
		jdbc.sql("UPDATE identidad.usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id_usuario = :id")
				.param("id", idUsuario)
				.update();
	}

	@Override
	public int administradoresActivos() {
		return jdbc.sql("""
				SELECT count(DISTINCT u.id_usuario) FROM identidad.usuarios u
				JOIN identidad.usuario_rol ur ON ur.id_usuario = u.id_usuario
				JOIN identidad.roles r ON r.id_rol = ur.id_rol
				WHERE u.activo AND r.codigo = 'ADMIN'
				""").query(Integer.class).single();
	}

	private void asignarRoles(long idUsuario, Set<String> roles) {
		for (String rol : roles) {
			jdbc.sql("""
					INSERT INTO identidad.usuario_rol (id_usuario, id_rol)
					SELECT :id, id_rol FROM identidad.roles WHERE codigo = :rol
					""")
					.param("id", idUsuario)
					.param("rol", rol)
					.update();
		}
	}

	private static Cuenta leer(ResultSet rs, int fila) throws SQLException {
		String roles = rs.getString("roles");
		Timestamp bloqueado = rs.getTimestamp("bloqueado_hasta");
		Timestamp ingreso = rs.getTimestamp("ultimo_ingreso");
		return new Cuenta(rs.getLong("id_usuario"), rs.getString("usuario"), rs.getString("nombre"), rs.getString("email"),
				rs.getBoolean("activo"), roles.isEmpty() ? Set.of() : new HashSet<>(List.of(roles.split(","))),
				bloqueado == null ? null : bloqueado.toInstant(), ingreso == null ? null : ingreso.toInstant(),
				rs.getBoolean("debe_cambiar_clave"), (Integer) rs.getObject("id_cliente"));
	}

}
