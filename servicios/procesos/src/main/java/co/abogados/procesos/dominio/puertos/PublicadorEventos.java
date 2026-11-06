package co.abogados.procesos.dominio.puertos;

import co.abogados.procesos.dominio.modelo.AvisoTermino;
import co.abogados.procesos.dominio.modelo.ProcesoCreado;

/** Publica los eventos del servicio (por el outbox, en la misma transacción que la operación). */
public interface PublicadorEventos {

	void procesoCreado(ProcesoCreado evento);

	void avisoTermino(AvisoTermino aviso);

}
