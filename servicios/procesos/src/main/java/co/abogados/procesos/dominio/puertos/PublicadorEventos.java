package co.abogados.procesos.dominio.puertos;

import co.abogados.procesos.dominio.modelo.AvisoTermino;
import co.abogados.procesos.dominio.modelo.ProcesoCreado;

/** Publica los eventos del servicio. Si la mensajería falla, no debe deshacer la operación. */
public interface PublicadorEventos {

	void procesoCreado(ProcesoCreado evento);

	void avisoTermino(AvisoTermino aviso);

}
