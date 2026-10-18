package co.abogados.gabinete.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.CarteraCliente;
import co.abogados.gabinete.dominio.modelo.Gasto;
import co.abogados.gabinete.dominio.puertos.ConsultaProcesos;
import co.abogados.gabinete.dominio.puertos.GastoRepositorio;

/** Gastos procesales y cartera por cliente (honorarios por cobrar y gastos por reembolsar). */
@Service
@Transactional
public class GastoServicio {

	private final GastoRepositorio gastos;
	private final ConsultaProcesos procesos;
	private final Clock reloj;

	public GastoServicio(GastoRepositorio gastos, ConsultaProcesos procesos, Clock reloj) {
		this.gastos = gastos;
		this.procesos = procesos;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public List<Gasto> listar(Integer idProceso, Integer idCliente) {
		return gastos.listar(idProceso, idCliente);
	}

	/** El cliente del gasto es el del proceso (lo responde el servicio procesos). */
	public Gasto registrar(Gasto datos, Long registradoPor) {
		int idCliente = procesos.clienteDelProceso(datos.idProceso())
				.orElseThrow(() -> new ReglaNegocioException("No existe el proceso " + datos.idProceso()));
		if (datos.fecha().isAfter(LocalDate.now(reloj))) {
			throw new ReglaNegocioException("La fecha del gasto no puede ser futura");
		}
		int id = gastos.crear(new Gasto(null, datos.idProceso(), idCliente, datos.fecha(), datos.concepto(), datos.valor(),
				datos.pagadoPor(), datos.reembolsable(), null, datos.soporteUrl(), registradoPor));
		return buscar(id);
	}

	public Gasto marcarReembolsado(int idGasto, LocalDate fecha) {
		Gasto g = buscar(idGasto);
		if (!g.reembolsable()) {
			throw new ReglaNegocioException("Ese gasto no es reembolsable");
		}
		if (g.reembolsadoEn() != null) {
			throw new ConflictoException("El gasto ya estaba reembolsado (" + g.reembolsadoEn() + ")");
		}
		LocalDate dia = fecha == null ? LocalDate.now(reloj) : fecha;
		if (dia.isAfter(LocalDate.now(reloj)) || dia.isBefore(g.fecha())) {
			throw new ReglaNegocioException("La fecha del reembolso debe estar entre la del gasto y hoy");
		}
		gastos.marcarReembolsado(idGasto, dia);
		return buscar(idGasto);
	}

	public void eliminar(int idGasto) {
		Gasto g = buscar(idGasto);
		if (g.reembolsadoEn() != null) {
			throw new ReglaNegocioException("Un gasto ya reembolsado no se elimina: es parte de la cuenta del cliente");
		}
		gastos.eliminar(idGasto);
	}

	/** Ordenada de mayor a menor deuda; diasSinPagar desde el último pago válido. */
	@Transactional(readOnly = true)
	public List<CarteraCliente> cartera() {
		LocalDate hoy = LocalDate.now(reloj);
		return gastos.cartera().stream()
				.map(c -> new CarteraCliente(c.idCliente(), c.cliente(), c.acuerdos(), c.pactado(), c.pagado(),
						c.saldoHonorarios(), c.gastosPorReembolsar(), c.totalPorCobrar(), c.ultimoPago(),
						c.ultimoPago() == null ? null : ChronoUnit.DAYS.between(c.ultimoPago(), hoy)))
				.toList();
	}

	private Gasto buscar(int idGasto) {
		return gastos.buscar(idGasto).orElseThrow(() -> new RecursoNoEncontradoException("No existe el gasto " + idGasto));
	}

}
