package co.abogados.gabinete.infraestructura.persistencia;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;

interface ClienteJpa extends JpaRepository<ClienteEntidad, Integer> {

	List<ClienteEntidad> findAllByOrderByNombreAsc();

	@Query("""
			SELECT c FROM ClienteEntidad c
			WHERE lower(c.nombre) LIKE lower(concat('%', :texto, '%')) OR c.numeroDocumento LIKE concat('%', :texto, '%')
			ORDER BY c.nombre
			""")
	List<ClienteEntidad> buscarTexto(@Param("texto") String texto);

	Optional<ClienteEntidad> findByTipoDocumentoAndNumeroDocumento(TipoDocumento tipo, String numero);

	@Query(value = "SELECT count(*) FROM gabinete.honorarios_pactados WHERE id_cliente = :id", nativeQuery = true)
	int contarHonorarios(@Param("id") int idCliente);
}
