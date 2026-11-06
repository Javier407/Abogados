package co.abogados.gabinete.infraestructura.persistencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import co.abogados.gabinete.dominio.modelo.CarteraCliente;
import co.abogados.gabinete.dominio.modelo.Gasto;
import co.abogados.gabinete.dominio.puertos.GastoRepositorio;

/** Gastos con JPA; la cartera es una consulta de agregación con SQL directo. */
@Component
class GastoRepositorioJpa implements GastoRepositorio {

	private final GastoJpa gastos;
	private final JdbcClient jdbc;

	GastoRepositorioJpa(GastoJpa gastos, JdbcClient jdbc) {
		this.gastos = gastos;
		this.jdbc = jdbc;
	}

	@Override
	public List<Gasto> listar(Integer idProceso, Integer idCliente) {
		return gastos.filtrar(idProceso, idCliente).stream().map(GastoRepositorioJpa::aDominio).toList();
	}

	@Override
	public Optional<Gasto> buscar(int idGasto) {
		return gastos.findById(idGasto).map(GastoRepositorioJpa::aDominio);
	}

	@Override
	public int crear(Gasto g) {
		GastoEntidad e = new GastoEntidad();
		e.idProceso = g.idProceso();
		e.idCliente = g.idCliente();
		e.fecha = g.fecha();
		e.concepto = g.concepto();
		e.valor = g.valor();
		e.pagadoPor = g.pagadoPor().name();
		e.reembolsable = g.reembolsable();
		e.soporteUrl = g.soporteUrl();
		e.registradoPor = g.registradoPor();
		return gastos.saveAndFlush(e).idGasto;
	}

	@Override
	public void marcarReembolsado(int idGasto, LocalDate fecha) {
		GastoEntidad e = gastos.findById(idGasto).orElseThrow();
		e.reembolsadoEn = fecha;
		gastos.saveAndFlush(e);
	}

	@Override
	public void eliminar(int idGasto) {
		gastos.deleteById(idGasto);
	}

	@Override
	public List<CarteraCliente> cartera() {
		return jdbc.sql("""
				WITH pagado AS (
				    SELECT id_honorario, sum(valor) AS pagado, max(fecha) AS ultimo
				    FROM gabinete.pagos WHERE anulado_en IS NULL GROUP BY id_honorario
				), acuerdo AS (
				    SELECT h.id_cliente, h.id_honorario,
				           coalesce(h.valor_liquidado,
				                    CASE WHEN h.forma_cobro IN ('FIJO', 'MIXTO') THEN h.valor_fijo END) AS total,
				           coalesce(p.pagado, 0) AS pagado, p.ultimo
				    FROM gabinete.honorarios_pactados h
				    LEFT JOIN pagado p ON p.id_honorario = h.id_honorario
				    WHERE h.estado <> 'CANCELADO'
				), por_cliente AS (
				    SELECT id_cliente, count(*) AS acuerdos, sum(total) AS pactado, sum(pagado) AS pagado,
				           sum(CASE WHEN total IS NULL THEN 0 ELSE greatest(total - pagado, 0) END) AS saldo,
				           max(ultimo) AS ultimo
				    FROM acuerdo GROUP BY id_cliente
				), gastos AS (
				    SELECT id_cliente, sum(valor) AS por_reembolsar
				    FROM gabinete.gastos WHERE reembolsable AND reembolsado_en IS NULL GROUP BY id_cliente
				)
				SELECT c.id_cliente, c.nombre, coalesce(a.acuerdos, 0) AS acuerdos, a.pactado,
				       coalesce(a.pagado, 0) AS pagado, coalesce(a.saldo, 0) AS saldo,
				       coalesce(g.por_reembolsar, 0) AS gastos, a.ultimo
				FROM gabinete.clientes c
				LEFT JOIN por_cliente a ON a.id_cliente = c.id_cliente
				LEFT JOIN gastos g ON g.id_cliente = c.id_cliente
				WHERE a.id_cliente IS NOT NULL OR g.id_cliente IS NOT NULL
				ORDER BY coalesce(a.saldo, 0) + coalesce(g.por_reembolsar, 0) DESC, c.nombre
				""")
				.query((rs, i) -> {
					BigDecimal saldo = rs.getBigDecimal("saldo");
					BigDecimal gastos = rs.getBigDecimal("gastos");
					return new CarteraCliente(rs.getInt("id_cliente"), rs.getString("nombre"), rs.getInt("acuerdos"),
							rs.getBigDecimal("pactado"), rs.getBigDecimal("pagado"), saldo, gastos, saldo.add(gastos),
							rs.getObject("ultimo", LocalDate.class), null);
				})
				.list();
	}

	static Gasto aDominio(GastoEntidad e) {
		return new Gasto(e.idGasto, e.idProceso, e.idCliente, e.fecha, e.concepto, e.valor,
				Gasto.PagadoPor.valueOf(e.pagadoPor), e.reembolsable, e.reembolsadoEn, e.soporteUrl, e.registradoPor);
	}

}
