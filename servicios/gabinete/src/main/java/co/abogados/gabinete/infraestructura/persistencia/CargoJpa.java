package co.abogados.gabinete.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

interface CargoJpa extends JpaRepository<CargoEntidad, Short> {
}
