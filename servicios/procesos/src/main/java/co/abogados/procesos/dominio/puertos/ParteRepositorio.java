package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.procesos.dominio.modelo.Parte;

public interface ParteRepositorio {

	/** Primero el cliente, luego las demás partes en el orden en que se agregaron. */
	List<Parte> deProceso(int idProceso);

	Optional<Parte> buscar(int idParte);

	int crear(Parte parte);

	void actualizar(Parte parte);

	void eliminar(int idParte);

}
