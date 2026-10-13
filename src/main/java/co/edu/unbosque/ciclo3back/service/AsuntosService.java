package co.edu.unbosque.ciclo3back.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.ciclo3back.dao.AbogadosDAO;
import co.edu.unbosque.ciclo3back.dao.AsuntosDAO;
import co.edu.unbosque.ciclo3back.dao.ClientesDAO;
import co.edu.unbosque.ciclo3back.model.Abogados;
import co.edu.unbosque.ciclo3back.model.AsuntoRequest;
import co.edu.unbosque.ciclo3back.model.Asuntos;
import co.edu.unbosque.ciclo3back.model.Clientes;
import co.edu.unbosque.ciclo3back.model.EstadoAsunto;

/**
 * Capa de negocio para los asuntos (expedientes) del gabinete.
 *
 * Reglas que se aplican:
 *  1. El número de expediente es único.
 *  2. Todo asunto pertenece a un cliente existente.
 *  3. Los abogados indicados deben existir (puede haber cero, uno o varios).
 *  4. La fecha de archivo no puede ser anterior a la fecha de inicio ni futura.
 *  5. Los estados de cierre (ARCHIVADO, FINALIZADO) exigen fecha de archivo;
 *     los estados abiertos (EN_TRAMITE, SUSPENDIDO) no pueden tenerla.
 */
@Service
@Transactional
public class AsuntosService {

	private final AsuntosDAO asuntosDAO;
	private final ClientesDAO clientesDAO;
	private final AbogadosDAO abogadosDAO;

	@Autowired
	public AsuntosService(AsuntosDAO asuntosDAO, ClientesDAO clientesDAO, AbogadosDAO abogadosDAO) {
		this.asuntosDAO = asuntosDAO;
		this.clientesDAO = clientesDAO;
		this.abogadosDAO = abogadosDAO;
	}

	@Transactional(readOnly = true)
	public List<Asuntos> listar() {
		List<Asuntos> lista = asuntosDAO.findAll();
		lista.sort(Comparator.comparing(Asuntos::getNumero_expediente));
		return lista;
	}

	@Transactional(readOnly = true)
	public Asuntos buscar(String expediente) {
		return asuntosDAO.findById(expediente)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe un asunto con el expediente " + expediente));
	}

	@Transactional(readOnly = true)
	public List<Asuntos> listarPorCliente(long idCliente) {
		if (!clientesDAO.existsById(idCliente)) {
			throw new RecursoNoEncontradoException("No existe un cliente con la identificación " + idCliente);
		}
		return asuntosDAO.buscarPorCliente(idCliente);
	}

	@Transactional(readOnly = true)
	public List<Asuntos> listarPorAbogado(long idAbogado) {
		if (!abogadosDAO.existsById(idAbogado)) {
			throw new RecursoNoEncontradoException("No existe un abogado con la identificación " + idAbogado);
		}
		return asuntosDAO.buscarPorAbogado(idAbogado);
	}

	@Transactional(readOnly = true)
	public List<Asuntos> listarPorEstado(EstadoAsunto estado) {
		return asuntosDAO.buscarPorEstado(estado);
	}

	public Asuntos crear(AsuntoRequest req) {
		if (asuntosDAO.existsById(req.getNumero_expediente())) {
			throw new ConflictoException("Ya existe un asunto con el expediente " + req.getNumero_expediente());
		}
		Asuntos asunto = new Asuntos();
		asunto.setNumero_expediente(req.getNumero_expediente());
		return asuntosDAO.save(completar(asunto, req));
	}

	public Asuntos actualizar(AsuntoRequest req) {
		Asuntos asunto = buscar(req.getNumero_expediente());
		return asuntosDAO.save(completar(asunto, req));
	}

	/** Cierra el asunto: pasa a ARCHIVADO con la fecha indicada (o la de hoy si no se envía). */
	public Asuntos archivar(String expediente, LocalDate fecha) {
		Asuntos asunto = buscar(expediente);
		if (asunto.getEstado().esCierre()) {
			throw new ReglaNegocioException("El asunto " + expediente + " ya está cerrado (" + asunto.getEstado() + ")");
		}
		LocalDate fechaArchivo = fecha != null ? fecha : LocalDate.now();
		validarFechas(asunto.getFecha_inicio(), fechaArchivo, EstadoAsunto.ARCHIVADO);
		asunto.setEstado(EstadoAsunto.ARCHIVADO);
		asunto.setFecha_archivo(fechaArchivo);
		return asuntosDAO.save(asunto);
	}

	public void eliminar(String expediente) {
		asuntosDAO.delete(buscar(expediente));
	}

	/** Copia los datos del request al asunto, resolviendo cliente y abogados y validando las reglas. */
	private Asuntos completar(Asuntos asunto, AsuntoRequest req) {
		validarFechas(req.getFecha_inicio(), req.getFecha_archivo(), req.getEstado());

		Clientes cliente = clientesDAO.findById(req.getId_cliente())
				.orElseThrow(() -> new ReglaNegocioException("No existe un cliente con la identificación " + req.getId_cliente()));

		Set<Long> ids = new LinkedHashSet<>(req.getIds_abogados());
		if (ids.contains(null)) {
			throw new ReglaNegocioException("La lista de abogados contiene identificaciones vacías");
		}
		List<Abogados> encontrados = abogadosDAO.findAllById(ids);
		if (encontrados.size() != ids.size()) {
			Set<Long> existentes = encontrados.stream().map(Abogados::getId_abogado).collect(Collectors.toSet());
			ids.removeAll(existentes);
			throw new ReglaNegocioException("No existen los abogados con identificación " + ids);
		}

		asunto.setFecha_inicio(req.getFecha_inicio());
		asunto.setFecha_archivo(req.getFecha_archivo());
		asunto.setEstado(req.getEstado());
		asunto.setCliente(cliente);
		asunto.setAbogados(new HashSet<>(encontrados));
		return asunto;
	}

	private void validarFechas(LocalDate inicio, LocalDate archivo, EstadoAsunto estado) {
		if (estado.esCierre() && archivo == null) {
			throw new ReglaNegocioException("Un asunto en estado " + estado + " debe tener fecha de archivo");
		}
		if (!estado.esCierre() && archivo != null) {
			throw new ReglaNegocioException("Un asunto en estado " + estado + " no puede tener fecha de archivo");
		}
		if (archivo != null) {
			if (archivo.isBefore(inicio)) {
				throw new ReglaNegocioException("La fecha de archivo no puede ser anterior a la fecha de inicio");
			}
			if (archivo.isAfter(LocalDate.now())) {
				throw new ReglaNegocioException("La fecha de archivo no puede ser futura");
			}
		}
	}

}
