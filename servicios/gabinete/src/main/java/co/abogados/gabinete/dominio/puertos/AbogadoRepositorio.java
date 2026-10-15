package co.abogados.gabinete.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.gabinete.dominio.modelo.Abogado;

public interface AbogadoRepositorio {

	List<Abogado> listar(boolean soloActivos);

	Optional<Abogado> buscar(long idPersona);

	/** Hay alguien del personal (abogado o no) con esa cédula. */
	boolean existePersona(long idPersona);

	Optional<Long> idPorEmail(String email);

	Optional<Long> idPorTarjeta(String tarjetaProfesional);

	Abogado guardar(Abogado abogado);

}
