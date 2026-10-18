package co.abogados.procesos.dominio.puertos;

import co.abogados.procesos.dominio.modelo.AvisoAudiencia;

/** Publica audiencia.proxima. Si la mensajería falla, se registra y se sigue (como PublicadorEventos). */
public interface PublicadorAgenda {

	void avisoAudiencia(AvisoAudiencia aviso);

}
