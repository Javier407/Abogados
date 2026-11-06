package co.abogados.gabinete.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.puertos.CatalogoGabinete;
import co.abogados.gabinete.dominio.puertos.ClienteRepositorio;
import co.abogados.gabinete.dominio.puertos.ConsultaProcesos;

/** Casos de uso de los clientes de la firma. */
@Service
@Transactional
public class ClienteServicio {

	private final ClienteRepositorio clientes;
	private final CatalogoGabinete catalogo;
	private final ConsultaProcesos procesos;
	private final Clock reloj;

	public ClienteServicio(ClienteRepositorio clientes, CatalogoGabinete catalogo, ConsultaProcesos procesos,
			Clock reloj) {
		this.clientes = clientes;
		this.catalogo = catalogo;
		this.procesos = procesos;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public List<Cliente> listar(String texto) {
		return clientes.listar(texto);
	}

	@Transactional(readOnly = true)
	public Cliente obtener(int idCliente) {
		return clientes.buscar(idCliente)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + idCliente));
	}

	public Cliente crear(Cliente nuevo) {
		validar(nuevo, null);
		return clientes.guardar(new Cliente(null, nuevo.tipoPersona(), nuevo.tipoDocumento(), nuevo.numeroDocumento(),
				nuevo.nombre(), nuevo.email(), nuevo.telefono(), nuevo.direccion(), nuevo.ciudad(), nuevo.idSede(),
				LocalDate.now(reloj)));
	}

	public Cliente actualizar(int idCliente, Cliente datos) {
		Cliente actual = obtener(idCliente);
		validar(datos, idCliente);
		return clientes.guardar(datos.conId(idCliente, actual.creado()));
	}

	/** Un cliente con procesos u honorarios tiene historia en la firma: no se borra. */
	public void eliminar(int idCliente) {
		obtener(idCliente);
		int honorarios = clientes.contarHonorarios(idCliente);
		if (honorarios > 0) {
			throw new ConflictoException("El cliente " + idCliente + " tiene " + honorarios
					+ " acuerdo(s) de honorarios y no se puede eliminar");
		}
		int conProcesos = procesos.procesosDelCliente(idCliente);
		if (conProcesos > 0) {
			throw new ConflictoException("El cliente " + idCliente + " tiene " + conProcesos
					+ " proceso(s) registrado(s) y no se puede eliminar");
		}
		clientes.eliminar(idCliente);
	}

	private void validar(Cliente c, Integer idPropio) {
		if (catalogo.sede(c.idSede()).isEmpty()) {
			throw new ReglaNegocioException("No existe la sede " + c.idSede());
		}
		clientes.idPorDocumento(c.tipoDocumento(), c.numeroDocumento())
				.filter(id -> !id.equals(idPropio))
				.ifPresent(id -> {
					throw new ConflictoException("Ya existe un cliente con " + c.tipoDocumento() + " "
							+ c.numeroDocumento() + " (cliente " + id + ")");
				});
	}

}
