package co.abogados.gabinete.infraestructura.persistencia;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

interface TarifaJpa extends JpaRepository<TarifaEntidad, Short> {

	List<TarifaEntidad> findByActivaTrue(Sort orden);
}
