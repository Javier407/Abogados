package co.abogados.procesos.aplicacion;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.modelo.Despacho;
import co.abogados.procesos.dominio.modelo.Rama;
import co.abogados.procesos.dominio.modelo.TipoProceso;
import co.abogados.procesos.dominio.puertos.CatalogoRepositorio;

/** Ramas, tipos de proceso y despachos: los listados que alimentan los formularios. */
@Service
@Transactional(readOnly = true)
public class CatalogoServicio {

	private final CatalogoRepositorio catalogos;

	public CatalogoServicio(CatalogoRepositorio catalogos) {
		this.catalogos = catalogos;
	}

	public List<Rama> ramas() {
		return catalogos.ramas();
	}

	public List<TipoProceso> tipos(String rama) {
		return catalogos.tipos(rama);
	}

	public List<Despacho> despachos(String rama) {
		return catalogos.despachos(rama);
	}

}
