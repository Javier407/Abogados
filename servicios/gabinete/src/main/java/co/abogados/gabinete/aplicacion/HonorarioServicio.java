package co.abogados.gabinete.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.EstadoHonorario;
import co.abogados.gabinete.dominio.modelo.FormaCobro;
import co.abogados.gabinete.dominio.modelo.Honorario;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.Pago;
import co.abogados.gabinete.dominio.puertos.ClienteRepositorio;
import co.abogados.gabinete.dominio.puertos.ConsultaProcesos;
import co.abogados.gabinete.dominio.puertos.HonorarioRepositorio;
import co.abogados.gabinete.dominio.puertos.TarifaRepositorio;

/** Acuerdos de honorarios por proceso y los pagos de los clientes. */
@Service
@Transactional
public class HonorarioServicio {

	private final HonorarioRepositorio honorarios;
	private final ClienteRepositorio clientes;
	private final TarifaRepositorio tarifas;
	private final ConsultaProcesos procesos;
	private final Clock reloj;

	public HonorarioServicio(HonorarioRepositorio honorarios, ClienteRepositorio clientes, TarifaRepositorio tarifas,
			ConsultaProcesos procesos, Clock reloj) {
		this.honorarios = honorarios;
		this.clientes = clientes;
		this.tarifas = tarifas;
		this.procesos = procesos;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public List<HonorarioVista> listar(Integer idCliente, Integer idProceso, EstadoHonorario estado) {
		return honorarios.listar(idCliente, idProceso, estado == null ? null : estado.name());
	}

	@Transactional(readOnly = true)
	public HonorarioVista obtener(int idHonorario) {
		return honorarios.buscar(idHonorario)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el acuerdo de honorarios " + idHonorario));
	}

	public HonorarioVista crear(Honorario nuevo) {
		if (clientes.buscar(nuevo.idCliente()).isEmpty()) {
			throw new ReglaNegocioException("No existe el cliente " + nuevo.idCliente());
		}
		if (nuevo.idTarifa() != null && !tarifas.existe(nuevo.idTarifa())) {
			throw new ReglaNegocioException("No existe la tarifa " + nuevo.idTarifa());
		}
		if (nuevo.fechaPacto().isAfter(LocalDate.now(reloj))) {
			throw new ReglaNegocioException("La fecha del acuerdo no puede ser futura");
		}
		Integer clienteDelProceso = procesos.clienteDelProceso(nuevo.idProceso())
				.orElseThrow(() -> new ReglaNegocioException("No existe el proceso " + nuevo.idProceso()));
		if (clienteDelProceso != nuevo.idCliente()) {
			throw new ReglaNegocioException("El proceso " + nuevo.idProceso() + " es de otro cliente");
		}
		if (honorarios.existeParaProceso(nuevo.idProceso())) {
			throw new ConflictoException("El proceso " + nuevo.idProceso() + " ya tiene un acuerdo de honorarios");
		}
		return obtener(honorarios.crear(nuevo.conEstado(EstadoHonorario.VIGENTE)));
	}

	public HonorarioVista cambiarEstado(int idHonorario, EstadoHonorario estado) {
		obtener(idHonorario);
		honorarios.cambiarEstado(idHonorario, estado.name());
		return obtener(idHonorario);
	}

	@Transactional(readOnly = true)
	public List<Pago> pagos(int idHonorario) {
		obtener(idHonorario);
		return honorarios.pagos(idHonorario);
	}

	/**
	 * Registra un pago. En un acuerdo de valor FIJO no se puede pagar más que el saldo, y al completar
	 * el valor el acuerdo queda CUMPLIDO. En MENSUAL y MIXTO el valor fijo no es un tope (mensualidades,
	 * o porcentaje que se paga al final según el resultado).
	 */
	public HonorarioVista registrarPago(int idHonorario, Pago pago) {
		Honorario h = honorarios.acuerdo(idHonorario)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el acuerdo de honorarios " + idHonorario));
		if (h.estado() != EstadoHonorario.VIGENTE) {
			throw new ReglaNegocioException("El acuerdo está " + h.estado() + ": no recibe pagos");
		}
		if (pago.fecha().isAfter(LocalDate.now(reloj))) {
			throw new ReglaNegocioException("La fecha del pago no puede ser futura");
		}
		if (pago.fecha().isBefore(h.fechaPacto())) {
			throw new ReglaNegocioException("El pago no puede ser anterior al acuerdo (" + h.fechaPacto() + ")");
		}
		BigDecimal pagado = honorarios.totalPagado(idHonorario);
		if (h.formaCobro() == FormaCobro.FIJO) {
			BigDecimal saldo = h.valorFijo().subtract(pagado);
			if (pago.valor().compareTo(saldo) > 0) {
				throw new ReglaNegocioException("El pago (" + pago.valor().toPlainString() + ") supera el saldo pendiente ("
						+ saldo.toPlainString() + ")");
			}
		}
		honorarios.registrarPago(new Pago(null, idHonorario, pago.fecha(), pago.valor(), pago.medio(), pago.referencia()));
		if (h.formaCobro() == FormaCobro.FIJO && pagado.add(pago.valor()).compareTo(h.valorFijo()) == 0) {
			honorarios.cambiarEstado(idHonorario, EstadoHonorario.CUMPLIDO.name());
		}
		return obtener(idHonorario);
	}

}
