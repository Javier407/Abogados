package co.abogados.gabinete.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.ConfigFacturacion;
import co.abogados.gabinete.dominio.modelo.Factura;
import co.abogados.gabinete.dominio.modelo.Factura.Linea;
import co.abogados.gabinete.dominio.modelo.Factura.TipoLinea;
import co.abogados.gabinete.dominio.modelo.Gasto;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.puertos.ClienteRepositorio;
import co.abogados.gabinete.dominio.puertos.FacturacionRepositorio;
import co.abogados.gabinete.dominio.puertos.GastoRepositorio;
import co.abogados.gabinete.dominio.puertos.HonorarioRepositorio;
import co.abogados.gabinete.dominio.puertos.ProveedorFacturacion;

/**
 * Facturación electrónica (F14).
 * - Se factura a un cliente: servicios (honorarios, con IVA si la firma es responsable) y reembolsos de gastos que
 *   la firma adelantó (sin IVA). El sistema sugiere lo pendiente: el saldo por facturar de cada acuerdo y los gastos
 *   reembolsables aún sin cobrar. Un gasto solo se cobra en una factura.
 * - El número sale de la resolución vigente, sin saltos (la configuración se bloquea al tomarlo).
 * - La factura se envía al proveedor tecnológico (hoy el SIMULADO), que devuelve el CUFE y si la DIAN la aceptó.
 * - Para corregir una factura se anula con una nota crédito por el total y se emite otra.
 * - El cliente debe tener correo y dirección: la DIAN exige el correo del adquirente para entregarle la factura.
 */
@Service
@Transactional
public class FacturacionServicio {

	private static final Logger log = LoggerFactory.getLogger(FacturacionServicio.class);
	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	public record LineaSolicitud(TipoLinea tipo, String descripcion, BigDecimal cantidad, BigDecimal valorUnitario,
			Integer idHonorario, Integer idGasto) {
	}

	/** retencionPct null = la sugerida según el tipo de cliente. */
	public record Solicitud(int idCliente, Integer idProceso, Factura.FormaPago formaPago, BigDecimal retencionPct,
			String observaciones, List<LineaSolicitud> lineas) {
	}

	/** Lo que se puede facturar a un cliente. */
	public record Pendiente(TipoLinea tipo, String descripcion, BigDecimal valor, Integer idHonorario, Integer idGasto,
			Integer idProceso) {
	}

	public record VistaPrevia(List<Linea> lineas, Factura.Totales totales, BigDecimal retencionSugerida) {
	}

	/** La factura con lo necesario para imprimirla: emisor, resolución y el texto del código QR. */
	public record Detalle(Factura factura, FacturacionRepositorio.Emisor emisor, ConfigFacturacion config, String textoQr,
			String urlConsulta) {
	}

	public record EstadoConfig(ConfigFacturacion config, long restantes, String impedimento, String proveedor) {
	}

	private final FacturacionRepositorio repo;
	private final ClienteRepositorio clientes;
	private final HonorarioRepositorio honorarios;
	private final GastoRepositorio gastos;
	private final ProveedorFacturacion proveedor;
	private final Clock reloj;

	public FacturacionServicio(FacturacionRepositorio repo, ClienteRepositorio clientes, HonorarioRepositorio honorarios,
			GastoRepositorio gastos, ProveedorFacturacion proveedor, Clock reloj) {
		this.repo = repo;
		this.clientes = clientes;
		this.honorarios = honorarios;
		this.gastos = gastos;
		this.proveedor = proveedor;
		this.reloj = reloj;
	}

	// ------------------------------------------------------------------ configuración

	@Transactional(readOnly = true)
	public EstadoConfig estadoConfig() {
		ConfigFacturacion c = repo.config();
		return new EstadoConfig(c, c.restantes(), c.impedimento(hoy()), proveedor.nombre());
	}

	/** Guarda la configuración; si cambian el IVA o las retenciones, queda sin validar por el contador. */
	public EstadoConfig guardarConfig(ConfigFacturacion nueva, long usuario) {
		nueva.validar();
		ConfigFacturacion actual = repo.config();
		if (!ConfigFacturacion.SIMULADO.equals(nueva.proveedor()) && !nueva.proveedor().equals(proveedor.nombre())) {
			throw new ReglaNegocioException("El proveedor " + nueva.proveedor() + " no está conectado; hoy solo está "
					+ proveedor.nombre());
		}
		if (nueva.siguiente() < actual.siguiente() && nueva.prefijo().equals(actual.prefijo())) {
			throw new ReglaNegocioException("El siguiente número no puede retroceder: ya se usó hasta "
					+ (actual.siguiente() - 1));
		}
		boolean sigueValidada = actual.validada() && !nueva.cambiaImpuestos(actual);
		repo.guardarConfig(nueva.conValidacion(sigueValidada ? actual.validadaPor() : null,
				sigueValidada ? actual.validadaEn() : null));
		log.info("Configuración de facturación guardada por {}{}", usuario, sigueValidada ? "" : " (sin validar)");
		return estadoConfig();
	}

	/** El contador revisó el IVA y las retenciones. */
	public EstadoConfig validarConfig(long usuario) {
		repo.validarConfig(usuario, Instant.now(reloj));
		log.info("IVA y retenciones de la facturación validados por {}", usuario);
		return estadoConfig();
	}

	// ------------------------------------------------------------------ facturas

	@Transactional(readOnly = true)
	public List<Pendiente> pendientes(int idCliente) {
		cliente(idCliente);
		Map<Integer, BigDecimal> facturado = repo.facturadoPorHonorario(idCliente);
		Set<Integer> gastosCobrados = repo.gastosFacturados(idCliente);
		List<Pendiente> lista = new ArrayList<>();
		for (HonorarioVista h : honorarios.listar(idCliente, null, "VIGENTE")) {
			BigDecimal porFacturar;
			String detalle;
			if ("MENSUAL".equals(h.formaCobro()) && h.valorFijo() != null) {
				porFacturar = h.valorFijo();                 // cada mes se factura la cuota
				detalle = "Honorarios mensuales";
			} else if (h.saldo() != null) {
				BigDecimal total = h.pagado().add(h.saldo());
				porFacturar = total.subtract(facturado.getOrDefault(h.idHonorario(), BigDecimal.ZERO));
				detalle = "Honorarios";
			} else {
				continue;                                    // porcentaje sin liquidar: aún no hay valor
			}
			if (porFacturar.signum() > 0) {
				lista.add(new Pendiente(TipoLinea.SERVICIO, detalle + " del proceso " + h.idProceso() + " ("
						+ h.formaCobro().toLowerCase().replace('_', ' ') + ")", porFacturar, h.idHonorario(), null, h.idProceso()));
			}
		}
		for (Gasto g : gastos.listar(null, idCliente)) {
			if (g.reembolsable() && g.reembolsadoEn() == null && !gastosCobrados.contains(g.idGasto())) {
				lista.add(new Pendiente(TipoLinea.REEMBOLSO, "Reembolso: " + g.concepto() + " (" + g.fecha() + ")", g.valor(),
						null, g.idGasto(), g.idProceso()));
			}
		}
		return lista;
	}

	@Transactional(readOnly = true)
	public VistaPrevia vistaPrevia(Solicitud s) {
		ConfigFacturacion config = repo.config();
		Cliente c = cliente(s.idCliente());
		List<Linea> lineas = lineas(s, config, c.idCliente());
		BigDecimal sugerida = config.retencionSugerida(c.tipoPersona());
		return new VistaPrevia(lineas, Factura.Totales.de(lineas, s.retencionPct() == null ? sugerida : s.retencionPct()),
				sugerida);
	}

	/** Emite la factura: toma el número, la guarda y la envía al proveedor. */
	public Detalle emitir(Solicitud s, long usuario) {
		Cliente c = cliente(s.idCliente());
		if (vacio(c.email()) || vacio(c.direccion())) {
			throw new ReglaNegocioException("Para facturar, la ficha de " + c.nombre()
					+ " necesita correo y dirección (la DIAN exige el correo del adquirente)");
		}
		ConfigFacturacion config = repo.configParaNumerar();
		String impedimento = config.impedimento(hoy());
		if (impedimento != null) {
			throw new ReglaNegocioException(impedimento);
		}
		List<Linea> lineas = lineas(s, config, c.idCliente());
		BigDecimal retencion = s.retencionPct() == null ? config.retencionSugerida(c.tipoPersona()) : s.retencionPct();
		Factura.Totales totales = Factura.Totales.de(lineas, retencion);
		if (totales.total().signum() <= 0) {
			throw new ReglaNegocioException("El total de la factura debe ser mayor que cero");
		}
		OffsetDateTime ahora = OffsetDateTime.now(reloj.withZone(BOGOTA));
		Factura.FormaPago forma = s.formaPago() == null ? Factura.FormaPago.CREDITO : s.formaPago();
		LocalDate vence = forma == Factura.FormaPago.CONTADO ? ahora.toLocalDate() : ahora.toLocalDate().plusDays(config.diasCredito());
		String observaciones = s.observaciones() == null || s.observaciones().isBlank() ? null : s.observaciones().strip();
		if (observaciones != null && observaciones.length() > 300) {
			throw new ReglaNegocioException("Las observaciones pasan de 300 caracteres");
		}
		Factura factura = new Factura(null, config.prefijo(), config.siguiente(), config.resolucion(), config.ambiente(),
				c.idCliente(), s.idProceso(), ahora, vence, forma, totales, observaciones, null, Factura.Estado.RECHAZADA,
				null, usuario, new Factura.Adquirente(c.nombre(), c.tipoDocumento().name(), c.numeroDocumento(),
						c.direccion(), c.ciudad(), c.email()), lineas, null);
		repo.avanzarNumero();
		int id = repo.guardar(factura);
		ProveedorFacturacion.Envio envio = proveedor.enviarFactura(factura, repo.emisor(), config);
		repo.registrarEnvio(id, envio.aceptado() ? Factura.Estado.ACEPTADA : Factura.Estado.RECHAZADA, envio.codigo(),
				envio.mensaje());
		log.info("Factura {} emitida por {} a {} por {} ({})", factura.numeroCompleto(), usuario, c.nombre(),
				totales.total(), envio.aceptado() ? "aceptada" : "rechazada: " + envio.mensaje());
		return detalle(id);
	}

	/** Anula con una nota crédito por el total; los gastos cobrados vuelven a quedar pendientes. */
	public Detalle anular(int idFactura, String motivo, long usuario) {
		if (vacio(motivo) || motivo.strip().length() > 200) {
			throw new ReglaNegocioException("Escriba el motivo de la anulación (hasta 200 caracteres)");
		}
		Factura f = factura(idFactura);
		if (f.estado() == Factura.Estado.ANULADA) {
			throw new ConflictoException("La factura " + f.numeroCompleto() + " ya está anulada");
		}
		if (f.estado() != Factura.Estado.ACEPTADA) {
			throw new ReglaNegocioException("Solo se anula una factura aceptada por la DIAN; una rechazada no tiene efecto");
		}
		ConfigFacturacion config = repo.configParaNumerar();
		Factura.NotaCredito nota = new Factura.NotaCredito(null, config.prefijoNota(), config.siguienteNota(), idFactura,
				motivo.strip(), f.totales().total(), OffsetDateTime.now(reloj.withZone(BOGOTA)), usuario, null,
				Factura.Estado.RECHAZADA, null);
		ProveedorFacturacion.Envio envio = proveedor.enviarNotaCredito(nota, f, repo.emisor(), config);
		if (!envio.aceptado()) {
			throw new ReglaNegocioException("La DIAN no aceptó la nota crédito: " + envio.mensaje());
		}
		repo.avanzarNota();
		repo.guardarNota(new Factura.NotaCredito(null, nota.prefijo(), nota.numero(), idFactura, nota.motivo(), nota.valor(),
				nota.emitida(), usuario, envio.codigo(), Factura.Estado.ACEPTADA, envio.mensaje()));
		repo.anular(idFactura);
		log.info("Factura {} anulada por {} con la nota {}: {}", f.numeroCompleto(), usuario, nota.numeroCompleto(), motivo);
		return detalle(idFactura);
	}

	@Transactional(readOnly = true)
	public List<Factura> listar(Integer idCliente, Factura.Estado estado) {
		return repo.listar(idCliente, estado);
	}

	@Transactional(readOnly = true)
	public Detalle detalle(int idFactura) {
		Factura f = factura(idFactura);
		FacturacionRepositorio.Emisor emisor = repo.emisor();
		ConfigFacturacion config = repo.config();
		String url = (f.ambiente().equals("PRODUCCION") ? "https://catalogo-vpfe.dian.gov.co"
				: "https://catalogo-vpfe-hab.dian.gov.co") + "/document/searchqr?documentkey=" + (f.cufe() == null ? "" : f.cufe());
		// Contenido del código QR de la representación gráfica (Anexo Técnico de la DIAN).
		String qr = "NumFac: " + f.numeroCompleto() + "\nFecFac: " + f.emitida().toLocalDate() + "\nHorFac: "
				+ f.emitida().toLocalTime().withNano(0) + "-05:00\nNitFac: " + co.abogados.gabinete.dominio.modelo.Cufe.soloNit(emisor.nit())
				+ "\nDocAdq: " + f.adquirente().numeroDocumento() + "\nValFac: " + f.totales().subtotal() + "\nValIva: "
				+ f.totales().iva() + "\nValOtroIm: 0.00\nValTolFac: " + f.totales().total() + "\nCUFE: "
				+ (f.cufe() == null ? "" : f.cufe()) + "\nQRCode: " + url;
		return new Detalle(f, emisor, config, qr, url);
	}

	// ------------------------------------------------------------------

	/** Arma las líneas: los reembolsos toman el valor y el concepto del gasto (no los que traiga la petición). */
	private List<Linea> lineas(Solicitud s, ConfigFacturacion config, int idCliente) {
		if (s.lineas() == null || s.lineas().isEmpty()) {
			throw new ReglaNegocioException("Agregue al menos una línea a la factura");
		}
		if (s.lineas().size() > 50) {
			throw new ReglaNegocioException("Una factura admite hasta 50 líneas");
		}
		Set<Integer> cobrados = repo.gastosFacturados(idCliente);
		List<Integer> vistos = new ArrayList<>();
		List<Linea> lineas = new ArrayList<>();
		int n = 1;
		for (LineaSolicitud l : s.lineas()) {
			if (l.tipo() == TipoLinea.REEMBOLSO) {
				if (l.idGasto() == null) {
					throw new ReglaNegocioException("Un reembolso se factura a partir de un gasto registrado");
				}
				Gasto g = gastos.buscar(l.idGasto())
						.orElseThrow(() -> new ReglaNegocioException("No existe el gasto " + l.idGasto()));
				if (g.idCliente() != idCliente || !g.reembolsable() || g.reembolsadoEn() != null) {
					throw new ReglaNegocioException("El gasto " + g.idGasto() + " no es un reembolso pendiente de este cliente");
				}
				if (cobrados.contains(g.idGasto()) || vistos.contains(g.idGasto())) {
					throw new ConflictoException("El gasto «" + g.concepto() + "» ya está en una factura");
				}
				vistos.add(g.idGasto());
				lineas.add(Linea.nueva(n++, TipoLinea.REEMBOLSO, "Reembolso: " + g.concepto(), BigDecimal.ONE, g.valor(),
						BigDecimal.ZERO, null, g.idGasto()));
			} else {
				if (l.idHonorario() != null) {
					HonorarioVista h = honorarios.buscar(l.idHonorario())
							.orElseThrow(() -> new ReglaNegocioException("No existe el acuerdo de honorarios " + l.idHonorario()));
					if (h.idCliente() != idCliente) {
						throw new ReglaNegocioException("El acuerdo de honorarios " + h.idHonorario() + " es de otro cliente");
					}
				}
				lineas.add(Linea.nueva(n++, TipoLinea.SERVICIO, l.descripcion(), l.cantidad(), l.valorUnitario(),
						config.ivaServicios(), l.idHonorario(), null));
			}
		}
		return lineas;
	}

	private Cliente cliente(int idCliente) {
		return clientes.buscar(idCliente)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + idCliente));
	}

	private Factura factura(int idFactura) {
		return repo.buscar(idFactura)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la factura " + idFactura));
	}

	private LocalDate hoy() {
		return LocalDate.now(reloj.withZone(BOGOTA));
	}

	private static boolean vacio(String s) {
		return s == null || s.isBlank();
	}

}
