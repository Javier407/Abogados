package co.abogados.procesos.dominio.puertos;

import java.util.Optional;

import co.abogados.procesos.dominio.modelo.Portal.ResumenPublicado;

/** Resúmenes para el cliente publicados por el abogado (procesos.resumenes_cliente). */
public interface ResumenRepositorio {

	ResumenPublicado guardar(int idProceso, String texto, long publicadoPor);

	/** El más reciente del proceso. */
	Optional<ResumenPublicado> ultimo(int idProceso);

}
