package co.abogados.gabinete.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

interface TarifaJpa extends JpaRepository<TarifaEntidad, Short> {
}
