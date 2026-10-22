package co.abogados.gabinete.infraestructura.persistencia;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Sede;
import co.abogados.gabinete.dominio.puertos.CatalogoEdicion;

/** Escritura de sedes, cargos y áreas con SQL (sus ids son smallint sin secuencia: se usa el máximo + 1). */
@Repository
public class CatalogoEdicionJdbc implements CatalogoEdicion {

	private final JdbcClient jdbc;

	public CatalogoEdicionJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Sede guardarSede(Short idSede, Sede s) {
		short id = idSede != null ? idSede : siguiente("gabinete.sedes", "id_sede");
		jdbc.sql("""
				INSERT INTO gabinete.sedes (id_sede, id_firma, nombre, ciudad, direccion, telefono, es_principal)
				VALUES (:id, (SELECT min(id_firma) FROM gabinete.firma), :nombre, :ciudad, :direccion, :telefono, :principal)
				ON CONFLICT (id_sede) DO UPDATE SET nombre = EXCLUDED.nombre, ciudad = EXCLUDED.ciudad,
				    direccion = EXCLUDED.direccion, telefono = EXCLUDED.telefono, es_principal = EXCLUDED.es_principal
				""").param("id", id).param("nombre", s.nombre()).param("ciudad", s.ciudad())
				.param("direccion", s.direccion()).param("telefono", s.telefono()).param("principal", s.esPrincipal())
				.update();
		return new Sede(id, s.nombre(), s.ciudad(), s.direccion(), s.telefono(), s.esPrincipal());
	}

	@Override
	public void desmarcarPrincipales() {
		jdbc.sql("UPDATE gabinete.sedes SET es_principal = false WHERE es_principal").update();
	}

	@Override
	public void eliminarSede(short idSede) {
		borrar("DELETE FROM gabinete.sedes WHERE id_sede = :id", idSede,
				"La sede tiene clientes o personal asignados: muévalos a otra sede antes de borrarla");
	}

	@Override
	public Cargo guardarCargo(Short idCargo, Cargo c) {
		short id = idCargo != null ? idCargo : siguiente("gabinete.cargos", "id_cargo");
		jdbc.sql("""
				INSERT INTO gabinete.cargos (id_cargo, codigo, nombre, es_abogado) VALUES (:id, :codigo, :nombre, :abogado)
				ON CONFLICT (id_cargo) DO UPDATE SET codigo = EXCLUDED.codigo, nombre = EXCLUDED.nombre,
				    es_abogado = EXCLUDED.es_abogado
				""").param("id", id).param("codigo", c.codigo()).param("nombre", c.nombre()).param("abogado", c.esAbogado())
				.update();
		return new Cargo(id, c.codigo(), c.nombre(), c.esAbogado());
	}

	@Override
	public void eliminarCargo(short idCargo) {
		borrar("DELETE FROM gabinete.cargos WHERE id_cargo = :id", idCargo,
				"Hay personal con ese cargo: cámbielo antes de borrarlo");
	}

	@Override
	public AreaPractica guardarArea(Short idArea, AreaPractica a) {
		short id = idArea != null ? idArea : siguiente("gabinete.areas_practica", "id_area");
		jdbc.sql("""
				INSERT INTO gabinete.areas_practica (id_area, codigo_rama, nombre, id_director)
				VALUES (:id, :rama, :nombre, :director)
				ON CONFLICT (id_area) DO UPDATE SET codigo_rama = EXCLUDED.codigo_rama, nombre = EXCLUDED.nombre,
				    id_director = EXCLUDED.id_director
				""").param("id", id).param("rama", a.codigoRama()).param("nombre", a.nombre())
				.param("director", a.idDirector()).update();
		return new AreaPractica(id, a.codigoRama(), a.nombre(), a.idDirector());
	}

	@Override
	public void eliminarArea(short idArea) {
		borrar("DELETE FROM gabinete.areas_practica WHERE id_area = :id", idArea,
				"Hay personal en esa área: muévalo a otra antes de borrarla");
	}

	@Override
	public boolean personalActivo(long idPersona) {
		return jdbc.sql("SELECT count(*) FROM gabinete.personal WHERE id_persona = :id AND activo")
				.param("id", idPersona).query(Integer.class).single() > 0;
	}

	private short siguiente(String tabla, String columna) {
		return jdbc.sql("SELECT coalesce(max(" + columna + "), 0) + 1 FROM " + tabla).query(Short.class).single();
	}

	private void borrar(String sql, short id, String enUso) {
		try {
			jdbc.sql(sql).param("id", id).update();
		} catch (DataIntegrityViolationException e) {
			throw new ConflictoException(enUso);
		}
	}

}
