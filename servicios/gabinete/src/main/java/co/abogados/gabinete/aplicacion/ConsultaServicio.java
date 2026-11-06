package co.abogados.gabinete.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.Consulta;
import co.abogados.gabinete.dominio.puertos.ClienteRepositorio;
import co.abogados.gabinete.dominio.puertos.ConsultaRepositorio;

/**
 * Consulta inicial (F10).
 * - Al registrarla (y cada vez que se consulta) se revisa el conflicto de intereses: si la contraparte ya es
 *   cliente de la firma (mismo documento o nombre), la firma no debería tomar el caso sin revisarlo.
 * - Contratar crea la ficha de cliente con los datos de la consulta (o enlaza la que ya existe con ese
 *   documento) y deja la consulta CONTRATADA. No contratar o remitir a otro abogado pide un motivo.
 */
@Service
@Transactional
public class ConsultaServicio {

	private static final Logger log = LoggerFactory.getLogger(ConsultaServicio.class);

	/** La consulta con los avisos de conflicto de intereses. */
	public record ConsultaConAvisos(Consulta consulta, List<String> avisos) {
	}

	/** Datos que faltan para crear la ficha de cliente (la consulta no los pide). */
	public record DatosCliente(String ciudad, short idSede, String direccion) {
	}

	private final ConsultaRepositorio consultas;
	private final ClienteRepositorio clientes;
	private final ClienteServicio clienteServicio;
	private final Clock reloj;

	public ConsultaServicio(ConsultaRepositorio consultas, ClienteRepositorio clientes, ClienteServicio clienteServicio,
			Clock reloj) {
		this.consultas = consultas;
		this.clientes = clientes;
		this.clienteServicio = clienteServicio;
		this.reloj = reloj;
	}

	public ConsultaConAvisos registrar(Consulta c, long usuario) {
		Consulta nueva = consultas.guardar(new Consulta(null, c.fecha() == null ? LocalDate.now(reloj) : c.fecha(),
				c.nombre(), c.tipoDocumento(), c.numeroDocumento(), c.email(), c.telefono(), c.contraparte(),
				c.contraparteDocumento(), c.codigoRama(), c.asunto(), c.descripcion(), c.idAbogado(),
				Consulta.Estado.ABIERTA, null, null, usuario));
		List<String> avisos = avisos(nueva);
		log.info("Consulta {} registrada por {}{}", nueva.idConsulta(), usuario,
				avisos.isEmpty() ? "" : " con avisos de conflicto: " + avisos);
		return new ConsultaConAvisos(nueva, avisos);
	}

	@Transactional(readOnly = true)
	public List<Consulta> listar(Consulta.Estado estado) {
		return consultas.listar(estado);
	}

	@Transactional(readOnly = true)
	public ConsultaConAvisos obtener(int idConsulta) {
		Consulta c = buscar(idConsulta);
		return new ConsultaConAvisos(c, avisos(c));
	}

	/** Contrató: crea la ficha de cliente (o enlaza la existente con el mismo documento). */
	public ConsultaConAvisos contratar(int idConsulta, DatosCliente datos, long usuario) {
		Consulta c = abierta(idConsulta);
		if (c.tipoDocumento() == null) {
			throw new ReglaNegocioException("Registre el documento de quien consulta antes de crear su ficha de cliente");
		}
		int idCliente = clientes.idPorDocumento(c.tipoDocumento(), c.numeroDocumento())
				.orElseGet(() -> clienteServicio.crear(new Cliente(null, c.tipoPersona(), c.tipoDocumento(),
						c.numeroDocumento(), c.nombre(), c.email(), c.telefono(), datos.direccion(), datos.ciudad(),
						datos.idSede(), null)).idCliente());
		consultas.cerrar(idConsulta, Consulta.Estado.CONTRATADA, null, idCliente);
		log.info("Consulta {} contratada por {}: cliente {}", idConsulta, usuario, idCliente);
		return obtener(idConsulta);
	}

	/** No contrató o se remitió a otro abogado: queda cerrada con el motivo. */
	public ConsultaConAvisos cerrar(int idConsulta, Consulta.Estado estado, String motivo, long usuario) {
		if (estado != Consulta.Estado.NO_CONTRATADA && estado != Consulta.Estado.REMITIDA) {
			throw new ReglaNegocioException("Para cerrar sin contratar use NO_CONTRATADA o REMITIDA");
		}
		if (motivo == null || motivo.isBlank() || motivo.length() > 300) {
			throw new ReglaNegocioException("Escriba el motivo (máximo 300 caracteres)");
		}
		abierta(idConsulta);
		consultas.cerrar(idConsulta, estado, motivo.strip(), null);
		log.info("Consulta {} cerrada como {} por {}", idConsulta, estado, usuario);
		return obtener(idConsulta);
	}

	// ------------------------------------------------------------------

	/** Avisos de conflicto de intereses y de cliente existente. */
	List<String> avisos(Consulta c) {
		List<String> avisos = new ArrayList<>();
		String contraparte = Consulta.normalizar(c.contraparte());
		String consultante = Consulta.normalizar(c.nombre());
		for (Cliente cl : clientes.listar(null)) {
			boolean mismaContraparte = (c.contraparteDocumento() != null
					&& c.contraparteDocumento().equals(cl.numeroDocumento()))
					|| (!contraparte.isEmpty() && contraparte.equals(Consulta.normalizar(cl.nombre())));
			if (mismaContraparte) {
				avisos.add("Conflicto de intereses: la contraparte «" + c.contraparte() + "» ya es cliente de la firma ("
						+ cl.nombre() + ", cliente " + cl.idCliente() + ")");
			}
			boolean yaEsCliente = (c.numeroDocumento() != null && c.numeroDocumento().equals(cl.numeroDocumento()))
					|| consultante.equals(Consulta.normalizar(cl.nombre()));
			if (yaEsCliente && c.estado() == Consulta.Estado.ABIERTA) {
				avisos.add("Quien consulta ya es cliente de la firma (" + cl.nombre() + ", cliente " + cl.idCliente() + ")");
			}
		}
		return avisos;
	}

	private Consulta buscar(int idConsulta) {
		return consultas.buscar(idConsulta)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la consulta " + idConsulta));
	}

	private Consulta abierta(int idConsulta) {
		Consulta c = buscar(idConsulta);
		if (c.estado() != Consulta.Estado.ABIERTA) {
			throw new ConflictoException("La consulta " + idConsulta + " ya está cerrada (" + c.estado() + ")");
		}
		return c;
	}

}
