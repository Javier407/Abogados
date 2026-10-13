package co.edu.unbosque.ciclo3back.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.ciclo3back.model.Usuarios;

/**
 * Repositorio JPA de la entidad Usuarios.
 * El tipo de la llave primaria es Long, porque en la entidad
 * cedula_usuario está declarada como long.
 */
@Repository
public interface UsuariosDAO extends JpaRepository<Usuarios, Long> {

}
