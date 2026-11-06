package co.abogados.gabinete.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SmmlvJpa extends JpaRepository<SmmlvEntidad, Short> {

	Optional<SmmlvEntidad> findFirstByOrderByAnioDesc();
}
