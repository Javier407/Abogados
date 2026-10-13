package co.edu.unbosque.ciclo3back.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.ciclo3back.dao.AbogadosDAO;
import co.edu.unbosque.ciclo3back.dao.AsuntosDAO;
import co.edu.unbosque.ciclo3back.dao.UsuariosDAO;
import co.edu.unbosque.ciclo3back.model.Abogados;
import co.edu.unbosque.ciclo3back.model.Usuarios;

/**
 * Capa de negocio para los abogados del gabinete.
 */
@Service
@Transactional
public class AbogadosService {

	private final AbogadosDAO abogadosDAO;
	private final AsuntosDAO asuntosDAO;
	private final UsuariosDAO usuariosDAO;

	@Autowired
	public AbogadosService(AbogadosDAO abogadosDAO, AsuntosDAO asuntosDAO, UsuariosDAO usuariosDAO) {
		this.abogadosDAO = abogadosDAO;
		this.asuntosDAO = asuntosDAO;
		this.usuariosDAO = usuariosDAO;
	}

	@Transactional(readOnly = true)
	public List<Abogados> listar() {
		List<Abogados> lista = abogadosDAO.findAll();
		lista.sort(Comparator.comparingLong(Abogados::getId_abogado));
		return lista;
	}

	@Transactional(readOnly = true)
	public Abogados buscar(long id) {
		return abogadosDAO.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe un abogado con la identificación " + id));
	}

	public Abogados crear(Abogados abogado) {
		if (abogadosDAO.existsById(abogado.getId_abogado())) {
			throw new ConflictoException("Ya existe un abogado con la identificación " + abogado.getId_abogado());
		}
		validarTarjetaUnica(abogado);
		vincularUsuario(abogado);
		return abogadosDAO.save(abogado);
	}

	public Abogados actualizar(Abogados abogado) {
		buscar(abogado.getId_abogado());
		validarTarjetaUnica(abogado);
		vincularUsuario(abogado);
		return abogadosDAO.save(abogado);
	}

	/** Un abogado asignado a asuntos no se puede borrar; primero hay que reasignar esos asuntos. */
	public void eliminar(long id) {
		buscar(id);
		long asuntos = asuntosDAO.contarPorAbogado(id);
		if (asuntos > 0) {
			throw new ConflictoException("El abogado " + id + " lleva " + asuntos
					+ " asunto(s) y no se puede eliminar");
		}
		abogadosDAO.deleteById(id);
	}

	/**
	 * Convierte la cédula de usuario recibida en la relación con Usuarios.
	 * Sin cédula, el abogado queda sin cuenta. El usuario debe existir y no estar
	 * vinculado a otro abogado (relación 1 : 0..1).
	 */
	private void vincularUsuario(Abogados abogado) {
		Long cedula = abogado.getCedula_usuario();
		if (cedula == null) {
			abogado.setUsuario(null);
			return;
		}
		if (cedula <= 0) {
			throw new ReglaNegocioException("La cédula del usuario debe ser un número positivo");
		}
		Usuarios usuario = usuariosDAO.findById(cedula)
				.orElseThrow(() -> new ReglaNegocioException("No existe un usuario con la cédula " + cedula));
		if (abogadosDAO.contarPorUsuario(cedula, abogado.getId_abogado()) > 0) {
			throw new ConflictoException("El usuario " + cedula + " ya está vinculado a otro abogado");
		}
		abogado.setUsuario(usuario);
	}

	/** La tarjeta profesional identifica al abogado ante el Consejo Superior: no puede repetirse. */
	private void validarTarjetaUnica(Abogados abogado) {
		if (abogadosDAO.contarPorTarjeta(abogado.getTarjeta_profesional(), abogado.getId_abogado()) > 0) {
			throw new ConflictoException("La tarjeta profesional " + abogado.getTarjeta_profesional()
					+ " ya está registrada para otro abogado");
		}
	}

}
