package co.abogados.procesos.dominio.puertos;

import java.util.List;

import co.abogados.procesos.dominio.modelo.Relevo;

/** Cambio del abogado responsable de un proceso y el historial de su equipo (procesos.asignaciones_historial). */
public interface RelevoRepositorio {

	/**
	 * Pone al nuevo responsable y saca al anterior (o lo deja de apoyo), todo en la misma transacción. El historial
	 * lo anota la base con el motivo, la nota y el autor.
	 *
	 * @param idAnterior responsable actual, o null si el proceso no tenía
	 */
	Relevo.Resultado relevar(int idProceso, Long idAnterior, Relevo.Solicitud solicitud, long autor);

	/** Todos los periodos del equipo: primero los vigentes, luego los terminados del más reciente al más antiguo. */
	List<Relevo.Periodo> historial(int idProceso);

}
