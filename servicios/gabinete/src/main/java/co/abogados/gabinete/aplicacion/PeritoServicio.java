package co.abogados.gabinete.aplicacion;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.modelo.Perito;
import co.abogados.gabinete.dominio.puertos.PeritoRepositorio;

/** Peritos de la firma. No se borran: las pruebas periciales de los procesos los referencian. */
@Service
@Transactional
public class PeritoServicio {

	private final PeritoRepositorio peritos;

	public PeritoServicio(PeritoRepositorio peritos) {
		this.peritos = peritos;
	}

	@Transactional(readOnly = true)
	public List<Perito> listar(String texto) {
		return peritos.listar(texto);
	}

	@Transactional(readOnly = true)
	public Perito obtener(int idPerito) {
		return peritos.buscar(idPerito)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el perito " + idPerito));
	}

	public Perito crear(Perito nuevo) {
		return peritos.guardar(new Perito(null, nuevo.nombre(), nuevo.especialidad(), nuevo.profesion(),
				nuevo.registro(), nuevo.telefono(), nuevo.email(), nuevo.ciudad(), nuevo.tarifaDictamen(),
				nuevo.calificacion()));
	}

	public Perito actualizar(int idPerito, Perito datos) {
		obtener(idPerito);
		return peritos.guardar(datos.conId(idPerito));
	}

}
