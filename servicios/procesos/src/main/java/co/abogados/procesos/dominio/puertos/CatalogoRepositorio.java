package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.procesos.dominio.modelo.Despacho;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.modelo.Rama;
import co.abogados.procesos.dominio.modelo.TipoProceso;

public interface CatalogoRepositorio {

	/** Solo las ramas que la firma ofrece hoy (activas). */
	List<Rama> ramas();

	/** rama null = todos los tipos. Solo los tipos activos: los que admiten procesos nuevos. */
	List<TipoProceso> tipos(String rama);

	/** rama null = todos los despachos. */
	List<Despacho> despachos(String rama);

	/** Busca un tipo aunque esté desactivado: los procesos que ya lo tienen lo conservan. */
	Optional<TipoProceso> tipo(short idTipo);

	/** ¿Se puede abrir un proceso nuevo con este tipo? Falso si no existe o si la firma dejó de ofrecerlo. */
	default boolean tipoDisponible(short idTipo) {
		return tipo(idTipo).isPresent();
	}

	Optional<Despacho> despacho(short idDespacho);

	/** Despacho cuyos códigos coinciden con los 12 primeros dígitos del radicado. */
	Optional<Despacho> despachoDeRadicado(Radicado radicado);

}
