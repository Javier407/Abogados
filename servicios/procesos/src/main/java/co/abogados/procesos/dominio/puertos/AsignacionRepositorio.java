package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.procesos.dominio.modelo.Asignacion;

/** Equipo de cada proceso (procesos.asignaciones). */
public interface AsignacionRepositorio {

	/** El responsable primero, luego apoyo y dependientes por fecha. */
	List<Asignacion> listar(int idProceso);

	Optional<Asignacion> buscar(int idProceso, long idPersona);

	void agregar(int idProceso, long idPersona, Asignacion.Rol rol);

	void quitar(int idProceso, long idPersona);

}
