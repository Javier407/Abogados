package co.abogados.gabinete.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

interface SedeJpa extends JpaRepository<SedeEntidad, Short> {
}
