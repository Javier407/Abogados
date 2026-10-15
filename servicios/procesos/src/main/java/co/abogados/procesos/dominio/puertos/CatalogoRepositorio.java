package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.procesos.dominio.modelo.Despacho;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.modelo.Rama;
import co.abogados.procesos.dominio.modelo.TipoProceso;

public interface CatalogoRepositorio {

	List<Rama> ramas();

	/** rama null = todos los tipos. */
	List<TipoProceso> tipos(String rama);

	/** rama null = todos los despachos. */
	List<Despacho> despachos(String rama);

	Optional<TipoProceso> tipo(short idTipo);

	Optional<Despacho> despacho(short idDespacho);

	/** Despacho cuyos códigos coinciden con los 12 primeros dígitos del radicado. */
	Optional<Despacho> despachoDeRadicado(Radicado radicado);

}
