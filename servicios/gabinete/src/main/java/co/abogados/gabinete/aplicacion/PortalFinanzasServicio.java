package co.abogados.gabinete.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.modelo.Gasto;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.Pago;

/**
 * Lo que el cliente ve de sus cuentas en el portal: sus acuerdos de honorarios con lo pagado y el saldo, los pagos
 * vigentes (los anulados no se muestran: fueron errores de registro) y los gastos que la firma adelantó y debe
 * reembolsar. Nunca datos de otro cliente: idCliente sale del token.
 */
@Service
public class PortalFinanzasServicio {

	public record Acuerdo(int idHonorario, int idProceso, String formaCobro, BigDecimal valorFijo, BigDecimal porcentaje,
			BigDecimal total, BigDecimal pagado, BigDecimal saldo, String estado, List<PagoCliente> pagos) {
	}

	public record PagoCliente(LocalDate fecha, BigDecimal valor, String medio, String referencia) {
	}

	public record GastoCliente(int idProceso, LocalDate fecha, String concepto, BigDecimal valor) {
	}

	public record Resumen(List<Acuerdo> acuerdos, List<GastoCliente> gastosPorReembolsar, BigDecimal saldoHonorarios,
			BigDecimal totalGastosPorReembolsar) {
	}

	private final HonorarioServicio honorarios;
	private final GastoServicio gastos;

	public PortalFinanzasServicio(HonorarioServicio honorarios, GastoServicio gastos) {
		this.honorarios = honorarios;
		this.gastos = gastos;
	}

	@Transactional(readOnly = true)
	public Resumen deCliente(int idCliente) {
		List<Acuerdo> acuerdos = honorarios.listar(idCliente, null, null).stream()
				.filter(h -> h.idCliente() == idCliente)
				.map(this::acuerdo).toList();
		List<GastoCliente> porReembolsar = gastos.listar(null, idCliente).stream()
				.filter(g -> g.idCliente() == idCliente && g.porReembolsar())
				.map(g -> new GastoCliente(g.idProceso(), g.fecha(), g.concepto(), g.valor())).toList();
		BigDecimal saldo = acuerdos.stream().map(Acuerdo::saldo).filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal totalGastos = porReembolsar.stream().map(GastoCliente::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new Resumen(acuerdos, porReembolsar, saldo, totalGastos);
	}

	private Acuerdo acuerdo(HonorarioVista h) {
		List<PagoCliente> pagos = honorarios.pagos(h.idHonorario()).stream().filter(p -> !p.anulado())
				.map(PortalFinanzasServicio::pago).toList();
		BigDecimal total = h.saldo() == null ? null : h.pagado().add(h.saldo());
		return new Acuerdo(h.idHonorario(), h.idProceso(), h.formaCobro(), h.valorFijo(), h.porcentaje(), total,
				h.pagado(), h.saldo(), h.estado(), pagos);
	}

	private static PagoCliente pago(Pago p) {
		return new PagoCliente(p.fecha(), p.valor(), p.medio().name(), p.referencia());
	}

}
