package co.abogados.gabinete.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

interface AreaJpa extends JpaRepository<AreaEntidad, Short> {
}
