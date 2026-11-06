package co.abogados.gabinete.infraestructura.persistencia;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PersonalJpa extends JpaRepository<PersonalEntidad, Long> {

	@Query("""
			SELECT p FROM PersonalEntidad p
			WHERE p.cargo.esAbogado = true AND (:soloActivos = false OR p.activo = true)
			ORDER BY p.apellidos, p.nombres
			""")
	List<PersonalEntidad> abogados(@Param("soloActivos") boolean soloActivos);

	@Query("SELECT p FROM PersonalEntidad p WHERE p.idPersona = :id AND p.cargo.esAbogado = true")
	Optional<PersonalEntidad> abogado(@Param("id") long idPersona);

	Optional<PersonalEntidad> findByEmailIgnoreCase(String email);

	Optional<PersonalEntidad> findByTarjetaProfesional(String tarjeta);
}
