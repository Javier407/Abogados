package co.abogados.gabinete.infraestructura.persistencia;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

interface AreaJpa extends JpaRepository<AreaEntidad, Short> {

	List<AreaEntidad> findByActivaTrue(Sort orden);
}
