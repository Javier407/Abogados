package co.abogados.identidad.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.UsuarioRepositorio;

@Repository
public class UsuarioRepositorioJdbc implements UsuarioRepositorio {

	private static final String SELECT = """
			SELECT u.id_usuario, u.usuario, u.nombre, u.email, u.password_hash, u.activo, u.intentos_fallidos,
			       u.bloqueado_hasta, coalesce(string_agg(r.codigo, ','), '') AS roles
			FROM identidad.usuarios u
			LEFT JOIN identidad.usuario_rol ur ON ur.id_usuario = u.id_usuario
			LEFT JOIN identidad.roles r ON r.id_rol = ur.id_rol
			""";
	private static final String AGRUPAR = """
			GROUP BY u.id_usuario, u.usuario, u.nombre, u.email, u.password_hash, u.activo, u.intentos_fallidos,
			         u.bloqueado_hasta
			""";

	private final JdbcClient jdbc;

	public UsuarioRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Usuario> porUsuarioOEmail(String texto) {
		return jdbc.sql(SELECT + "WHERE lower(u.usuario) = :t OR lower(u.email) = :t\n" + AGRUPAR)
				.param("t", texto.toLowerCase())
				.query(this::aUsuario)
				.optional();
	}

	@Override
	public Optional<Usuario> porId(long idUsuario) {
		return jdbc.sql(SELECT + "WHERE u.id_usuario = :id\n" + AGRUPAR).param("id", idUsuario).query(this::aUsuario)
				.optional();
	}

	@Override
	public void guardarFallo(long idUsuario, int intentos, Instant bloqueadoHasta) {
		jdbc.sql("UPDATE identidad.usuarios SET intentos_fallidos = :i, bloqueado_hasta = :b WHERE id_usuario = :id")
				.param("i", intentos)
				.param("b", bloqueadoHasta == null ? null : Timestamp.from(bloqueadoHasta))
				.param("id", idUsuario)
				.update();
	}

	@Override
	public void guardarIngreso(long idUsuario, Instant ahora) {
		jdbc.sql("""
				UPDATE identidad.usuarios SET intentos_fallidos = 0, bloqueado_hasta = NULL, ultimo_ingreso = :a
				WHERE id_usuario = :id
				""")
				.param("a", Timestamp.from(ahora))
				.param("id", idUsuario)
				.update();
	}

	@Override
	public void cambiarClave(long idUsuario, String hashClave, Instant ahora) {
		jdbc.sql("UPDATE identidad.usuarios SET password_hash = :h, clave_cambiada = :a WHERE id_usuario = :id")
				.param("h", hashClave)
				.param("a", Timestamp.from(ahora))
				.param("id", idUsuario)
				.update();
	}

	@Override
	public void registrarIntento(String usuario, Long idUsuario, boolean exitoso, String motivo, String ip) {
		jdbc.sql("""
				INSERT INTO identidad.ingresos (usuario, id_usuario, exitoso, motivo, ip)
				VALUES (:u, :id, :e, :m, :ip)
				""")
				.param("u", usuario)
				.param("id", idUsuario)
				.param("e", exitoso)
				.param("m", motivo)
				.param("ip", ip == null ? null : ip.length() > 45 ? ip.substring(0, 45) : ip)
				.update();
	}

	private Usuario aUsuario(ResultSet rs, int fila) throws SQLException {
		String roles = rs.getString("roles");
		Set<String> conjunto = roles.isEmpty() ? Set.of() : new HashSet<>(List.of(roles.split(",")));
		Timestamp bloqueado = rs.getTimestamp("bloqueado_hasta");
		return new Usuario(rs.getLong("id_usuario"), rs.getString("usuario"), rs.getString("nombre"),
				rs.getString("email"), rs.getString("password_hash"), rs.getBoolean("activo"), conjunto,
				rs.getInt("intentos_fallidos"), bloqueado == null ? null : bloqueado.toInstant());
	}

}
