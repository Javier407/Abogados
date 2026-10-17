package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.procesos.dominio.modelo.Actuacion;

public interface ActuacionRepositorio {

	/** De la más reciente a la más antigua. */
	List<Actuacion> deProceso(int idProceso);

	Optional<Actuacion> buscar(int idActuacion);

	int crear(Actuacion actuacion);

	void eliminar(int idActuacion);

}
