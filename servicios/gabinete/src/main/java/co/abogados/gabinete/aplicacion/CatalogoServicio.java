package co.abogados.gabinete.aplicacion;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Sede;
import co.abogados.gabinete.dominio.puertos.CatalogoGabinete;

/** Sedes, cargos y áreas de práctica para los formularios. */
@Service
@Transactional(readOnly = true)
public class CatalogoServicio {

	private final CatalogoGabinete catalogo;

	public CatalogoServicio(CatalogoGabinete catalogo) {
		this.catalogo = catalogo;
	}

	public List<Sede> sedes() {
		return catalogo.sedes();
	}

	public List<Cargo> cargos() {
		return catalogo.cargos();
	}

	public List<AreaPractica> areas() {
		return catalogo.areas();
	}

}
