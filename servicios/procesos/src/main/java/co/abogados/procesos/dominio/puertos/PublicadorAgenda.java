package co.abogados.procesos.dominio.puertos;

import co.abogados.procesos.dominio.modelo.AvisoAudiencia;

/** Publica audiencia.proxima (por el outbox, como PublicadorEventos). */
public interface PublicadorAgenda {

	void avisoAudiencia(AvisoAudiencia aviso);

}
