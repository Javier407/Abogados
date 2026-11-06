package co.abogados.gabinete.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.abogados.gabinete.aplicacion.PortalFinanzasServicio.Acuerdo;
import co.abogados.gabinete.aplicacion.PortalFinanzasServicio.Resumen;
import co.abogados.gabinete.dominio.modelo.Gasto;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.MedioPago;
import co.abogados.gabinete.dominio.modelo.Pago;

/** El portal muestra al cliente solo lo suyo: acuerdos con saldo, pagos válidos y gastos por reembolsar. */
class PortalFinanzasServicioTest {

	static final LocalDate PACTO = LocalDate.of(2026, 10, 1);

	HonorarioServicio honorarios = mock(HonorarioServicio.class);
	GastoServicio gastos = mock(GastoServicio.class);
	PortalFinanzasServicio servicio = new PortalFinanzasServicio(honorarios, gastos);

	static HonorarioVista acuerdo(int id, int cliente, String forma, String pagado, String saldo) {
		return new HonorarioVista(id, 30 + id, cliente, "Cliente " + cliente, null, forma, new BigDecimal("8000000"),
				null, null, PACTO, "VIGENTE", new BigDecimal(pagado), saldo == null ? null : new BigDecimal(saldo), 1);
	}

	static Gasto gasto(int cliente, String valor, boolean reembolsable, LocalDate reembolsado) {
		return new Gasto(null, 34, cliente, LocalDate.of(2026, 10, 5), "Copias", new BigDecimal(valor),
				Gasto.PagadoPor.FIRMA, reembolsable, reembolsado, null, 1L);
	}

	@Test
	void sumaLosSaldosConocidosYOcultaLosPagosAnulados() {
		given(honorarios.listar(12, null, null)).willReturn(List.of(
				acuerdo(1, 12, "FIJO", "3000000", "5000000"),
				acuerdo(2, 12, "MENSUAL", "900000", null),
				acuerdo(3, 99, "FIJO", "0", "8000000")));
		given(honorarios.pagos(1)).willReturn(List.of(
				new Pago(10, 1, LocalDate.of(2026, 10, 2), new BigDecimal("3000000"), MedioPago.TRANSFERENCIA, "TRX-1"),
				new Pago(11, 1, LocalDate.of(2026, 10, 3), new BigDecimal("3000000"), MedioPago.TRANSFERENCIA, "TRX-1",
						Instant.parse("2026-10-04T15:00:00Z"), 79123456L, "Pago duplicado")));
		given(honorarios.pagos(2)).willReturn(List.of(
				new Pago(12, 2, LocalDate.of(2026, 10, 5), new BigDecimal("900000"), MedioPago.EFECTIVO, null)));
		given(gastos.listar(null, 12)).willReturn(List.of());

		Resumen r = servicio.deCliente(12);

		assertThat(r.acuerdos()).extracting(Acuerdo::idHonorario).containsExactly(1, 2);
		Acuerdo fijo = r.acuerdos().get(0);
		assertThat(fijo.total()).isEqualByComparingTo("8000000");
		assertThat(fijo.pagos()).singleElement().satisfies(p -> {
			assertThat(p.medio()).isEqualTo("TRANSFERENCIA");
			assertThat(p.referencia()).isEqualTo("TRX-1");
		});
		Acuerdo mensual = r.acuerdos().get(1);
		assertThat(mensual.total()).isNull();
		assertThat(mensual.saldo()).isNull();
		assertThat(mensual.pagos()).hasSize(1);
		assertThat(r.saldoHonorarios()).isEqualByComparingTo("5000000");
		assertThat(r.totalGastosPorReembolsar()).isEqualByComparingTo("0");
	}

	@Test
	void soloCuentaLosGastosDelClientePendientesDeReembolso() {
		given(honorarios.listar(12, null, null)).willReturn(List.of());
		given(gastos.listar(null, 12)).willReturn(List.of(
				gasto(12, "45000", true, null),
				gasto(12, "120000", true, null),
				gasto(12, "80000", true, LocalDate.of(2026, 10, 10)),
				gasto(12, "300000", false, null),
				gasto(99, "999999", true, null)));

		Resumen r = servicio.deCliente(12);

		assertThat(r.gastosPorReembolsar()).extracting(PortalFinanzasServicio.GastoCliente::valor)
				.extracting(BigDecimal::toPlainString).containsExactly("45000", "120000");
		assertThat(r.totalGastosPorReembolsar()).isEqualByComparingTo("165000");
		assertThat(r.saldoHonorarios()).isEqualByComparingTo("0");
		assertThat(r.acuerdos()).isEmpty();
	}

}
