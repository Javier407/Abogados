package co.abogados.procesos.dominio.puertos;

import co.abogados.procesos.dominio.modelo.Portal.ResumenAvisado;

/** Publica portal.resumen_publicado (por el outbox: sale aunque RabbitMQ esté caído en ese momento). */
public interface PublicadorPortal {

	void resumenPublicado(ResumenAvisado evento);

}
