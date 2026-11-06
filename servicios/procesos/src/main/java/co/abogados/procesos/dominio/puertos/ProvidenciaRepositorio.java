package co.abogados.procesos.dominio.puertos;

import java.util.List;

import co.abogados.procesos.dominio.modelo.ActuacionDetectada;
import co.abogados.procesos.dominio.modelo.NuevaProvidencia;
import co.abogados.procesos.dominio.modelo.ProvidenciaVista;

public interface ProvidenciaRepositorio {

	List<ProvidenciaVista> deProceso(int idProceso);

	boolean existeReferencia(String referenciaExterna);

	/** Registra la actuación como providencia con fuente VIGILANCIA. Devuelve el id. */
	int registrarDetectada(int idProceso, ActuacionDetectada actuacion);

	/** Registra una providencia escrita por la firma (fuente MANUAL). Devuelve el id. */
	int registrarManual(int idProceso, NuevaProvidencia providencia, Long registradoPor);

}
