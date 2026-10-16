package co.abogados.gabinete.dominio.puertos;

import java.util.Optional;

/** Pregunta al servicio procesos; si no responde lanza ServicioNoDisponibleException. */
public interface ConsultaProcesos {

	int procesosDelCliente(int idCliente);

	/** Cliente del proceso, o vacío si el proceso no existe. */
	Optional<Integer> clienteDelProceso(int idProceso);

}
