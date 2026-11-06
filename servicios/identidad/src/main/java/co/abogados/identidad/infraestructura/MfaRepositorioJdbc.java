package co.abogados.identidad.infraestructura;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.identidad.dominio.EstadoMfa;
import co.abogados.identidad.dominio.puertos.MfaRepositorio;

@Repository
public class MfaRepositorioJdbc implements MfaRepositorio {

	private final JdbcClient jdbc;

	public MfaRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<EstadoMfa> estado(long idUsuario) {
		return jdbc.sql("""
				SELECT m.secreto, m.activo, m.desde, m.ultimo_paso,
				       (SELECT count(*) FROM identidad.mfa_codigos c WHERE c.id_usuario = m.id_usuario AND c.usado_en IS NULL)
				FROM identidad.mfa m WHERE m.id_usuario = :id
				""")
				.param("id", idUsuario)
				.query((rs, i) -> {
					Timestamp desde = rs.getTimestamp(3);
					return new EstadoMfa(idUsuario, rs.getString(1), rs.getBoolean(2),
							desde == null ? null : desde.toInstant(), rs.getObject(4, Long.class), rs.getInt(5));
				})
				.optional();
	}

	@Override
	public void guardarPendiente(long idUsuario, String secretoCifrado) {
		jdbc.sql("""
				INSERT INTO identidad.mfa (id_usuario, secreto, activo) VALUES (:id, :secreto, false)
				ON CONFLICT (id_usuario) DO UPDATE SET secreto = EXCLUDED.secreto, desde = NULL, ultimo_paso = NULL
				WHERE identidad.mfa.activo = false
				""").param("id", idUsuario).param("secreto", secretoCifrado).update();
	}

	@Override
	public void activar(long idUsuario, Instant desde, long paso) {
		jdbc.sql("UPDATE identidad.mfa SET activo = true, desde = :desde, ultimo_paso = :paso WHERE id_usuario = :id")
				.param("desde", Timestamp.from(desde)).param("paso", paso).param("id", idUsuario).update();
	}

	@Override
	public void desactivar(long idUsuario) {
		jdbc.sql("DELETE FROM identidad.mfa_codigos WHERE id_usuario = :id").param("id", idUsuario).update();
		jdbc.sql("DELETE FROM identidad.mfa WHERE id_usuario = :id").param("id", idUsuario).update();
	}

	@Override
	public boolean usarPaso(long idUsuario, long paso) {
		// Atómico: si dos peticiones traen el mismo código a la vez, solo una lo gasta.
		return jdbc.sql("""
				UPDATE identidad.mfa SET ultimo_paso = :paso
				WHERE id_usuario = :id AND activo AND (ultimo_paso IS NULL OR ultimo_paso < :paso)
				""").param("paso", paso).param("id", idUsuario).update() == 1;
	}

	@Override
	public void guardarCodigos(long idUsuario, List<String> hashes) {
		jdbc.sql("DELETE FROM identidad.mfa_codigos WHERE id_usuario = :id").param("id", idUsuario).update();
		for (String h : hashes) {
			jdbc.sql("INSERT INTO identidad.mfa_codigos (id_usuario, hash) VALUES (:id, :hash)")
					.param("id", idUsuario).param("hash", h).update();
		}
	}

	@Override
	public boolean usarCodigo(long idUsuario, String hash, Instant ahora) {
		return jdbc.sql("""
				UPDATE identidad.mfa_codigos SET usado_en = :ahora
				WHERE id_usuario = :id AND hash = :hash AND usado_en IS NULL
				""").param("ahora", Timestamp.from(ahora)).param("id", idUsuario).param("hash", hash).update() == 1;
	}

	@Override
	public Set<Long> conMfa() {
		return new HashSet<>(jdbc.sql("SELECT id_usuario FROM identidad.mfa WHERE activo").query(Long.class).list());
	}

}
