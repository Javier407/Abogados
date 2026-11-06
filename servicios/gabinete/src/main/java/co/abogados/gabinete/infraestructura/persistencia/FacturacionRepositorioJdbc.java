package co.abogados.gabinete.infraestructura.persistencia;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import co.abogados.gabinete.dominio.modelo.ConfigFacturacion;
import co.abogados.gabinete.dominio.modelo.Factura;
import co.abogados.gabinete.dominio.puertos.FacturacionRepositorio;

@Repository
public class FacturacionRepositorioJdbc implements FacturacionRepositorio {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
	private static final String CONFIG = """
			SELECT responsable_iva, tarifa_iva, retencion_juridica, retencion_natural, dias_credito, resolucion, prefijo,
			       rango_desde, rango_hasta, siguiente, vigente_desde, vigente_hasta, prefijo_nota, siguiente_nota,
			       ambiente, proveedor, validada_por, validada_en
			FROM gabinete.facturacion_config WHERE id_config = 1
			""";
	private static final String FACTURA = """
			SELECT id_factura, prefijo, numero, resolucion, ambiente, id_cliente, id_proceso, emitida, vence, forma_pago,
			       subtotal, iva, reembolsos, total, retencion_pct, retencion, neto, observaciones, cufe, estado, respuesta,
			       emitida_por, cliente_nombre, cliente_documento, cliente_direccion, cliente_ciudad, cliente_email
			FROM gabinete.facturas
			""";

	private final JdbcClient jdbc;

	public FacturacionRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public ConfigFacturacion config() {
		return jdbc.sql(CONFIG).query((rs, i) -> config(rs)).single();
	}

	@Override
	public ConfigFacturacion configParaNumerar() {
		return jdbc.sql(CONFIG + " FOR UPDATE").query((rs, i) -> config(rs)).single();
	}

	@Override
	public void guardarConfig(ConfigFacturacion c) {
		jdbc.sql("""
				UPDATE gabinete.facturacion_config SET responsable_iva = :iva, tarifa_iva = :tarifa,
				       retencion_juridica = :retJ, retencion_natural = :retN, dias_credito = :dias, resolucion = :res,
				       prefijo = :prefijo, rango_desde = :desde, rango_hasta = :hasta, siguiente = :siguiente,
				       vigente_desde = :vDesde, vigente_hasta = :vHasta, prefijo_nota = :prefijoNota,
				       siguiente_nota = :siguienteNota, ambiente = :ambiente, proveedor = :proveedor,
				       validada_por = :validadaPor, validada_en = :validadaEn
				WHERE id_config = 1
				""")
				.param("iva", c.responsableIva()).param("tarifa", c.tarifaIva()).param("retJ", c.retencionJuridica())
				.param("retN", c.retencionNatural()).param("dias", c.diasCredito()).param("res", c.resolucion())
				.param("prefijo", c.prefijo()).param("desde", c.rangoDesde()).param("hasta", c.rangoHasta())
				.param("siguiente", c.siguiente()).param("vDesde", c.vigenteDesde()).param("vHasta", c.vigenteHasta())
				.param("prefijoNota", c.prefijoNota()).param("siguienteNota", c.siguienteNota())
				.param("ambiente", c.ambiente()).param("proveedor", c.proveedor())
				.param("validadaPor", c.validadaPor())
				.param("validadaEn", c.validadaEn() == null ? null : Timestamp.from(c.validadaEn()))
				.update();
	}

	@Override
	public void avanzarNumero() {
		jdbc.sql("UPDATE gabinete.facturacion_config SET siguiente = siguiente + 1 WHERE id_config = 1").update();
	}

	@Override
	public void avanzarNota() {
		jdbc.sql("UPDATE gabinete.facturacion_config SET siguiente_nota = siguiente_nota + 1 WHERE id_config = 1").update();
	}

	@Override
	public Emisor emisor() {
		return jdbc.sql("""
				SELECT f.nit, f.razon_social, s.direccion, s.ciudad, COALESCE(s.telefono, f.telefono), f.email
				FROM gabinete.firma f LEFT JOIN gabinete.sedes s ON s.id_firma = f.id_firma AND s.es_principal
				ORDER BY f.id_firma
				""")
				.query((rs, i) -> new Emisor(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getString(5), rs.getString(6)))
				.list().get(0);
	}

	@Override
	public int guardar(Factura f) {
		Factura.Totales t = f.totales();
		Factura.Adquirente a = f.adquirente();
		KeyHolder llave = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO gabinete.facturas (prefijo, numero, resolucion, ambiente, id_cliente, id_proceso, emitida, vence,
				       forma_pago, subtotal, iva, reembolsos, total, retencion_pct, retencion, neto, observaciones, cufe,
				       estado, respuesta, emitida_por, cliente_nombre, cliente_documento, cliente_direccion, cliente_ciudad,
				       cliente_email)
				VALUES (:prefijo, :numero, :resolucion, :ambiente, :cliente, :proceso, :emitida, :vence, :forma, :subtotal,
				       :iva, :reembolsos, :total, :retPct, :ret, :neto, :obs, :cufe, :estado, :respuesta, :por, :nombre,
				       :documento, :direccion, :ciudad, :email)
				""")
				.param("prefijo", f.prefijo()).param("numero", f.numero()).param("resolucion", f.resolucion())
				.param("ambiente", f.ambiente()).param("cliente", f.idCliente()).param("proceso", f.idProceso())
				.param("emitida", Timestamp.from(f.emitida().toInstant())).param("vence", f.vence())
				.param("forma", f.formaPago().name()).param("subtotal", t.subtotal()).param("iva", t.iva())
				.param("reembolsos", t.reembolsos()).param("total", t.total()).param("retPct", t.retencionPct())
				.param("ret", t.retencion()).param("neto", t.neto()).param("obs", f.observaciones())
				.param("cufe", f.cufe()).param("estado", f.estado().name()).param("respuesta", f.respuesta())
				.param("por", f.emitidaPor()).param("nombre", a.nombre()).param("documento", a.documento())
				.param("direccion", a.direccion()).param("ciudad", a.ciudad()).param("email", a.email())
				.update(llave, "id_factura");
		int id = llave.getKey().intValue();
		for (Factura.Linea l : f.lineas()) {
			jdbc.sql("""
					INSERT INTO gabinete.factura_lineas (id_factura, linea, tipo, descripcion, cantidad, valor_unitario,
					       iva_pct, valor, iva, id_honorario, id_gasto)
					VALUES (:id, :linea, :tipo, :descripcion, :cantidad, :unitario, :ivaPct, :valor, :iva, :honorario, :gasto)
					""")
					.param("id", id).param("linea", l.linea()).param("tipo", l.tipo().name())
					.param("descripcion", l.descripcion()).param("cantidad", l.cantidad())
					.param("unitario", l.valorUnitario()).param("ivaPct", l.ivaPct()).param("valor", l.valor())
					.param("iva", l.iva()).param("honorario", l.idHonorario()).param("gasto", l.idGasto())
					.update();
			if (l.idGasto() != null) {
				jdbc.sql("UPDATE gabinete.gastos SET id_factura = :id WHERE id_gasto = :gasto")
						.param("id", id).param("gasto", l.idGasto()).update();
			}
		}
		return id;
	}

	@Override
	public void registrarEnvio(int idFactura, Factura.Estado estado, String cufe, String respuesta) {
		jdbc.sql("UPDATE gabinete.facturas SET estado = :estado, cufe = :cufe, respuesta = :respuesta WHERE id_factura = :id")
				.param("estado", estado.name()).param("cufe", cufe)
				.param("respuesta", respuesta == null || respuesta.length() <= 300 ? respuesta : respuesta.substring(0, 300))
				.param("id", idFactura).update();
		if (estado == Factura.Estado.RECHAZADA) {
			liberarGastos(idFactura);
		}
	}

	@Override
	public List<Factura> listar(Integer idCliente, Factura.Estado estado) {
		return jdbc.sql(FACTURA + """
				WHERE (CAST(:cliente AS integer) IS NULL OR id_cliente = :cliente)
				  AND (CAST(:estado AS varchar(10)) IS NULL OR estado = :estado)
				ORDER BY emitida DESC, id_factura DESC LIMIT 500
				""")
				.param("cliente", idCliente).param("estado", estado == null ? null : estado.name())
				.query((rs, i) -> factura(rs, List.of(), null)).list();
	}

	@Override
	public Optional<Factura> buscar(int idFactura) {
		List<Factura.Linea> lineas = jdbc.sql("""
				SELECT linea, tipo, descripcion, cantidad, valor_unitario, iva_pct, valor, iva, id_honorario, id_gasto
				FROM gabinete.factura_lineas WHERE id_factura = :id ORDER BY linea
				""")
				.param("id", idFactura)
				.query((rs, i) -> new Factura.Linea(rs.getInt(1), Factura.TipoLinea.valueOf(rs.getString(2)), rs.getString(3),
						rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8),
						rs.getObject(9, Integer.class), rs.getObject(10, Integer.class)))
				.list();
		Factura.NotaCredito nota = jdbc.sql("""
				SELECT id_nota, prefijo, numero, id_factura, motivo, valor, emitida, emitida_por, cude, estado, respuesta
				FROM gabinete.notas_credito WHERE id_factura = :id
				""")
				.param("id", idFactura)
				.query((rs, i) -> new Factura.NotaCredito(rs.getInt(1), rs.getString(2), rs.getLong(3), rs.getInt(4),
						rs.getString(5), rs.getBigDecimal(6), momento(rs.getTimestamp(7)), rs.getLong(8), rs.getString(9),
						Factura.Estado.valueOf(rs.getString(10)), rs.getString(11)))
				.optional().orElse(null);
		return jdbc.sql(FACTURA + " WHERE id_factura = :id").param("id", idFactura)
				.query((rs, i) -> factura(rs, lineas, nota)).optional();
	}

	@Override
	public Map<Integer, BigDecimal> facturadoPorHonorario(int idCliente) {
		Map<Integer, BigDecimal> mapa = new HashMap<>();
		jdbc.sql("""
				SELECT l.id_honorario, SUM(l.valor)
				FROM gabinete.factura_lineas l JOIN gabinete.facturas f ON f.id_factura = l.id_factura
				WHERE f.id_cliente = :cliente AND f.estado = 'ACEPTADA' AND l.id_honorario IS NOT NULL
				GROUP BY l.id_honorario
				""")
				.param("cliente", idCliente)
				.query((rs, i) -> mapa.put(rs.getInt(1), rs.getBigDecimal(2))).list();
		return mapa;
	}

	@Override
	public Set<Integer> gastosFacturados(int idCliente) {
		return new HashSet<>(jdbc.sql("SELECT id_gasto FROM gabinete.gastos WHERE id_cliente = :cliente AND id_factura IS NOT NULL")
				.param("cliente", idCliente).query(Integer.class).list());
	}

	@Override
	public int guardarNota(Factura.NotaCredito n) {
		KeyHolder llave = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO gabinete.notas_credito (prefijo, numero, id_factura, motivo, valor, emitida, emitida_por, cude,
				       estado, respuesta)
				VALUES (:prefijo, :numero, :factura, :motivo, :valor, :emitida, :por, :cude, :estado, :respuesta)
				""")
				.param("prefijo", n.prefijo()).param("numero", n.numero()).param("factura", n.idFactura())
				.param("motivo", n.motivo()).param("valor", n.valor()).param("emitida", Timestamp.from(n.emitida().toInstant()))
				.param("por", n.emitidaPor()).param("cude", n.cude()).param("estado", n.estado().name())
				.param("respuesta", n.respuesta())
				.update(llave, "id_nota");
		return llave.getKey().intValue();
	}

	@Override
	public void anular(int idFactura) {
		jdbc.sql("UPDATE gabinete.facturas SET estado = 'ANULADA' WHERE id_factura = :id").param("id", idFactura).update();
		liberarGastos(idFactura);
	}

	private void liberarGastos(int idFactura) {
		jdbc.sql("UPDATE gabinete.gastos SET id_factura = NULL WHERE id_factura = :id").param("id", idFactura).update();
	}

	// ------------------------------------------------------------------

	private static ConfigFacturacion config(ResultSet rs) throws SQLException {
		Timestamp validada = rs.getTimestamp("validada_en");
		return new ConfigFacturacion(rs.getBoolean("responsable_iva"), rs.getBigDecimal("tarifa_iva"),
				rs.getBigDecimal("retencion_juridica"), rs.getBigDecimal("retencion_natural"), rs.getInt("dias_credito"),
				rs.getString("resolucion"), rs.getString("prefijo"), rs.getLong("rango_desde"), rs.getLong("rango_hasta"),
				rs.getLong("siguiente"), rs.getDate("vigente_desde").toLocalDate(), rs.getDate("vigente_hasta").toLocalDate(),
				rs.getString("prefijo_nota"), rs.getLong("siguiente_nota"), rs.getString("ambiente"), rs.getString("proveedor"),
				rs.getObject("validada_por", Long.class), validada == null ? null : validada.toInstant());
	}

	private static Factura factura(ResultSet rs, List<Factura.Linea> lineas, Factura.NotaCredito nota) throws SQLException {
		String documento = rs.getString("cliente_documento");
		int espacio = documento.indexOf(' ');
		return new Factura(rs.getInt("id_factura"), rs.getString("prefijo"), rs.getLong("numero"), rs.getString("resolucion"),
				rs.getString("ambiente"), rs.getInt("id_cliente"), rs.getObject("id_proceso", Integer.class),
				momento(rs.getTimestamp("emitida")), rs.getDate("vence").toLocalDate(),
				Factura.FormaPago.valueOf(rs.getString("forma_pago")),
				new Factura.Totales(rs.getBigDecimal("subtotal"), rs.getBigDecimal("iva"), rs.getBigDecimal("reembolsos"),
						rs.getBigDecimal("total"), rs.getBigDecimal("retencion_pct"), rs.getBigDecimal("retencion"),
						rs.getBigDecimal("neto")),
				rs.getString("observaciones"), rs.getString("cufe"), Factura.Estado.valueOf(rs.getString("estado")),
				rs.getString("respuesta"), rs.getLong("emitida_por"),
				new Factura.Adquirente(rs.getString("cliente_nombre"), documento.substring(0, Math.max(espacio, 0)),
						documento.substring(espacio + 1), rs.getString("cliente_direccion"), rs.getString("cliente_ciudad"),
						rs.getString("cliente_email")),
				lineas, nota);
	}

	private static OffsetDateTime momento(Timestamp t) {
		return t == null ? null : OffsetDateTime.ofInstant(t.toInstant(), BOGOTA);
	}


}
