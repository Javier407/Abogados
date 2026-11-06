package co.abogados.gabinete.infraestructura.persistencia;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface HonorarioJpa extends JpaRepository<HonorarioEntidad, Integer> {

	@Query("""
			SELECT h FROM HonorarioEntidad h
			WHERE (:cliente IS NULL OR h.cliente.idCliente = :cliente)
			  AND (:proceso IS NULL OR h.idProceso = :proceso)
			  AND (:estado IS NULL OR h.estado = :estado)
			ORDER BY h.fechaPacto DESC, h.idHonorario DESC
			""")
	List<HonorarioEntidad> filtrar(@Param("cliente") Integer idCliente, @Param("proceso") Integer idProceso,
			@Param("estado") String estado);

	boolean existsByIdProceso(Integer idProceso);
}
