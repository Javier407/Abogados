package co.abogados.gabinete.dominio.puertos;

/** Pregunta al servicio procesos; si no responde lanza ServicioNoDisponibleException. */
public interface ConsultaProcesos {

	int procesosDelCliente(int idCliente);

}
