package co.abogados.procesos.dominio.puertos;

import co.abogados.procesos.dominio.modelo.Cierre;
import co.abogados.procesos.dominio.modelo.EtapaProceso;

/** Cierre y reapertura de un proceso. */
public interface CierreRepositorio {

	/**
	 * Deja el proceso TERMINADO o ARCHIVADO en la etapa CERRADO, guarda motivo, fecha, nota y la etapa que tenía, y
	 * apaga su vigilancia. Devuelve lo que quedó sin efecto.
	 */
	Cierre.Efectos cerrar(int idProceso, Cierre cierre, long autor);

	/** Vuelve a dejarlo ACTIVO en la etapa indicada y borra los datos del cierre. No vuelve a encender la vigilancia. */
	void reabrir(int idProceso, EtapaProceso etapa);

}
