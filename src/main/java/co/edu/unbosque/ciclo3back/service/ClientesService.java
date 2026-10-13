package co.edu.unbosque.ciclo3back.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.ciclo3back.dao.AsuntosDAO;
import co.edu.unbosque.ciclo3back.dao.ClientesDAO;
import co.edu.unbosque.ciclo3back.model.Clientes;

/**
 * Capa de negocio para los clientes del gabinete.
 * El controlador solo delega aquí; este servicio decide qué se puede hacer
 * y lanza excepciones de negocio cuando algo no cumple las reglas.
 */
@Service
@Transactional
public class ClientesService {

	private final ClientesDAO clientesDAO;
	private final AsuntosDAO asuntosDAO;

	@Autowired
	public ClientesService(ClientesDAO clientesDAO, AsuntosDAO asuntosDAO) {
		this.clientesDAO = clientesDAO;
		this.asuntosDAO = asuntosDAO;
	}

	@Transactional(readOnly = true)
	public List<Clientes> listar() {
		List<Clientes> lista = clientesDAO.findAll();
		lista.sort(Comparator.comparingLong(Clientes::getId_cliente));
		return lista;
	}

	@Transactional(readOnly = true)
	public Clientes buscar(long id) {
		return clientesDAO.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe un cliente con la identificación " + id));
	}

	public Clientes crear(Clientes cliente) {
		if (clientesDAO.existsById(cliente.getId_cliente())) {
			throw new ConflictoException("Ya existe un cliente con la identificación " + cliente.getId_cliente());
		}
		return clientesDAO.save(cliente);
	}

	public Clientes actualizar(Clientes cliente) {
		buscar(cliente.getId_cliente());
		return clientesDAO.save(cliente);
	}

	/** Un cliente con asuntos no se puede borrar: se perdería el historial del gabinete. */
	public void eliminar(long id) {
		buscar(id);
		long asuntos = asuntosDAO.contarPorCliente(id);
		if (asuntos > 0) {
			throw new ConflictoException("El cliente " + id + " tiene " + asuntos
					+ " asunto(s) registrado(s) y no se puede eliminar");
		}
		clientesDAO.deleteById(id);
	}

}
