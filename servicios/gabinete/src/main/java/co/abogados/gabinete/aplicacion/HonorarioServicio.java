package co.abogados.gabinete.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.Anulacion;
import co.abogados.gabinete.dominio.modelo.EstadoHonorario;
import co.abogados.gabinete.dominio.modelo.FormaCobro;
import co.abogados.gabinete.dominio.modelo.Honorario;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.Liquidacion;
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
	 * Registra un pago. Cuando el acuerdo tiene un total conocido (valor FIJO, o la parte variable ya liquidada)
	 * no se puede pagar más que el saldo, y al completarlo el acuerdo queda CUMPLIDO. En MENSUAL, y en MIXTO o
	 * porcentaje sin liquidar, no hay tope (mensualidades, o un resultado que aún no se conoce).
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
		BigDecimal total = total(h);
		if (total != null) {
			BigDecimal saldo = total.subtract(pagado);
			if (pago.valor().compareTo(saldo) > 0) {
				throw new ReglaNegocioException("El pago (" + pago.valor().toPlainString() + ") supera el saldo pendiente ("
						+ saldo.toPlainString() + ")");
			}
		}
		honorarios.registrarPago(new Pago(null, idHonorario, pago.fecha(), pago.valor(), pago.medio(), pago.referencia()));
		if (total != null && pagado.add(pago.valor()).compareTo(total) == 0) {
			honorarios.cambiarEstado(idHonorario, EstadoHonorario.CUMPLIDO.name());
		}
		return obtener(idHonorario);
	}

	/**
	 * Anula un pago mal registrado: no se borra, queda con el motivo, la fecha y quién lo anuló, y deja de
	 * contar. Si el acuerdo estaba CUMPLIDO vuelve a VIGENTE (reaparece el saldo).
	 */
	public HonorarioVista anularPago(int idHonorario, int idPago, String motivo, Long quien) {
		Honorario h = acuerdo(idHonorario);
		Pago p = honorarios.pago(idPago).filter(x -> x.idHonorario() == idHonorario)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + idPago + " en este acuerdo"));
		if (p.anulado()) {
			throw new ConflictoException("El pago " + idPago + " ya estaba anulado");
		}
		Anulacion anulacion = new Anulacion(motivo);
		honorarios.anularPago(idPago, Instant.now(reloj), quien, anulacion.motivo());
		if (h.estado() == EstadoHonorario.CUMPLIDO) {
			honorarios.cambiarEstado(idHonorario, EstadoHonorario.VIGENTE.name());
		}
		return obtener(idHonorario);
	}

	/**
	 * Liquida la parte variable con lo que obtuvo el cliente (o la cuantía): porcentaje × base, más el fijo en
	 * MIXTO. Desde entonces el acuerdo tiene un total: los pagos no lo pueden superar y al completarlo queda CUMPLIDO.
	 */
	public HonorarioVista liquidar(int idHonorario, BigDecimal base, LocalDate fecha) {
		Honorario h = acuerdo(idHonorario);
		if (h.estado() != EstadoHonorario.VIGENTE) {
			throw new ReglaNegocioException("El acuerdo está " + h.estado() + ": no se liquida");
		}
		LocalDate dia = fecha == null ? LocalDate.now(reloj) : fecha;
		if (dia.isAfter(LocalDate.now(reloj))) {
			throw new ReglaNegocioException("La fecha de liquidación no puede ser futura");
		}
		if (dia.isBefore(h.fechaPacto())) {
			throw new ReglaNegocioException("La liquidación no puede ser anterior al acuerdo (" + h.fechaPacto() + ")");
		}
		BigDecimal valor = Liquidacion.calcular(h, base);
		honorarios.liquidar(idHonorario, base, valor, dia);
		if (honorarios.totalPagado(idHonorario).compareTo(valor) >= 0) {
			honorarios.cambiarEstado(idHonorario, EstadoHonorario.CUMPLIDO.name());
		}
		return obtener(idHonorario);
	}

	/** Total que se debe pagar, si se conoce: el valor FIJO o lo liquidado. */
	private BigDecimal total(Honorario h) {
		if (h.formaCobro() == FormaCobro.FIJO) {
			return h.valorFijo();
		}
		return honorarios.valorLiquidado(h.idHonorario()).orElse(null);
	}

	private Honorario acuerdo(int idHonorario) {
		return honorarios.acuerdo(idHonorario)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el acuerdo de honorarios " + idHonorario));
	}

}
