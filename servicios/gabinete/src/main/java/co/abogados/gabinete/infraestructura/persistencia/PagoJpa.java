package co.abogados.gabinete.infraestructura.persistencia;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PagoJpa extends JpaRepository<PagoEntidad, Integer> {

	List<PagoEntidad> findByIdHonorarioOrderByFechaDescIdPagoDesc(Integer idHonorario);

	@Query("SELECT coalesce(sum(p.valor), 0) FROM PagoEntidad p WHERE p.idHonorario = :id")
	BigDecimal totalPagado(@Param("id") Integer idHonorario);

	/** Por cada acuerdo: [idHonorario, total pagado, cantidad de pagos]. */
	@Query("SELECT p.idHonorario, sum(p.valor), count(p) FROM PagoEntidad p WHERE p.idHonorario IN :ids GROUP BY p.idHonorario")
	List<Object[]> resumen(@Param("ids") List<Integer> idsHonorario);
}
