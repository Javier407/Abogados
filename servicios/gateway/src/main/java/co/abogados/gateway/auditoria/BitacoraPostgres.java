package co.abogados.gateway.auditoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

/**
 * Bitácora en PostgreSQL (esquema auditoria, migración 015). El gateway solo agrega y lee: el número, el hash
 * anterior y el hash los pone la base al guardar (disparador auditoria.encadenar), y la base rechaza cambios y
 * borrados. La revisión de la cadena se hace aquí, recalculando cada hash en Java, sin usar funciones de la base.
 */
public final class BitacoraPostgres implements Bitacora, AutoCloseable {

	private static final String COLUMNAS =
			"id, momento, usuario, roles, metodo, ruta, estado, duracion_ms, ip, hash_anterior, hash";

	private final DataSource datos;
	private final EscritorEnLotes escritor;

	public BitacoraPostgres(DataSource datos) {
		this(datos, 20_000, 500, Duration.ofSeconds(1), Duration.ofSeconds(30));
	}

	BitacoraPostgres(DataSource datos, int capacidad, int tamanoLote, Duration esperaMinima, Duration esperaMaxima) {
		this.datos = datos;
		this.escritor = new EscritorEnLotes(this::guardar, capacidad, tamanoLote, esperaMinima, esperaMaxima);
	}

	@Override
	public void anotar(Acceso acceso) {
		escritor.anotar(acceso);
	}

	void guardar(List<Acceso> lote) throws SQLException {
		try (Connection c = datos.getConnection()) {
			c.setAutoCommit(false);
			try (PreparedStatement ps = c.prepareStatement("INSERT INTO auditoria.registros "
					+ "(momento, usuario, roles, metodo, ruta, estado, duracion_ms, ip) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
				for (Acceso a : lote) {
					ps.setObject(1, utc(a.momento()));
					ps.setString(2, a.usuario());
					ps.setString(3, a.roles());
					ps.setString(4, a.metodo());
					ps.setString(5, a.ruta());
					if (a.estado() == null) {
						ps.setNull(6, Types.SMALLINT);
					} else {
						ps.setShort(6, a.estado().shortValue());
					}
					ps.setInt(7, a.duracionMs());
					ps.setString(8, a.ip());
					ps.addBatch();
				}
				ps.executeBatch();
				c.commit();
			} catch (SQLException e) {
				c.rollback();
				throw e;
			}
		}
	}

	@Override
	public List<Registro> buscar(Filtro f) {
		StringBuilder sql = new StringBuilder("SELECT " + COLUMNAS + " FROM auditoria.registros WHERE true");
		List<Object> valores = new ArrayList<>();
		if (f.usuario() != null) {
			sql.append(" AND usuario = ?");
			valores.add(f.usuario());
		}
		if (f.metodo() != null) {
			sql.append(" AND metodo = ?");
			valores.add(f.metodo().toUpperCase());
		}
		if (f.ruta() != null) {
			sql.append(" AND strpos(lower(ruta), lower(?)) > 0");
			valores.add(f.ruta());
		}
		if (f.soloFallos()) {
			sql.append(" AND (estado IS NULL OR estado >= 400)");
		}
		if (f.desde() != null) {
			sql.append(" AND momento >= ?");
			valores.add(utc(f.desde()));
		}
		if (f.hasta() != null) {
			sql.append(" AND momento < ?");
			valores.add(utc(f.hasta()));
		}
		if (f.antesDe() != null) {
			sql.append(" AND id < ?");
			valores.add(f.antesDe());
		}
		sql.append(" ORDER BY id DESC LIMIT ?");
		valores.add(f.limite());
		try (Connection c = datos.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
			for (int i = 0; i < valores.size(); i++) {
				ps.setObject(i + 1, valores.get(i));
			}
			List<Registro> salida = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					salida.add(leer(rs));
				}
			}
			return salida;
		} catch (SQLException e) {
			throw new IllegalStateException("No se pudo leer la bitácora de auditoría", e);
		}
	}

	@Override
	public Optional<Registro> registro(long id) {
		try (Connection c = datos.getConnection();
				PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNAS + " FROM auditoria.registros WHERE id = ?")) {
			ps.setLong(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
			}
		} catch (SQLException e) {
			throw new IllegalStateException("No se pudo leer la bitácora de auditoría", e);
		}
	}

	/** Lee en orden con un cursor (de a 2.000), sin cargar toda la tabla en memoria. */
	@Override
	public Cadena.Verificacion verificar(long selloId, String selloHash) {
		Cadena.Verificador v = selloId > 0 ? new Cadena.Verificador(selloId, selloHash) : new Cadena.Verificador();
		try (Connection c = datos.getConnection()) {
			c.setAutoCommit(false);
			c.setReadOnly(true);
			try (PreparedStatement ps = c.prepareStatement(
					"SELECT " + COLUMNAS + " FROM auditoria.registros WHERE id >= ? ORDER BY id")) {
				ps.setFetchSize(2_000);
				ps.setLong(1, v.leerDesde());
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next() && v.revisar(leer(rs))) {
						// sigue mientras cuadre
					}
				}
			} finally {
				c.rollback();
			}
		} catch (SQLException e) {
			throw new IllegalStateException("No se pudo leer la bitácora de auditoría", e);
		}
		return v.resultado();
	}

	@Override
	public Estado estado() {
		return new Estado("postgres", escritor.pendientes(), escritor.perdidos());
	}

	private static Registro leer(ResultSet rs) throws SQLException {
		short estado = rs.getShort("estado");
		Integer codigo = rs.wasNull() ? null : (int) estado;
		Acceso a = new Acceso(rs.getObject("momento", OffsetDateTime.class).toInstant(), rs.getString("usuario"),
				rs.getString("roles"), rs.getString("metodo"), rs.getString("ruta"), codigo, rs.getInt("duracion_ms"),
				rs.getString("ip"));
		return new Registro(rs.getLong("id"), a, rs.getString("hash_anterior"), rs.getString("hash"));
	}

	private static OffsetDateTime utc(Instant i) {
		return i.atOffset(ZoneOffset.UTC);
	}

	@Override
	public void close() {
		escritor.close();
		if (datos instanceof AutoCloseable cerrable) {
			try {
				cerrable.close();
			} catch (Exception e) {
				// apagando: nada más que hacer
			}
		}
	}

}
