package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.procesos.dominio.modelo.Prueba;

public interface PruebaRepositorio {

	List<Prueba> deProceso(int idProceso);

	Optional<Prueba> buscar(int idPrueba);

	int crear(Prueba prueba);

	void actualizar(Prueba prueba);

	void eliminar(int idPrueba);

}
