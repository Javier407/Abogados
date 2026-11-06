package co.abogados.procesos.dominio.puertos;

import java.util.List;

import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.EventoExpediente;
import co.abogados.procesos.dominio.modelo.Instancia;

/** Vistas del expediente que cruzan actuaciones, providencias y términos. */
public interface ExpedienteRepositorio {

	/** Actuaciones, providencias y términos del proceso, del más reciente al más antiguo. */
	List<EventoExpediente> lineaDeTiempo(int idProceso);

	/** Los demás procesos con el mismo radicado base (otras instancias), sin incluir el propio. */
	List<Instancia> otrasInstancias(int idProceso);

	/** Cambia la etapa sin tocar el resto del proceso. No lo hace si el proceso ya está CERRADO. */
	void avanzarEtapa(int idProceso, EtapaProceso etapa);

}
