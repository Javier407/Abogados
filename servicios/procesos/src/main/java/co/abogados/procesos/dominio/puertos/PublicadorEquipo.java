package co.abogados.procesos.dominio.puertos;

import co.abogados.procesos.dominio.modelo.Relevo;

/** Publica proceso.reasignado (por el outbox, como PublicadorEventos). */
public interface PublicadorEquipo {

	void procesoReasignado(Relevo.Reasignado evento);

}
