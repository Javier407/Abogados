package co.edu.unbosque.ciclo3back.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.ciclo3back.model.Abogados;

/**
 * Repositorio JPA de la entidad Abogados (PK id_abogado: Long).
 * Se usa @Query porque Spring Data interpreta el "_" de los nombres de
 * atributo como navegación, así que no se pueden derivar consultas por nombre.
 */
@Repository
public interface AbogadosDAO extends JpaRepository<Abogados, Long> {

	/** Cuenta los abogados (distintos a idExcluido) que ya usan esa tarjeta profesional. */
	@Query("SELECT COUNT(a) FROM Abogados a WHERE a.tarjeta_profesional = :tarjeta AND a.id_abogado <> :idExcluido")
	long contarPorTarjeta(@Param("tarjeta") String tarjeta, @Param("idExcluido") long idExcluido);

	/** Cuenta los abogados (distintos a idExcluido) vinculados a ese usuario. */
	@Query("SELECT COUNT(a) FROM Abogados a WHERE a.usuario.cedula_usuario = :cedula AND a.id_abogado <> :idExcluido")
	long contarPorUsuario(@Param("cedula") long cedula, @Param("idExcluido") long idExcluido);

}
