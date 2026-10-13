package co.edu.unbosque.ciclo3back.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.ciclo3back.model.Asuntos;
import co.edu.unbosque.ciclo3back.model.EstadoAsunto;

/**
 * Repositorio JPA de la entidad Asuntos (PK numero_expediente: String).
 */
@Repository
public interface AsuntosDAO extends JpaRepository<Asuntos, String> {

	@Query("SELECT a FROM Asuntos a WHERE a.cliente.id_cliente = :idCliente ORDER BY a.fecha_inicio DESC")
	List<Asuntos> buscarPorCliente(@Param("idCliente") long idCliente);

	@Query("SELECT DISTINCT a FROM Asuntos a JOIN a.abogados ab WHERE ab.id_abogado = :idAbogado ORDER BY a.fecha_inicio DESC")
	List<Asuntos> buscarPorAbogado(@Param("idAbogado") long idAbogado);

	@Query("SELECT a FROM Asuntos a WHERE a.estado = :estado ORDER BY a.fecha_inicio DESC")
	List<Asuntos> buscarPorEstado(@Param("estado") EstadoAsunto estado);

	@Query("SELECT COUNT(a) FROM Asuntos a WHERE a.cliente.id_cliente = :idCliente")
	long contarPorCliente(@Param("idCliente") long idCliente);

	@Query("SELECT COUNT(a) FROM Asuntos a JOIN a.abogados ab WHERE ab.id_abogado = :idAbogado")
	long contarPorAbogado(@Param("idAbogado") long idAbogado);

}
