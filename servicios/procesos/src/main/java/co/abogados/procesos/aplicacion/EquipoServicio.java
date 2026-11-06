package co.abogados.procesos.aplicacion;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.excepciones.ServicioNoDisponibleException;
import co.abogados.procesos.dominio.modelo.Asignacion;
import co.abogados.procesos.dominio.puertos.AsignacionRepositorio;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;

/**
 * Equipo del proceso (F11): además del responsable, abogados de apoyo y dependientes judiciales.
 * - Solo personal activo de la firma (se verifica en gabinete).
 * - El responsable no se agrega ni se quita aquí: se cambia al editar el proceso (el anterior pasa a apoyo).
 * - Quien está en el equipo ve el proceso en "solo mis procesos" y en su agenda de términos.
 */
@Service
@Transactional
public class EquipoServicio {

	private static final Logger log = LoggerFactory.getLogger(EquipoServicio.class);

	private final AsignacionRepositorio asignaciones;
	private final ProcesoRepositorio procesos;
	private final DirectorioGabinete gabinete;

	public EquipoServicio(AsignacionRepositorio asignaciones, ProcesoRepositorio procesos, DirectorioGabinete gabinete) {
		this.asignaciones = asignaciones;
		this.procesos = procesos;
		this.gabinete = gabinete;
	}

	@Transactional(readOnly = true)
	public List<Asignacion> equipo(int idProceso) {
		existe(idProceso);
		return asignaciones.listar(idProceso).stream().map(a -> a.conNombre(nombre(a.idPersona()))).toList();
	}

	public Asignacion agregar(int idProceso, long idPersona, Asignacion.Rol rol, long usuario) {
		existe(idProceso);
		if (rol == null || rol == Asignacion.Rol.RESPONSABLE) {
			throw new ReglaNegocioException("El responsable se cambia editando el proceso; aquí se agrega apoyo o dependiente");
		}
		DirectorioGabinete.Abogado persona = gabinete.abogado(idPersona)
				.orElseThrow(() -> new ReglaNegocioException("No existe una persona de la firma con la cédula " + idPersona));
		if (!persona.activo()) {
			throw new ReglaNegocioException(persona.nombres() + " " + persona.apellidos() + " está inactivo(a)");
		}
		asignaciones.buscar(idProceso, idPersona).ifPresent(a -> {
			throw new ConflictoException(persona.nombres() + " ya está en el equipo del proceso como "
					+ a.rol().name().toLowerCase());
		});
		asignaciones.agregar(idProceso, idPersona, rol);
		log.info("{} agregado(a) al proceso {} como {} por {}", idPersona, idProceso, rol, usuario);
		return asignaciones.buscar(idProceso, idPersona).orElseThrow()
				.conNombre((persona.nombres() + " " + persona.apellidos()).strip());
	}

	public void quitar(int idProceso, long idPersona, long usuario) {
		Asignacion a = asignaciones.buscar(idProceso, idPersona)
				.orElseThrow(() -> new RecursoNoEncontradoException("Esa persona no está en el equipo del proceso"));
		if (a.rol() == Asignacion.Rol.RESPONSABLE) {
			throw new ReglaNegocioException("El responsable no se quita: asigne otro editando el proceso");
		}
		asignaciones.quitar(idProceso, idPersona);
		log.info("{} quitado(a) del proceso {} por {}", idPersona, idProceso, usuario);
	}

	private void existe(int idProceso) {
		procesos.buscar(idProceso).orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
	}

	/** Si gabinete no responde, el equipo se muestra igual, sin nombres. */
	private String nombre(long idPersona) {
		try {
			return gabinete.abogado(idPersona).map(p -> (p.nombres() + " " + p.apellidos()).strip()).orElse(null);
		} catch (ServicioNoDisponibleException e) {
			return null;
		}
	}

}
