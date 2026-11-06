package co.abogados.gabinete.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.ConfigFacturacion;
import co.abogados.gabinete.dominio.modelo.Cufe;
import co.abogados.gabinete.dominio.modelo.Factura;
import co.abogados.gabinete.dominio.modelo.Factura.TipoLinea;
import co.abogados.gabinete.dominio.modelo.Gasto;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.modelo.TipoPersona;
import co.abogados.gabinete.dominio.puertos.ClienteRepositorio;
import co.abogados.gabinete.dominio.puertos.FacturacionRepositorio;
import co.abogados.gabinete.dominio.puertos.GastoRepositorio;
import co.abogados.gabinete.dominio.puertos.HonorarioRepositorio;
import co.abogados.gabinete.dominio.puertos.ProveedorFacturacion;
import co.abogados.gabinete.infraestructura.facturacion.ProveedorSimulado;

/** F14: facturación electrónica con el proveedor simulado y la base en memoria. */
class FacturacionServicioTest {

	static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC);
	static final int ANDINA = 1, CARLOS = 6, OTRO = 9;
	static final long USUARIO = 79876543L;

	FacturasEnMemoria repo;
	ClienteRepositorio clientes = mock(ClienteRepositorio.class);
	HonorarioRepositorio honorarios = mock(HonorarioRepositorio.class);
	GastoRepositorio gastos = mock(GastoRepositorio.class);
	FacturacionServicio servicio;

	static Gasto gasto(int id, int cliente, boolean reembolsable, LocalDate reembolsado) {
		return new Gasto(id, 7, cliente, LocalDate.of(2026, 10, 1), "Copias del expediente", new BigDecimal("85000"),
				Gasto.PagadoPor.FIRMA, reembolsable, reembolsado, null, null);
	}

	static HonorarioVista honorario(int id, int cliente, String forma, String fijo, String pagado, String saldo) {
		return new HonorarioVista(id, 7, cliente, "Cliente", null, forma, fijo == null ? null : new BigDecimal(fijo), null,
				null, LocalDate.of(2026, 1, 10), "VIGENTE", new BigDecimal(pagado), saldo == null ? null : new BigDecimal(saldo), 1);
	}

	@BeforeEach
	void preparar() {
		repo = new FacturasEnMemoria();
		given(clientes.buscar(ANDINA)).willReturn(Optional.of(new Cliente(ANDINA, TipoPersona.JURIDICA, TipoDocumento.NIT,
				"900456789", "Constructora Andina SAS", "pagos@andina.co", null, "Calle 80 # 10-20", "Bogotá", (short) 1, null)));
		given(clientes.buscar(CARLOS)).willReturn(Optional.of(new Cliente(CARLOS, TipoPersona.NATURAL, TipoDocumento.CC,
				"1032456781", "Carlos Méndez", null, null, null, "Bogotá", (short) 1, null)));
		given(honorarios.listar(eq(ANDINA), any(), eq("VIGENTE"))).willReturn(List.of(
				honorario(3, ANDINA, "FIJO", "10000000", "4000000", "6000000"),
				honorario(4, ANDINA, "MENSUAL", "1500000", "0", null),
				honorario(5, ANDINA, "CUOTA_LITIS", null, "0", null)));
		given(honorarios.buscar(3)).willReturn(Optional.of(honorario(3, ANDINA, "FIJO", "10000000", "4000000", "6000000")));
		given(honorarios.buscar(8)).willReturn(Optional.of(honorario(8, OTRO, "FIJO", "1", "0", "1")));
		given(gastos.listar(null, ANDINA)).willReturn(List.of(gasto(11, ANDINA, true, null), gasto(12, ANDINA, false, null),
				gasto(13, ANDINA, true, LocalDate.of(2026, 10, 5))));
		given(gastos.buscar(11)).willReturn(Optional.of(gasto(11, ANDINA, true, null)));
		given(gastos.buscar(12)).willReturn(Optional.of(gasto(12, ANDINA, false, null)));
		armar(new ProveedorSimulado());
	}

	void armar(ProveedorFacturacion proveedor) {
		servicio = new FacturacionServicio(repo, clientes, honorarios, gastos, proveedor, RELOJ);
	}

	static FacturacionServicio.Solicitud solicitud(int cliente, FacturacionServicio.LineaSolicitud... lineas) {
		return new FacturacionServicio.Solicitud(cliente, 7, Factura.FormaPago.CREDITO, null, null, List.of(lineas));
	}

	static FacturacionServicio.LineaSolicitud servicioDe(String valor, Integer honorario) {
		return new FacturacionServicio.LineaSolicitud(TipoLinea.SERVICIO, "Honorarios octubre", BigDecimal.ONE,
				new BigDecimal(valor), honorario, null);
	}

	static FacturacionServicio.LineaSolicitud reembolso(int gasto) {
		return new FacturacionServicio.LineaSolicitud(TipoLinea.REEMBOLSO, "lo que diga", BigDecimal.TEN,
				new BigDecimal("1"), null, gasto);
	}

	@Test
	void sugiereElSaldoPorFacturarLaCuotaMensualYLosGastosReembolsables() {
		repo.facturado.put(3, new BigDecimal("2000000"));

		List<FacturacionServicio.Pendiente> p = servicio.pendientes(ANDINA);

		assertThat(p).extracting(FacturacionServicio.Pendiente::valor).containsExactly(
				new BigDecimal("8000000"), new BigDecimal("1500000"), new BigDecimal("85000"));
		assertThat(p.get(0).descripcion()).isEqualTo("Honorarios del proceso 7 (fijo)");
		assertThat(p.get(2).tipo()).isEqualTo(TipoLinea.REEMBOLSO);
		assertThat(p.get(2).idGasto()).isEqualTo(11);
		repo.gastosCobrados.add(11);
		assertThat(servicio.pendientes(ANDINA)).hasSize(2);
	}

	@Test
	void emiteConIvaRetencionYReembolsoSinIvaYNumeraSinSaltos() {
		FacturacionServicio.Detalle d = servicio.emitir(solicitud(ANDINA, servicioDe("6000000", 3), reembolso(11)), USUARIO);
		Factura f = d.factura();

		assertThat(f.numeroCompleto()).isEqualTo("SETP990000000");
		assertThat(f.estado()).isEqualTo(Factura.Estado.ACEPTADA);
		assertThat(f.cufe()).hasSize(96).matches("[0-9a-f]+");
		Factura.Totales t = f.totales();
		assertThat(t.subtotal()).isEqualByComparingTo("6000000");
		assertThat(t.iva()).isEqualByComparingTo("1140000");
		assertThat(t.reembolsos()).isEqualByComparingTo("85000");
		assertThat(t.total()).isEqualByComparingTo("7225000");
		assertThat(t.retencionPct()).isEqualByComparingTo("11");           // persona jurídica: retención sugerida
		assertThat(t.retencion()).isEqualByComparingTo("660000");           // solo sobre los servicios
		assertThat(t.neto()).isEqualByComparingTo("6565000");
		assertThat(f.lineas().get(1).descripcion()).isEqualTo("Reembolso: Copias del expediente");   // del gasto
		assertThat(f.lineas().get(1).valor()).isEqualByComparingTo("85000");
		assertThat(f.vence()).isEqualTo(LocalDate.of(2026, 11, 21));         // 30 días de crédito
		assertThat(f.adquirente().documento()).isEqualTo("NIT 900456789");
		assertThat(repo.gastosCobrados).containsExactly(11);
		assertThat(d.textoQr()).contains("NumFac: SETP990000000").contains("ValTolFac: 7225000.00")
				.contains("NitFac: 901234567").contains("catalogo-vpfe-hab.dian.gov.co/document/searchqr?documentkey=" + f.cufe());

		Factura segunda = servicio.emitir(solicitud(ANDINA, servicioDe("100", null)), USUARIO).factura();
		assertThat(segunda.numero()).isEqualTo(990000001L);
		assertThat(repo.config.siguiente()).isEqualTo(990000002L);
	}

	@Test
	void elCufeEsElSha384DeLosCamposDelAnexoTecnico() {
		OffsetDateTime emitida = OffsetDateTime.parse("2026-10-22T10:00:00-05:00");
		String cufe = Cufe.calcular("SETP990000000", emitida, new BigDecimal("100"), new BigDecimal("19"),
				new BigDecimal("119"), "901234567-8", "900456789", "clave", "PRUEBAS");
		String esperado = sha384("SETP9900000002026-10-2210:00:00-05:00100.000119.00040.00030.00119.00901234567900456789clave2");
		assertThat(cufe).isEqualTo(esperado);
		assertThat(Cufe.calcular("SETP990000000", emitida.withOffsetSameInstant(ZoneOffset.UTC), new BigDecimal("100"),
				new BigDecimal("19"), new BigDecimal("119"), "901234567-8", "900456789", "clave", "PRUEBAS")).isEqualTo(esperado);
	}

	@Test
	void unGastoSoloSeCobraUnaVezYDebeSerUnReembolsoPendienteDelCliente() {
		servicio.emitir(solicitud(ANDINA, reembolso(11)), USUARIO);

		assertThatThrownBy(() -> servicio.emitir(solicitud(ANDINA, reembolso(11)), USUARIO))
				.isInstanceOf(ConflictoException.class).hasMessageContaining("ya está en una factura");
		assertThatThrownBy(() -> servicio.emitir(solicitud(ANDINA, reembolso(12)), USUARIO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es un reembolso pendiente");
		assertThatThrownBy(() -> servicio.emitir(solicitud(ANDINA, servicioDe("10", 8)), USUARIO))
				.hasMessageContaining("es de otro cliente");
		assertThat(repo.config.siguiente()).isEqualTo(990000001L);   // los intentos fallidos no gastan números
	}

	@Test
	void sinCorreoODireccionDelClienteNoSeFactura() {
		assertThatThrownBy(() -> servicio.emitir(solicitud(CARLOS, servicioDe("100", null)), USUARIO))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("correo y dirección");
		assertThatThrownBy(() -> servicio.emitir(solicitud(77, servicioDe("100", null)), USUARIO))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void laResolucionVencidaOAgotadaImpideFacturar() {
		repo.config = conRango(repo.config, 990000000, 990000000, 990000001, LocalDate.of(2026, 1, 1));
		assertThatThrownBy(() -> servicio.emitir(solicitud(ANDINA, servicioDe("100", null)), USUARIO))
				.hasMessageContaining("Se acabó el rango");
		repo.config = conRango(repo.config, 990000000, 995000000, 990000000, LocalDate.of(2026, 11, 1));
		assertThatThrownBy(() -> servicio.emitir(solicitud(ANDINA, servicioDe("100", null)), USUARIO))
				.hasMessageContaining("no está vigente");
		assertThat(servicio.estadoConfig().impedimento()).contains("no está vigente");
	}

	@Test
	void anularEmiteUnaNotaCreditoYLiberaLosGastos() {
		int id = servicio.emitir(solicitud(ANDINA, servicioDe("1000", null), reembolso(11)), USUARIO).factura().idFactura();

		Factura f = servicio.anular(id, "Valor equivocado", USUARIO).factura();

		assertThat(f.estado()).isEqualTo(Factura.Estado.ANULADA);
		assertThat(f.notaCredito().numeroCompleto()).isEqualTo("NC1");
		assertThat(f.notaCredito().valor()).isEqualByComparingTo(f.totales().total());
		assertThat(f.notaCredito().cude()).hasSize(96);
		assertThat(repo.gastosCobrados).isEmpty();
		assertThat(repo.config.siguienteNota()).isEqualTo(2);
		assertThatThrownBy(() -> servicio.anular(id, "otra vez", USUARIO)).isInstanceOf(ConflictoException.class);
		assertThatThrownBy(() -> servicio.anular(id, " ", USUARIO)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> servicio.anular(99, "x", USUARIO)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void siElProveedorLaRechazaQuedaRechazadaYNoSePuedeAnular() {
		armar(new ProveedorFacturacion() {
			public String nombre() { return "SIMULADO"; }
			public Envio enviarFactura(Factura f, FacturacionRepositorio.Emisor e, ConfigFacturacion c) {
				return new Envio(false, null, "Rechazada: NIT del adquirente inválido");
			}
			public Envio enviarNotaCredito(Factura.NotaCredito n, Factura f, FacturacionRepositorio.Emisor e, ConfigFacturacion c) {
				return new Envio(false, null, "no");
			}
		});
		Factura f = servicio.emitir(solicitud(ANDINA, reembolso(11)), USUARIO).factura();

		assertThat(f.estado()).isEqualTo(Factura.Estado.RECHAZADA);
		assertThat(f.respuesta()).contains("NIT del adquirente");
		assertThat(repo.gastosCobrados).isEmpty();                 // el gasto vuelve a quedar pendiente
		assertThatThrownBy(() -> servicio.anular(f.idFactura(), "x", USUARIO)).hasMessageContaining("aceptada");
	}

	@Test
	void laVistaPreviaCalculaSinGuardarYRespetaLaRetencionEscrita() {
		FacturacionServicio.VistaPrevia v = servicio.vistaPrevia(new FacturacionServicio.Solicitud(ANDINA, null, null,
				BigDecimal.ZERO, null, List.of(servicioDe("1000.555", null))));
		assertThat(v.retencionSugerida()).isEqualByComparingTo("11");
		assertThat(v.totales().retencion()).isEqualByComparingTo("0");
		assertThat(v.totales().subtotal()).isEqualByComparingTo("1000.56");
		assertThat(v.totales().iva()).isEqualByComparingTo("190.11");
		assertThat(repo.facturas).isEmpty();
		assertThatThrownBy(() -> servicio.vistaPrevia(solicitud(ANDINA))).hasMessageContaining("al menos una línea");
	}

	@Test
	void sinResponsabilidadDeIvaLosServiciosNoLlevanIva() {
		repo.config = new ConfigFacturacion(false, new BigDecimal("19"), new BigDecimal("11"), BigDecimal.ZERO, 0,
				"1876", "SETP", 1, 100, 1, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "NC", 1, "PRUEBAS",
				"SIMULADO", null, null);
		Factura f = servicio.emitir(new FacturacionServicio.Solicitud(ANDINA, null, Factura.FormaPago.CONTADO, null, null,
				List.of(servicioDe("1000", null))), USUARIO).factura();
		assertThat(f.totales().iva()).isEqualByComparingTo("0");
		assertThat(f.vence()).isEqualTo(LocalDate.of(2026, 10, 22));
	}

	@Test
	void cambiarElIvaQuitaLaValidacionDelContadorPeroCambiarElRangoNo() {
		servicio.validarConfig(USUARIO);
		assertThat(repo.config.validada()).isTrue();

		ConfigFacturacion c = repo.config;
		servicio.guardarConfig(conRango(c, c.rangoDesde(), c.rangoHasta() + 10, c.siguiente(), c.vigenteDesde()), USUARIO);
		assertThat(repo.config.validada()).isTrue();

		servicio.guardarConfig(new ConfigFacturacion(true, new BigDecimal("16"), c.retencionJuridica(), c.retencionNatural(),
				c.diasCredito(), c.resolucion(), c.prefijo(), c.rangoDesde(), c.rangoHasta(), c.siguiente(), c.vigenteDesde(),
				c.vigenteHasta(), c.prefijoNota(), c.siguienteNota(), c.ambiente(), c.proveedor(), null, null), USUARIO);
		assertThat(repo.config.validada()).isFalse();
		assertThat(repo.config.tarifaIva()).isEqualByComparingTo("16");
	}

	@Test
	void laConfiguracionRechazaValoresIncoherentes() {
		ConfigFacturacion c = repo.config;
		assertThatThrownBy(() -> servicio.guardarConfig(conRango(c, 10, 5, 10, c.vigenteDesde()), USUARIO))
				.hasMessageContaining("rango");
		repo.config = conRango(c, c.rangoDesde(), c.rangoHasta(), c.rangoDesde() + 5, c.vigenteDesde());
		assertThatThrownBy(() -> servicio.guardarConfig(conRango(c, c.rangoDesde(), c.rangoHasta(), c.rangoDesde() + 1,
				c.vigenteDesde()), USUARIO)).hasMessageContaining("no puede retroceder");
		ConfigFacturacion produccion = new ConfigFacturacion(true, c.tarifaIva(), c.retencionJuridica(), c.retencionNatural(),
				c.diasCredito(), c.resolucion(), c.prefijo(), c.rangoDesde(), c.rangoHasta(), c.rangoDesde() + 5,
				c.vigenteDesde(), c.vigenteHasta(), c.prefijoNota(), c.siguienteNota(), "PRODUCCION", "SIMULADO", null, null);
		assertThatThrownBy(() -> servicio.guardarConfig(produccion, USUARIO)).hasMessageContaining("proveedor tecnológico real");
		ConfigFacturacion otroProveedor = new ConfigFacturacion(true, c.tarifaIva(), c.retencionJuridica(), c.retencionNatural(),
				c.diasCredito(), c.resolucion(), c.prefijo(), c.rangoDesde(), c.rangoHasta(), c.rangoDesde() + 5,
				c.vigenteDesde(), c.vigenteHasta(), c.prefijoNota(), c.siguienteNota(), "PRODUCCION", "ALEGRA", null, null);
		assertThatThrownBy(() -> servicio.guardarConfig(otroProveedor, USUARIO)).hasMessageContaining("no está conectado");
		assertThatThrownBy(() -> servicio.guardarConfig(new ConfigFacturacion(true, new BigDecimal("120"), c.retencionJuridica(),
				c.retencionNatural(), 0, "x", "SETP", 1, 2, 1, c.vigenteDesde(), c.vigenteHasta(), "NC", 1, "PRUEBAS", "SIMULADO",
				null, null), USUARIO)).hasMessageContaining("0 a 100");
	}

	@Test
	void unaLineaMalHechaSeExplica() {
		assertThatThrownBy(() -> servicio.vistaPrevia(solicitud(ANDINA, new FacturacionServicio.LineaSolicitud(
				TipoLinea.SERVICIO, " ", BigDecimal.ONE, BigDecimal.TEN, null, null)))).hasMessageContaining("descripción");
		assertThatThrownBy(() -> servicio.vistaPrevia(solicitud(ANDINA, new FacturacionServicio.LineaSolicitud(
				TipoLinea.SERVICIO, "x", BigDecimal.ONE, BigDecimal.ZERO, null, null)))).hasMessageContaining("mayores que cero");
		assertThatThrownBy(() -> servicio.vistaPrevia(solicitud(ANDINA, new FacturacionServicio.LineaSolicitud(
				TipoLinea.REEMBOLSO, "x", BigDecimal.ONE, BigDecimal.TEN, null, null)))).hasMessageContaining("gasto registrado");
	}

	@Test
	void listaYDetalle() {
		servicio.emitir(solicitud(ANDINA, servicioDe("100", null)), USUARIO);
		assertThat(servicio.listar(ANDINA, null)).hasSize(1);
		assertThat(servicio.listar(null, Factura.Estado.ANULADA)).isEmpty();
		assertThat(servicio.detalle(1).emisor().razonSocial()).contains("Rodríguez");
	}

	static ConfigFacturacion conRango(ConfigFacturacion c, long desde, long hasta, long siguiente, LocalDate vigenteDesde) {
		return new ConfigFacturacion(c.responsableIva(), c.tarifaIva(), c.retencionJuridica(), c.retencionNatural(),
				c.diasCredito(), c.resolucion(), c.prefijo(), desde, hasta, siguiente, vigenteDesde, c.vigenteHasta(),
				c.prefijoNota(), c.siguienteNota(), c.ambiente(), c.proveedor(), c.validadaPor(), c.validadaEn());
	}

	static String sha384(String s) {
		try {
			return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-384")
					.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// ------------------------------------------------------------------ doble

	static class FacturasEnMemoria implements FacturacionRepositorio {
		ConfigFacturacion config = new ConfigFacturacion(true, new BigDecimal("19.00"), new BigDecimal("11.00"),
				BigDecimal.ZERO, 30, "18760000001", "SETP", 990000000, 995000000, 990000000, LocalDate.of(2026, 1, 1),
				LocalDate.of(2027, 12, 31), "NC", 1, "PRUEBAS", "SIMULADO", null, null);
		final Map<Integer, Factura> facturas = new LinkedHashMap<>();
		final Map<Integer, BigDecimal> facturado = new HashMap<>();
		final Set<Integer> gastosCobrados = new HashSet<>();
		final Map<Integer, List<Integer>> gastosPorFactura = new HashMap<>();

		public ConfigFacturacion config() { return config; }
		public ConfigFacturacion configParaNumerar() { return config; }
		public void guardarConfig(ConfigFacturacion c) { config = c; }
		public void avanzarNumero() {
			config = conRango(config, config.rangoDesde(), config.rangoHasta(), config.siguiente() + 1, config.vigenteDesde());
		}
		public void avanzarNota() {
			ConfigFacturacion c = config;
			config = new ConfigFacturacion(c.responsableIva(), c.tarifaIva(), c.retencionJuridica(), c.retencionNatural(),
					c.diasCredito(), c.resolucion(), c.prefijo(), c.rangoDesde(), c.rangoHasta(), c.siguiente(),
					c.vigenteDesde(), c.vigenteHasta(), c.prefijoNota(), c.siguienteNota() + 1, c.ambiente(), c.proveedor(),
					c.validadaPor(), c.validadaEn());
		}
		public Emisor emisor() {
			return new Emisor("901234567-8", "Rodríguez & Asociados Abogados SAS", "Carrera 7 # 71-21", "Bogotá", "6013216540",
					"contacto@rodriguezasociados.co");
		}
		public int guardar(Factura f) {
			int id = facturas.size() + 1;
			facturas.put(id, conId(f, id, f.estado(), f.cufe(), f.respuesta(), null));
			List<Integer> g = new ArrayList<>();
			f.lineas().stream().filter(l -> l.idGasto() != null).forEach(l -> { gastosCobrados.add(l.idGasto()); g.add(l.idGasto()); });
			gastosPorFactura.put(id, g);
			return id;
		}
		public void registrarEnvio(int id, Factura.Estado estado, String cufe, String respuesta) {
			Factura f = facturas.get(id);
			facturas.put(id, conId(f, id, estado, cufe, respuesta, f.notaCredito()));
			if (estado == Factura.Estado.RECHAZADA) gastosPorFactura.get(id).forEach(gastosCobrados::remove);
		}
		public List<Factura> listar(Integer cliente, Factura.Estado estado) {
			return facturas.values().stream().filter(f -> cliente == null || f.idCliente() == cliente)
					.filter(f -> estado == null || f.estado() == estado).toList();
		}
		public Optional<Factura> buscar(int id) { return Optional.ofNullable(facturas.get(id)); }
		public Map<Integer, BigDecimal> facturadoPorHonorario(int cliente) { return facturado; }
		public Set<Integer> gastosFacturados(int cliente) { return Set.copyOf(gastosCobrados); }
		public int guardarNota(Factura.NotaCredito n) {
			Factura f = facturas.get(n.idFactura());
			facturas.put(n.idFactura(), conId(f, n.idFactura(), f.estado(), f.cufe(), f.respuesta(), n));
			return 1;
		}
		public void anular(int id) {
			Factura f = facturas.get(id);
			facturas.put(id, conId(f, id, Factura.Estado.ANULADA, f.cufe(), f.respuesta(), f.notaCredito()));
			gastosPorFactura.get(id).forEach(gastosCobrados::remove);
		}

		static Factura conId(Factura f, int id, Factura.Estado estado, String cufe, String respuesta, Factura.NotaCredito nota) {
			return new Factura(id, f.prefijo(), f.numero(), f.resolucion(), f.ambiente(), f.idCliente(), f.idProceso(),
					f.emitida(), f.vence(), f.formaPago(), f.totales(), f.observaciones(), cufe, estado, respuesta,
					f.emitidaPor(), f.adquirente(), f.lineas(), nota);
		}
	}

}
