package co.abogados.gabinete.infraestructura.persistencia;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PeritoJpa extends JpaRepository<PeritoEntidad, Integer> {

	List<PeritoEntidad> findAllByOrderByNombreAsc();

	@Query("""
			SELECT p FROM PeritoEntidad p
			WHERE lower(p.nombre) LIKE lower(concat('%', :texto, '%'))
			   OR lower(p.especialidad) LIKE lower(concat('%', :texto, '%'))
			ORDER BY p.nombre
			""")
	List<PeritoEntidad> buscarTexto(@Param("texto") String texto);
}
