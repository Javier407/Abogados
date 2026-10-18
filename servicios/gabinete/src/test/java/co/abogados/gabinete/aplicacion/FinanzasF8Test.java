package co.abogados.gabinete.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.CarteraCliente;
import co.abogados.gabinete.dominio.modelo.FormaCobro;
import co.abogados.gabinete.dominio.modelo.Gasto;
import co.abogados.gabinete.dominio.modelo.Honorario;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.Liquidacion;
import co.abogados.gabinete.dominio.puertos.ConsultaProcesos;
import co.abogados.gabinete.dominio.puertos.GastoRepositorio;

/** F8: anulación de pagos, liquidación de la cuota litis y gastos procesales. Hoy es 16/10/2026. */
class FinanzasF8Test {

	static final LocalDate HOY = HonorarioServicioTest.HOY;
	static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneId.of("America/Bogota"));

	HonorarioServicioTest base;

	@BeforeEach
	void preparar() {
		base = new HonorarioServicioTest();
		base.preparar();
	}

	static Honorario cuotaLitis(String porcentaje) {
		return new Honorario(null, 34, 1, null, FormaCobro.CUOTA_LITIS, null, new BigDecimal(porcentaje), null,
				LocalDate.of(2026, 10, 1), null);
	}

	// ----------------------------------------------------------------- anulación de pagos

	@Test
	void anularUnPagoLoDejaEnElHistorialPeroNoCuentaYReabreElAcuerdo() {
		int id = base.servicio.crear(HonorarioServicioTest.fijo(34, 1, "1000000")).idHonorario();
		base.servicio.registrarPago(id, HonorarioServicioTest.pago("1000000", HOY));
		assertThat(base.servicio.obtener(id).estado()).isEqualTo("CUMPLIDO");
		int idPago = base.servicio.pagos(id).get(0).idPago();

		HonorarioVista h = base.servicio.anularPago(id, idPago, "Se registró dos veces la misma transferencia", 79123456L);

		assertThat(h.estado()).isEqualTo("VIGENTE");
		assertThat(h.pagado()).isEqualByComparingTo("0");
		assertThat(base.servicio.pagos(id)).singleElement().satisfies(p -> {
			assertThat(p.anulado()).isTrue();
			assertThat(p.anuladoPor()).isEqualTo(79123456L);
			assertThat(p.motivoAnulacion()).isEqualTo("Se registró dos veces la misma transferencia");
		});
	}

	@Test
	void reglasDeLaAnulacion() {
		int id = base.servicio.crear(HonorarioServicioTest.fijo(34, 1, "1000000")).idHonorario();
		base.servicio.registrarPago(id, HonorarioServicioTest.pago("500000", HOY));
		int idPago = base.servicio.pagos(id).get(0).idPago();

		assertThatThrownBy(() -> base.servicio.anularPago(id, idPago, "  no ", 1L)).isInstanceOf(ReglaNegocioException.class);
		base.servicio.anularPago(id, idPago, "Valor equivocado", 1L);
		assertThatThrownBy(() -> base.servicio.anularPago(id, idPago, "Otra vez", 1L)).isInstanceOf(ConflictoException.class);
		assertThatThrownBy(() -> base.servicio.anularPago(id, 99, "No existe", 1L))
				.hasMessageContaining("No existe el pago");
	}

	// ----------------------------------------------------------------- liquidación

	@Test
	void liquidaLaCuotaLitisYDesdeEntoncesElSaldoTieneTope() {
		int id = base.servicio.crear(cuotaLitis("25")).idHonorario();

		base.servicio.liquidar(id, new BigDecimal("80000000"), HOY);
		assertThat(base.repo.liquidados.get(id)).isEqualByComparingTo("20000000");

		assertThatThrownBy(() -> base.servicio.registrarPago(id, HonorarioServicioTest.pago("20000001", HOY)))
				.hasMessageContaining("supera el saldo");
		HonorarioVista h = base.servicio.registrarPago(id, HonorarioServicioTest.pago("20000000", HOY));
		assertThat(h.estado()).isEqualTo("CUMPLIDO");
	}

	@Test
	void elMixtoSumaElFijoYSeRedondeaAlPeso() {
		Honorario mixto = new Honorario(null, 34, 1, null, FormaCobro.MIXTO, new BigDecimal("3000000"),
				new BigDecimal("12.5"), null, LocalDate.of(2026, 10, 1), null);
		assertThat(Liquidacion.calcular(mixto, new BigDecimal("10000003"))).isEqualByComparingTo("4250000");   // 1.250.000,375 → 1.250.000
		assertThatThrownBy(() -> Liquidacion.calcular(HonorarioServicioTest.fijo(34, 1, "1000"), BigDecimal.TEN))
				.hasMessageContaining("Solo se liquidan");
		assertThatThrownBy(() -> Liquidacion.calcular(mixto, new BigDecimal("-1"))).hasMessageContaining("negativo");
	}

	@Test
	void siYaSePagoLoLiquidadoElAcuerdoQuedaCumplido() {
		int id = base.servicio.crear(cuotaLitis("10")).idHonorario();
		base.servicio.registrarPago(id, HonorarioServicioTest.pago("2000000", HOY));    // anticipo sin tope
		base.servicio.liquidar(id, new BigDecimal("15000000"), HOY);                  // 10 % = 1.500.000
		assertThat(base.servicio.obtener(id).estado()).isEqualTo("CUMPLIDO");
		assertThatThrownBy(() -> base.servicio.liquidar(id, BigDecimal.ONE, HOY)).hasMessageContaining("CUMPLIDO");
		int otro = base.servicio.crear(new Honorario(null, 35, 2, null, FormaCobro.CUOTA_LITIS, null, BigDecimal.TEN, null,
				LocalDate.of(2026, 10, 1), null)).idHonorario();
		assertThatThrownBy(() -> base.servicio.liquidar(otro, BigDecimal.ONE, HOY.plusDays(1))).hasMessageContaining("futura");
	}

	// ----------------------------------------------------------------- gastos

	static class GastosEnMemoria implements GastoRepositorio {
		final Map<Integer, Gasto> datos = new HashMap<>();

		public List<Gasto> listar(Integer p, Integer c) {
			return datos.values().stream().filter(g -> (p == null || g.idProceso() == p) && (c == null || g.idCliente() == c)).toList();
		}
		public Optional<Gasto> buscar(int id) { return Optional.ofNullable(datos.get(id)); }
		public int crear(Gasto g) {
			int id = datos.size() + 1;
			datos.put(id, new Gasto(id, g.idProceso(), g.idCliente(), g.fecha(), g.concepto(), g.valor(), g.pagadoPor(),
					g.reembolsable(), g.reembolsadoEn(), g.soporteUrl(), g.registradoPor()));
			return id;
		}
		public void marcarReembolsado(int id, LocalDate fecha) {
			Gasto g = datos.get(id);
			datos.put(id, new Gasto(id, g.idProceso(), g.idCliente(), g.fecha(), g.concepto(), g.valor(), g.pagadoPor(),
					g.reembolsable(), fecha, g.soporteUrl(), g.registradoPor()));
		}
		public void eliminar(int id) { datos.remove(id); }
		public List<CarteraCliente> cartera() {
			return List.of(new CarteraCliente(1, "Ana", 1, new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1000"),
					new BigDecimal("50"), new BigDecimal("1050"), LocalDate.of(2026, 9, 16), null));
		}
	}

	static final ConsultaProcesos PROCESOS = new ConsultaProcesos() {
		public int procesosDelCliente(int idCliente) { return 0; }
		public Optional<Integer> clienteDelProceso(int idProceso) { return idProceso == 34 ? Optional.of(1) : Optional.empty(); }
	};

	static Gasto gasto(boolean reembolsable, Gasto.PagadoPor quien, LocalDate fecha) {
		return new Gasto(null, 34, 0, fecha, "Copias auténticas del expediente", new BigDecimal("48000"), quien, reembolsable,
				null, null, null);
	}

	@Test
	void unGastoTomaElClienteDelProcesoYSeReembolsaUnaVez() {
		GastosEnMemoria repo = new GastosEnMemoria();
		GastoServicio gastos = new GastoServicio(repo, PROCESOS, RELOJ);

		Gasto g = gastos.registrar(gasto(true, Gasto.PagadoPor.FIRMA, HOY.minusDays(3)), 52345678L);
		assertThat(g.idCliente()).isEqualTo(1);
		assertThat(g.registradoPor()).isEqualTo(52345678L);
		assertThat(g.porReembolsar()).isTrue();

		Gasto r = gastos.marcarReembolsado(g.idGasto(), HOY);
		assertThat(r.porReembolsar()).isFalse();
		assertThatThrownBy(() -> gastos.marcarReembolsado(g.idGasto(), HOY)).isInstanceOf(ConflictoException.class);
		assertThatThrownBy(() -> gastos.eliminar(g.idGasto())).hasMessageContaining("reembolsado");
	}

	@Test
	void reglasDeLosGastos() {
		GastoServicio gastos = new GastoServicio(new GastosEnMemoria(), PROCESOS, RELOJ);
		assertThatThrownBy(() -> gasto(true, Gasto.PagadoPor.CLIENTE, HOY)).hasMessageContaining("adelantó la firma");
		assertThatThrownBy(() -> gastos.registrar(gasto(false, Gasto.PagadoPor.FIRMA, HOY.plusDays(1)), null))
				.hasMessageContaining("futura");
		assertThatThrownBy(() -> gastos.registrar(new Gasto(null, 99, 0, HOY, "Póliza", BigDecimal.TEN, Gasto.PagadoPor.FIRMA,
				false, null, null, null), null)).hasMessageContaining("No existe el proceso");
		Gasto noReembolsable = gastos.registrar(gasto(false, Gasto.PagadoPor.FIRMA, HOY), null);
		assertThatThrownBy(() -> gastos.marcarReembolsado(noReembolsable.idGasto(), HOY)).hasMessageContaining("no es reembolsable");
	}

	@Test
	void laCarteraCalculaLosDiasSinPagar() {
		assertThat(new GastoServicio(new GastosEnMemoria(), PROCESOS, RELOJ).cartera())
				.singleElement().satisfies(c -> assertThat(c.diasSinPagar()).isEqualTo(30L));
	}

}
