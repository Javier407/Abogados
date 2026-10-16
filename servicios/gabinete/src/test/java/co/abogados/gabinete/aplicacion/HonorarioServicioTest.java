package co.abogados.gabinete.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.EstadoHonorario;
import co.abogados.gabinete.dominio.modelo.FormaCobro;
import co.abogados.gabinete.dominio.modelo.Honorario;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.MedioPago;
import co.abogados.gabinete.dominio.modelo.Pago;
import co.abogados.gabinete.dominio.modelo.Smmlv;
import co.abogados.gabinete.dominio.modelo.TarifaVista;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.modelo.TipoPersona;
import co.abogados.gabinete.dominio.puertos.ConsultaProcesos;
import co.abogados.gabinete.dominio.puertos.HonorarioRepositorio;
import co.abogados.gabinete.dominio.puertos.TarifaRepositorio;

/** Honorarios y pagos con dobles en memoria y un reloj fijo en el 16 de octubre de 2026. */
class HonorarioServicioTest {

	static final LocalDate HOY = LocalDate.of(2026, 10, 16);

	HonorariosEnMemoria repo;
	HonorarioServicio servicio;

	@BeforeEach
	void preparar() {
		repo = new HonorariosEnMemoria();
		ServiciosTest.ClientesEnMemoria clientes = new ServiciosTest.ClientesEnMemoria();
		clientes.guardar(new Cliente(null, TipoPersona.NATURAL, TipoDocumento.CC, "52678123", "Ana", null, null, null,
				"Bogotá", (short) 1, HOY));                                   // cliente 1
		clientes.guardar(new Cliente(null, TipoPersona.NATURAL, TipoDocumento.CC, "19456789", "Jorge", null, null,
				null, "Bogotá", (short) 1, HOY));                             // cliente 2
		ConsultaProcesos procesos = new ConsultaProcesos() {
			public int procesosDelCliente(int idCliente) { return 0; }
			public Optional<Integer> clienteDelProceso(int idProceso) {
				return Map.of(34, 1, 35, 2).entrySet().stream().filter(e -> e.getKey() == idProceso)
						.map(Map.Entry::getValue).findFirst();
			}
		};
		TarifaRepositorio tarifas = new TarifaRepositorio() {
			public List<TarifaVista> listar(int anio, BigDecimal smmlv) { return List.of(); }
			public boolean existe(short id) { return id == 9; }
			public Optional<Smmlv> smmlv(int anio) { return Optional.empty(); }
		};
		Clock reloj = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneId.of("America/Bogota"));
		servicio = new HonorarioServicio(repo, clientes, tarifas, procesos, reloj);
	}

	static Honorario fijo(int proceso, int cliente, String valor) {
		return new Honorario(null, proceso, cliente, null, FormaCobro.FIJO, new BigDecimal(valor), null, null,
				LocalDate.of(2026, 10, 1), null);
	}

	static Pago pago(String valor, LocalDate fecha) {
		return new Pago(null, 0, fecha, new BigDecimal(valor), MedioPago.TRANSFERENCIA, "TRX-1");
	}

	@Test
	void reglasDeLaFormaDeCobro() {
		assertThatThrownBy(() -> new Honorario(null, 34, 1, null, FormaCobro.FIJO, null, null, null, HOY, null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("valor fijo");
		assertThatThrownBy(() -> new Honorario(null, 34, 1, null, FormaCobro.CUOTA_LITIS, null, null, null, HOY, null))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("porcentaje");
		assertThatThrownBy(() -> new Honorario(null, 34, 1, null, FormaCobro.CUOTA_LITIS, BigDecimal.TEN,
				new BigDecimal("30"), null, HOY, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("no lleva valor fijo");
		assertThatThrownBy(() -> new Honorario(null, 34, 1, null, FormaCobro.CUOTA_LITIS, null, new BigDecimal("60"),
				null, HOY, null)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("50");
		Honorario mixto = new Honorario(null, 34, 1, null, FormaCobro.MIXTO, new BigDecimal("3500000"),
				new BigDecimal("15"), null, HOY, null);
		assertThat(mixto.estado()).isEqualTo(EstadoHonorario.VIGENTE);
	}

	@Test
	void creaElAcuerdoSoloSiElProcesoEsDelCliente() {
		HonorarioVista h = servicio.crear(fijo(34, 1, "5000000"));
		assertThat(h.saldo()).isEqualByComparingTo("5000000");

		assertThatThrownBy(() -> servicio.crear(fijo(35, 1, "1000"))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("otro cliente");
		assertThatThrownBy(() -> servicio.crear(fijo(99, 1, "1000"))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("No existe el proceso 99");
		assertThatThrownBy(() -> servicio.crear(fijo(34, 1, "1000"))).isInstanceOf(ConflictoException.class);
	}

	@Test
	void laTarifaReferenciadaDebeExistir() {
		Honorario conTarifa = new Honorario(null, 34, 1, (short) 4, FormaCobro.FIJO, BigDecimal.TEN, null, null,
				LocalDate.of(2026, 10, 1), null);
		assertThatThrownBy(() -> servicio.crear(conTarifa)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("tarifa 4");
	}

	@Test
	void losPagosDescuentanElSaldoYAlCompletarloElAcuerdoQuedaCumplido() {
		int id = servicio.crear(fijo(34, 1, "5000000")).idHonorario();

		HonorarioVista tras1 = servicio.registrarPago(id, pago("2000000", LocalDate.of(2026, 10, 5)));
		assertThat(tras1.saldo()).isEqualByComparingTo("3000000");
		assertThat(tras1.estado()).isEqualTo("VIGENTE");

		assertThatThrownBy(() -> servicio.registrarPago(id, pago("3000001", HOY)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("supera el saldo");

		HonorarioVista tras2 = servicio.registrarPago(id, pago("3000000", HOY));
		assertThat(tras2.estado()).isEqualTo("CUMPLIDO");
		assertThat(tras2.pagos()).isEqualTo(2);
		assertThatThrownBy(() -> servicio.registrarPago(id, pago("1", HOY)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("CUMPLIDO");
	}

	@Test
	void fechasDelPago() {
		int id = servicio.crear(fijo(34, 1, "5000000")).idHonorario();
		assertThatThrownBy(() -> servicio.registrarPago(id, pago("1000", HOY.plusDays(1))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("futura");
		assertThatThrownBy(() -> servicio.registrarPago(id, pago("1000", LocalDate.of(2026, 9, 30))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("anterior al acuerdo");
	}

	@Test
	void unaMensualidadNoTieneTope() {
		int id = servicio.crear(new Honorario(null, 34, 1, null, FormaCobro.MENSUAL, new BigDecimal("875453"), null,
				null, LocalDate.of(2026, 10, 1), null)).idHonorario();

		servicio.registrarPago(id, pago("875453", LocalDate.of(2026, 10, 2)));
		HonorarioVista h = servicio.registrarPago(id, pago("875453", HOY));

		assertThat(h.pagado()).isEqualByComparingTo("1750906");
		assertThat(h.estado()).isEqualTo("VIGENTE");
	}

	@Test
	void unAcuerdoCanceladoNoRecibePagos() {
		int id = servicio.crear(fijo(34, 1, "5000000")).idHonorario();
		servicio.cambiarEstado(id, EstadoHonorario.CANCELADO);
		assertThatThrownBy(() -> servicio.registrarPago(id, pago("1000", HOY)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("CANCELADO");
	}

	// ----------------------------------------------------------------- doble

	static class HonorariosEnMemoria implements HonorarioRepositorio {
		final Map<Integer, Honorario> acuerdos = new HashMap<>();
		final List<Pago> pagos = new ArrayList<>();

		public List<HonorarioVista> listar(Integer c, Integer p, String e) {
			return acuerdos.keySet().stream().map(id -> buscar(id).orElseThrow()).toList();
		}

		public Optional<HonorarioVista> buscar(int id) {
			Honorario h = acuerdos.get(id);
			if (h == null) return Optional.empty();
			BigDecimal pagado = totalPagado(id);
			BigDecimal saldo = h.formaCobro() == FormaCobro.MENSUAL || h.valorFijo() == null ? null
					: h.valorFijo().subtract(pagado);
			return Optional.of(new HonorarioVista(id, h.idProceso(), h.idCliente(), "Cliente", h.idTarifa(),
					h.formaCobro().name(), h.valorFijo(), h.porcentaje(), h.valorEstimado(), h.fechaPacto(),
					h.estado().name(), pagado, saldo, (int) pagos.stream().filter(p -> p.idHonorario() == id).count()));
		}

		public Optional<Honorario> acuerdo(int id) { return Optional.ofNullable(acuerdos.get(id)); }
		public boolean existeParaProceso(int idProceso) {
			return acuerdos.values().stream().anyMatch(h -> h.idProceso() == idProceso);
		}
		public int crear(Honorario h) {
			int id = acuerdos.size() + 1;
			acuerdos.put(id, new Honorario(id, h.idProceso(), h.idCliente(), h.idTarifa(), h.formaCobro(),
					h.valorFijo(), h.porcentaje(), h.valorEstimado(), h.fechaPacto(), h.estado()));
			return id;
		}
		public void cambiarEstado(int id, String estado) {
			acuerdos.put(id, acuerdos.get(id).conEstado(EstadoHonorario.valueOf(estado)));
		}
		public List<Pago> pagos(int id) { return pagos.stream().filter(p -> p.idHonorario() == id).toList(); }
		public BigDecimal totalPagado(int id) {
			return pagos(id).stream().map(Pago::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
		}
		public int registrarPago(Pago p) {
			pagos.add(p);
			return pagos.size();
		}
	}

}
