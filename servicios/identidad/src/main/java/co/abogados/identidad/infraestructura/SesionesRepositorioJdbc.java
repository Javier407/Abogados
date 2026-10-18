package co.abogados.identidad.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.identidad.dominio.Refresco;
import co.abogados.identidad.dominio.puertos.SesionesRepositorio;

@Repository
public class SesionesRepositorioJdbc implements SesionesRepositorio {

	/** Revoca las filas elegidas y copia a la lista negra los tokens de acceso que siguen vigentes. */
	private static final String REVOCAR = """
			WITH r AS (
			    UPDATE identidad.sesiones SET revocado_en = :ahora, motivo = :motivo
			    WHERE revocado_en IS NULL AND %s
			    RETURNING jti, jti_expira)
			INSERT INTO identidad.tokens_revocados (jti, expira)
			SELECT jti, jti_expira FROM r WHERE jti IS NOT NULL AND jti_expira > :ahora
			ON CONFLICT (jti) DO NOTHING
			""";

	private final JdbcClient jdbc;

	public SesionesRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void guardar(Refresco r, String hash, UUID jti, Instant jtiExpira, String ip) {
		jdbc.sql("""
				INSERT INTO identidad.sesiones
				    (id_refresco, familia, id_usuario, hash_refresco, expira, familia_expira, jti, jti_expira, ip)
				VALUES (:id, :familia, :usuario, :hash, :expira, :familiaExpira, :jti, :jtiExpira, :ip)
				""")
				.param("id", r.id())
				.param("familia", r.familia())
				.param("usuario", r.idUsuario())
				.param("hash", hash)
				.param("expira", Timestamp.from(r.expira()))
				.param("familiaExpira", Timestamp.from(r.familiaExpira()))
				.param("jti", jti)
				.param("jtiExpira", Timestamp.from(jtiExpira))
				.param("ip", ip == null ? null : ip.length() > 45 ? ip.substring(0, 45) : ip)
				.update();
	}

	@Override
	public Optional<Refresco> porHash(String hash) {
		return jdbc.sql("""
				SELECT id_refresco, familia, id_usuario, expira, familia_expira, usado_en, revocado_en
				FROM identidad.sesiones WHERE hash_refresco = :hash
				""").param("hash", hash).query(SesionesRepositorioJdbc::leer).optional();
	}

	@Override
	public boolean marcarUsado(UUID id, Instant ahora) {
		return jdbc.sql("""
				UPDATE identidad.sesiones SET usado_en = :ahora
				WHERE id_refresco = :id AND usado_en IS NULL AND revocado_en IS NULL
				""").param("ahora", Timestamp.from(ahora)).param("id", id).update() == 1;
	}

	@Override
	public int revocarFamilia(UUID familia, String motivo, Instant ahora) {
		return jdbc.sql(REVOCAR.formatted("familia = :familia"))
				.param("familia", familia).param("motivo", motivo).param("ahora", Timestamp.from(ahora)).update();
	}

	@Override
	public int revocarUsuario(long idUsuario, UUID salvoFamilia, String motivo, Instant ahora) {
		return jdbc.sql(REVOCAR.formatted("id_usuario = :usuario AND (CAST(:salvo AS uuid) IS NULL OR familia <> :salvo)"))
				.param("usuario", idUsuario).param("salvo", salvoFamilia).param("motivo", motivo)
				.param("ahora", Timestamp.from(ahora)).update();
	}

	@Override
	public List<String> revocados(Instant ahora) {
		return jdbc.sql("SELECT jti::text FROM identidad.tokens_revocados WHERE expira > :ahora")
				.param("ahora", Timestamp.from(ahora)).query(String.class).list();
	}

	@Override
	public void limpiar(Instant ahora) {
		Timestamp t = Timestamp.from(ahora);
		jdbc.sql("DELETE FROM identidad.tokens_revocados WHERE expira <= :ahora").param("ahora", t).update();
		// Se guardan 2 días más para poder ver en la base cuándo y por qué se cerró cada sesión.
		jdbc.sql("DELETE FROM identidad.sesiones WHERE familia_expira <= :ahora - interval '2 days' OR expira <= :ahora - interval '2 days'")
				.param("ahora", t).update();
	}

	private static Refresco leer(ResultSet rs, int fila) throws SQLException {
		return new Refresco(rs.getObject("id_refresco", UUID.class), rs.getObject("familia", UUID.class),
				rs.getLong("id_usuario"), rs.getTimestamp("expira").toInstant(),
				rs.getTimestamp("familia_expira").toInstant(), instante(rs.getTimestamp("usado_en")),
				instante(rs.getTimestamp("revocado_en")));
	}

	private static Instant instante(Timestamp t) {
		return t == null ? null : t.toInstant();
	}

}
