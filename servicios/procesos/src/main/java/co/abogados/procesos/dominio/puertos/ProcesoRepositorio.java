package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.procesos.dominio.modelo.FiltroProcesos;
import co.abogados.procesos.dominio.modelo.Proceso;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Radicado;

public interface ProcesoRepositorio {

	List<ProcesoVista> listar(FiltroProcesos filtro);

	Optional<ProcesoVista> buscar(int idProceso);

	/** Id del proceso que ya tiene ese radicado, si existe. */
	Optional<Integer> idPorRadicado(Radicado radicado);

	/**
	 * Guarda el proceso, su abogado responsable y al cliente como parte del proceso.
	 * Devuelve el id asignado.
	 */
	int crear(Proceso proceso, String nombreCliente);

	/** Actualiza los datos del proceso y, si cambió, el abogado responsable. */
	void actualizar(Proceso proceso);

	int contarProvidencias(int idProceso);

	void eliminar(int idProceso);

}
