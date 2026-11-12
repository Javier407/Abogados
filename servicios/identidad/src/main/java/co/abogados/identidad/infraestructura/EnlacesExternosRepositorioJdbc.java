package co.abogados.identidad.infraestructura;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.identidad.dominio.AccesoExterno.Enlace;
import co.abogados.identidad.dominio.AccesoExterno.Estado;
import co.abogados.identidad.dominio.AccesoExterno.Identidad;
import co.abogados.identidad.dominio.AccesoExterno.Modo;
import co.abogados.identidad.dominio.AccesoExterno.Proveedor;
import co.abogados.identidad.dominio.puertos.EnlacesExternosRepositorio;

@Repository
public class EnlacesExternosRepositorioJdbc implements EnlacesExternosRepositorio {

	private final JdbcClient jdbc;

	public EnlacesExternosRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Long> duenoDe(Proveedor proveedor, String sujeto) {
		return jdbc.sql("SELECT id_usuario FROM identidad.cuentas_externas WHERE proveedor = :p AND sujeto = :s")
				.param("p", proveedor.name()).param("s", sujeto).query(Long.class).optional();
	}

	@Override
	public boolean tieneEnlace(long idUsuario, Proveedor proveedor) {
		return jdbc.sql("SELECT count(*) FROM identidad.cuentas_externas WHERE id_usuario = :id AND proveedor = :p")
				.param("id", idUsuario).param("p", proveedor.name()).query(Integer.class).single() > 0;
	}

	@Override
	public void enlazar(long idUsuario, Identidad i, Instant ahora) {
		jdbc.sql("""
				INSERT INTO identidad.cuentas_externas (proveedor, sujeto, id_usuario, correo, creada)
				VALUES (:p, :s, :id, :correo, :ahora)
				""").param("p", i.proveedor().name()).param("s", i.sujeto()).param("id", idUsuario)
				.param("correo", i.correo()).param("ahora", Timestamp.from(ahora)).update();
	}

	@Override
	public void registrarUso(Proveedor proveedor, String sujeto, Instant ahora) {
		jdbc.sql("UPDATE identidad.cuentas_externas SET ultimo_uso = :ahora WHERE proveedor = :p AND sujeto = :s")
				.param("ahora", Timestamp.from(ahora)).param("p", proveedor.name()).param("s", sujeto).update();
	}

	@Override
	public List<Enlace> enlaces(long idUsuario) {
		return jdbc.sql("""
				SELECT proveedor, correo, creada, ultimo_uso FROM identidad.cuentas_externas
				WHERE id_usuario = :id ORDER BY proveedor
				""").param("id", idUsuario)
				.query((rs, n) -> {
					Timestamp uso = rs.getTimestamp(4);
					return new Enlace(Proveedor.valueOf(rs.getString(1)), rs.getString(2), rs.getTimestamp(3).toInstant(),
							uso == null ? null : uso.toInstant());
				}).list();
	}

	@Override
	public boolean quitar(long idUsuario, Proveedor proveedor) {
		return jdbc.sql("DELETE FROM identidad.cuentas_externas WHERE id_usuario = :id AND proveedor = :p")
				.param("id", idUsuario).param("p", proveedor.name()).update() > 0;
	}

	@Override
	public void guardarEstado(Estado e) {
		jdbc.sql("""
				INSERT INTO identidad.oauth_estados (hash_estado, hash_comprobante, proveedor, modo, id_usuario, verificador, vence)
				VALUES (:he, :hc, :p, :m, :id, :v, :vence)
				""").param("he", e.hashEstado()).param("hc", e.hashComprobante()).param("p", e.proveedor().name())
				.param("m", e.modo().name()).param("id", e.idUsuario()).param("v", e.verificador())
				.param("vence", Timestamp.from(e.vence())).update();
	}

	@Override
	public Optional<Estado> tomarEstado(String hashEstado) {
		// DELETE ... RETURNING: leer y gastar en un solo paso (dos vueltas con el mismo estado no pueden ganar ambas)
		return jdbc.sql("""
				DELETE FROM identidad.oauth_estados WHERE hash_estado = :he
				RETURNING hash_estado, hash_comprobante, proveedor, modo, id_usuario, verificador, vence
				""").param("he", hashEstado)
				.query((rs, n) -> new Estado(rs.getString(1), rs.getString(2), Proveedor.valueOf(rs.getString(3)),
						Modo.valueOf(rs.getString(4)), rs.getObject(5, Long.class), rs.getString(6),
						rs.getTimestamp(7).toInstant()))
				.optional();
	}

	@Override
	public void limpiarVencidos(Instant ahora) {
		jdbc.sql("DELETE FROM identidad.oauth_estados WHERE vence < :ahora").param("ahora", Timestamp.from(ahora)).update();
	}

}
