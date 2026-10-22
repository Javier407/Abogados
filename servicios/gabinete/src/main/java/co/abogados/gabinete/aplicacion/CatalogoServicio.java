package co.abogados.gabinete.aplicacion;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Sede;
import co.abogados.gabinete.dominio.puertos.CatalogoEdicion;
import co.abogados.gabinete.dominio.puertos.CatalogoGabinete;

/**
 * Sedes, cargos y áreas de práctica: se consultan en los formularios y, desde F11, la dirección (ADMIN y SOCIO, lo
 * exige el gateway) los crea, cambia y borra.
 * - Siempre hay una sola sede principal: marcar otra quita la marca a la anterior; no se puede desmarcar sin más.
 * - Códigos de cargo y de rama en mayúsculas y sin repetirse.
 * - El director de un área debe ser personal activo de la firma.
 * - No se borra lo que está en uso (clientes, personal).
 */
@Service
@Transactional
public class CatalogoServicio {

	private static final Logger log = LoggerFactory.getLogger(CatalogoServicio.class);

	private final CatalogoGabinete catalogo;
	private final CatalogoEdicion edicion;

	public CatalogoServicio(CatalogoGabinete catalogo, CatalogoEdicion edicion) {
		this.catalogo = catalogo;
		this.edicion = edicion;
	}

	@Transactional(readOnly = true)
	public List<Sede> sedes() {
		return catalogo.sedes();
	}

	@Transactional(readOnly = true)
	public List<Cargo> cargos() {
		return catalogo.cargos();
	}

	@Transactional(readOnly = true)
	public List<AreaPractica> areas() {
		return catalogo.areas();
	}

	// ------------------------------------------------------------------ sedes

	public Sede guardarSede(Short idSede, Sede s, long usuario) {
		String nombre = obligatorio(s.nombre(), "El nombre de la sede", 60);
		String ciudad = obligatorio(s.ciudad(), "La ciudad", 60);
		String direccion = obligatorio(s.direccion(), "La dirección", 150);
		String telefono = opcional(s.telefono(), 20);
		if (idSede != null) {
			Sede actual = catalogo.sede(idSede).orElseThrow(() -> new RecursoNoEncontradoException("No existe la sede " + idSede));
			if (actual.esPrincipal() && !s.esPrincipal()) {
				throw new ReglaNegocioException("Para cambiar la sede principal, marque como principal la otra sede");
			}
		}
		if (s.esPrincipal()) {
			edicion.desmarcarPrincipales();
		}
		Sede g = edicion.guardarSede(idSede, new Sede((short) 0, nombre, ciudad, direccion, telefono, s.esPrincipal()));
		log.info("Sede {} ({}) guardada por {}", g.idSede(), g.nombre(), usuario);
		return g;
	}

	public void eliminarSede(short idSede, long usuario) {
		Sede s = catalogo.sede(idSede).orElseThrow(() -> new RecursoNoEncontradoException("No existe la sede " + idSede));
		if (s.esPrincipal()) {
			throw new ConflictoException("La sede principal no se borra");
		}
		edicion.eliminarSede(idSede);
		log.info("Sede {} borrada por {}", idSede, usuario);
	}

	// ------------------------------------------------------------------ cargos

	public Cargo guardarCargo(Short idCargo, Cargo c, long usuario) {
		if (idCargo != null) {
			catalogo.cargo(idCargo).orElseThrow(() -> new RecursoNoEncontradoException("No existe el cargo " + idCargo));
		}
		String codigo = codigo(c.codigo(), "El código del cargo");
		boolean repetido = catalogo.cargos().stream()
				.anyMatch(x -> x.codigo().equalsIgnoreCase(codigo) && (idCargo == null || x.idCargo() != idCargo));
		if (repetido) {
			throw new ConflictoException("Ya existe un cargo con el código " + codigo);
		}
		Cargo g = edicion.guardarCargo(idCargo, new Cargo((short) 0, codigo, obligatorio(c.nombre(), "El nombre del cargo", 60),
				c.esAbogado()));
		log.info("Cargo {} ({}) guardado por {}", g.idCargo(), g.codigo(), usuario);
		return g;
	}

	public void eliminarCargo(short idCargo, long usuario) {
		catalogo.cargo(idCargo).orElseThrow(() -> new RecursoNoEncontradoException("No existe el cargo " + idCargo));
		edicion.eliminarCargo(idCargo);
		log.info("Cargo {} borrado por {}", idCargo, usuario);
	}

	// ------------------------------------------------------------------ áreas de práctica

	public AreaPractica guardarArea(Short idArea, AreaPractica a, long usuario) {
		if (idArea != null) {
			catalogo.area(idArea).orElseThrow(() -> new RecursoNoEncontradoException("No existe el área " + idArea));
		}
		String rama = codigo(a.codigoRama(), "La rama del área");
		boolean repetida = catalogo.areas().stream()
				.anyMatch(x -> x.codigoRama().equalsIgnoreCase(rama) && (idArea == null || x.idArea() != idArea));
		if (repetida) {
			throw new ConflictoException("Ya existe un área para la rama " + rama);
		}
		if (!edicion.personalActivo(a.idDirector())) {
			throw new ReglaNegocioException("El director debe ser una persona activa de la firma");
		}
		AreaPractica g = edicion.guardarArea(idArea, new AreaPractica((short) 0, rama,
				obligatorio(a.nombre(), "El nombre del área", 60), a.idDirector()));
		log.info("Área {} ({}) guardada por {}", g.idArea(), g.codigoRama(), usuario);
		return g;
	}

	public void eliminarArea(short idArea, long usuario) {
		catalogo.area(idArea).orElseThrow(() -> new RecursoNoEncontradoException("No existe el área " + idArea));
		edicion.eliminarArea(idArea);
		log.info("Área {} borrada por {}", idArea, usuario);
	}

	// ------------------------------------------------------------------

	private static String obligatorio(String valor, String campo, int maximo) {
		String v = valor == null ? "" : valor.strip().replaceAll("\\s+", " ");
		if (v.isEmpty()) {
			throw new ReglaNegocioException(campo + " es obligatorio");
		}
		if (v.length() > maximo) {
			throw new ReglaNegocioException(campo + " admite máximo " + maximo + " caracteres");
		}
		return v;
	}

	private static String opcional(String valor, int maximo) {
		String v = valor == null ? "" : valor.strip();
		if (v.length() > maximo) {
			throw new ReglaNegocioException("Máximo " + maximo + " caracteres");
		}
		return v.isEmpty() ? null : v;
	}

	private static String codigo(String valor, String campo) {
		String v = valor == null ? "" : valor.strip().toUpperCase(Locale.ROOT);
		if (!v.matches("[A-Z][A-Z_]{1,19}")) {
			throw new ReglaNegocioException(campo + " debe tener de 2 a 20 letras mayúsculas o guion bajo");
		}
		return v;
	}

}
