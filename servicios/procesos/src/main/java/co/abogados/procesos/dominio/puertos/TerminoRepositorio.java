package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.procesos.dominio.modelo.FiltroTerminos;
import co.abogados.procesos.dominio.modelo.Termino;
import co.abogados.procesos.dominio.modelo.TerminoVista;

public interface TerminoRepositorio {

	List<TerminoVista> listar(FiltroTerminos filtro);

	Optional<TerminoVista> buscar(int idTermino);

	int crear(Termino termino);

	void marcarCumplido(int idTermino);

	void eliminar(int idTermino);

	/** La providencia existe y pertenece a ese proceso. */
	boolean providenciaDelProceso(int idProvidencia, int idProceso);

	/**
	 * Términos pendientes que vencen dentro de diasAviso días hábiles o que vencieron hace
	 * máximo diasRecordarVencidos días hábiles.
	 */
	List<TerminoVista> paraAvisar(int diasAviso, int diasRecordarVencidos);

}
