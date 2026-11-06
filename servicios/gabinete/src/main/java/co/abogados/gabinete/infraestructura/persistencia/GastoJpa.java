package co.abogados.gabinete.infraestructura.persistencia;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GastoJpa extends JpaRepository<GastoEntidad, Integer> {

	@Query("""
			SELECT g FROM GastoEntidad g
			WHERE (:proceso IS NULL OR g.idProceso = :proceso) AND (:cliente IS NULL OR g.idCliente = :cliente)
			ORDER BY g.fecha DESC, g.idGasto DESC
			""")
	List<GastoEntidad> filtrar(@Param("proceso") Integer idProceso, @Param("cliente") Integer idCliente);

}
