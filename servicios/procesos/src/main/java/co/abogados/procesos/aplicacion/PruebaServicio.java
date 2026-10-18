package co.abogados.procesos.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.CalendarioJudicial;
import co.abogados.procesos.dominio.modelo.EstadoTermino;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Prueba;
import co.abogados.procesos.dominio.modelo.Termino;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.PruebaRepositorio;
import co.abogados.procesos.dominio.puertos.TerminoRepositorio;

/**
 * Pruebas del proceso. Si una prueba pendiente tiene fecha de entrega (por ejemplo, el dictamen del perito),
 * se crea un término que vence ese día y avisa como los demás. Al practicarse, el término queda cumplido;
 * si el juez la niega o se elimina, el término pendiente se borra.
 */
@Service
@Transactional
public class PruebaServicio {

	private final ProcesoRepositorio procesos;
	private final PruebaRepositorio pruebas;
	private final TerminoRepositorio terminos;
	private final CalendarioServicio calendario;
	private final DirectorioGabinete gabinete;
	private final Clock reloj;

	public PruebaServicio(ProcesoRepositorio procesos, PruebaRepositorio pruebas, TerminoRepositorio terminos,
			CalendarioServicio calendario, DirectorioGabinete gabinete, Clock reloj) {
		this.procesos = procesos;
		this.pruebas = pruebas;
		this.terminos = terminos;
		this.calendario = calendario;
		this.gabinete = gabinete;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public List<Prueba> pruebas(int idProceso) {
		proceso(idProceso);
		return pruebas.deProceso(idProceso);
	}

	public Prueba crear(int idProceso, Prueba datos) {
		ProcesoVista proceso = proceso(idProceso);
		validarPerito(datos.idPerito());
		Integer idTermino = datos.pendiente() && datos.fechaEntrega() != null
				? crearTermino(proceso, datos.descripcion(), datos.fechaEntrega())
				: null;
		int id = pruebas.crear(new Prueba(null, idProceso, datos.tipo(), datos.descripcion(), datos.idPerito(),
				datos.estado(), datos.fechaEntrega(), idTermino));
		return pruebas.buscar(id).orElseThrow();
	}

	public Prueba actualizar(int idProceso, int idPrueba, Prueba datos) {
		ProcesoVista proceso = proceso(idProceso);
		Prueba actual = pruebaDelProceso(idProceso, idPrueba);
		if (!Objects.equals(actual.idPerito(), datos.idPerito())) {
			validarPerito(datos.idPerito());
		}
		Integer idTermino = actual.idTermino();
		boolean terminoAbierto = terminoPendiente(idTermino);
		boolean cambioEntrega = !Objects.equals(actual.fechaEntrega(), datos.fechaEntrega());

		if (datos.estado() == Prueba.Estado.PRACTICADA) {
			if (terminoAbierto) {
				terminos.marcarCumplido(idTermino);                  // se entregó: el término se cumplió
			}
		} else if (datos.estado() == Prueba.Estado.NEGADA) {
			if (terminoAbierto) {
				terminos.eliminar(idTermino);                        // el juez la negó: ya no hay nada que entregar
				idTermino = null;
			}
		} else if (cambioEntrega || (idTermino == null && datos.fechaEntrega() != null)) {
			if (terminoAbierto) {
				terminos.eliminar(idTermino);                        // otra fecha: el término viejo sobra
			}
			idTermino = datos.fechaEntrega() == null ? null
					: crearTermino(proceso, datos.descripcion(), datos.fechaEntrega());
		}
		pruebas.actualizar(new Prueba(idPrueba, idProceso, datos.tipo(), datos.descripcion(), datos.idPerito(),
				datos.estado(), datos.fechaEntrega(), idTermino));
		return pruebas.buscar(idPrueba).orElseThrow();
	}

	public void eliminar(int idProceso, int idPrueba) {
		Prueba actual = pruebaDelProceso(idProceso, idPrueba);
		pruebas.eliminar(idPrueba);
		if (terminoPendiente(actual.idTermino())) {
			terminos.eliminar(actual.idTermino());
		}
	}

	/** Término que vence el día de entrega (o el último día hábil antes, si ese día no es hábil). */
	private int crearTermino(ProcesoVista proceso, String descripcion, LocalDate entrega) {
		LocalDate hoy = LocalDate.now(reloj);
		if (!entrega.isAfter(hoy)) {
			throw new ReglaNegocioException("La fecha de entrega debe ser posterior a hoy");
		}
		if (proceso.idResponsable() == null) {
			throw new ReglaNegocioException("El proceso no tiene abogado responsable para el término de entrega");
		}
		CalendarioJudicial cal = calendario.calendario();
		int dias = cal.diasHabilesEntre(hoy, entrega);
		if (dias < 1) {
			throw new ReglaNegocioException("Entre hoy y la fecha de entrega no hay ningún día hábil");
		}
		if (dias > 365) {
			throw new ReglaNegocioException("La fecha de entrega está a más de 365 días hábiles");
		}
		String texto = "Entrega de prueba: " + descripcion;
		return terminos.crear(new Termino(null, proceso.idProceso(), null, texto.length() > 150 ? texto.substring(0, 150) : texto,
				hoy, dias, cal.sumarDiasHabiles(hoy, dias), EstadoTermino.PENDIENTE, proceso.idResponsable()));
	}

	private boolean terminoPendiente(Integer idTermino) {
		return idTermino != null && terminos.buscar(idTermino).map(t -> "PENDIENTE".equals(t.estado())).orElse(false);
	}

	private void validarPerito(Integer idPerito) {
		if (idPerito != null && gabinete.perito(idPerito).isEmpty()) {
			throw new ReglaNegocioException("No existe el perito " + idPerito);
		}
	}

	private ProcesoVista proceso(int idProceso) {
		return procesos.buscar(idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
	}

	private Prueba pruebaDelProceso(int idProceso, int idPrueba) {
		return pruebas.buscar(idPrueba)
				.filter(p -> p.idProceso() == idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la prueba " + idPrueba + " en este proceso"));
	}

}
