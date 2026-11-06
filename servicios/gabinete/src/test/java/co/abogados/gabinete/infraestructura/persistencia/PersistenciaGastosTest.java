package co.abogados.gabinete.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import co.abogados.gabinete.dominio.modelo.CarteraCliente;
import co.abogados.gabinete.dominio.modelo.EstadoHonorario;
import co.abogados.gabinete.dominio.modelo.FormaCobro;
import co.abogados.gabinete.dominio.modelo.Gasto;
import co.abogados.gabinete.dominio.modelo.Honorario;
import co.abogados.gabinete.dominio.modelo.MedioPago;
import co.abogados.gabinete.dominio.modelo.Pago;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.modelo.TipoPersona;

/** Gastos, anulación y liquidación de honorarios y la consulta de cartera sobre H2. */
@DataJpaTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true" })
@Import(AdaptadoresHonorarios.HonorarioRepositorioJpa.class)
class PersistenciaGastosTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	GastoJpa gastoJpa;

	@Autowired
	DataSource datos;

	@Autowired
	AdaptadoresHonorarios.HonorarioRepositorioJpa honorarios;

	GastoRepositorioJpa gastos;
	int idCliente;

	@BeforeEach
	void preparar() {
		gastos = new GastoRepositorioJpa(gastoJpa, JdbcClient.create(datos));
		ClienteEntidad c = new ClienteEntidad();
		c.tipoPersona = TipoPersona.JURIDICA;
		c.tipoDocumento = TipoDocumento.NIT;
		c.numeroDocumento = "900456789";
		c.nombre = "Inversiones Andina SAS";
		c.ciudad = "Bogotá";
		c.idSede = 1;
		c.creado = LocalDate.of(2026, 4, 28);
		idCliente = em.persistAndFlush(c).idCliente;
	}

	Gasto gasto(int proceso, String fecha, String valor, Gasto.PagadoPor quien, boolean reembolsable) {
		return new Gasto(null, proceso, idCliente, LocalDate.parse(fecha), "Copias del expediente", new BigDecimal(valor),
				quien, reembolsable, null, "https://soportes.firma.co/1.pdf", 79123456L);
	}

	@Test
	void guardaFiltraYMarcaReembolsados() {
		int a = gastos.crear(gasto(34, "2026-10-01", "45000", Gasto.PagadoPor.FIRMA, true));
		int b = gastos.crear(gasto(34, "2026-10-05", "300000", Gasto.PagadoPor.CLIENTE, false));
		gastos.crear(gasto(35, "2026-10-03", "12000", Gasto.PagadoPor.FIRMA, true));

		assertThat(gastos.listar(34, null)).extracting(Gasto::idGasto).containsExactly(b, a);
		assertThat(gastos.listar(null, idCliente)).hasSize(3);
		assertThat(gastos.listar(null, 999)).isEmpty();

		gastos.marcarReembolsado(a, LocalDate.of(2026, 10, 20));
		em.clear();
		assertThat(gastos.buscar(a)).hasValueSatisfying(g -> {
			assertThat(g.porReembolsar()).isFalse();
			assertThat(g.pagadoPor()).isEqualTo(Gasto.PagadoPor.FIRMA);
			assertThat(g.soporteUrl()).isEqualTo("https://soportes.firma.co/1.pdf");
		});

		gastos.eliminar(b);
		em.flush();
		assertThat(gastos.buscar(b)).isEmpty();
	}

	@Test
	void anularYLiquidarHonorarios() {
		int id = honorarios.crear(new Honorario(null, 34, idCliente, null, FormaCobro.CUOTA_LITIS, null,
				new BigDecimal("20"), new BigDecimal("100000000"), LocalDate.of(2026, 10, 1), EstadoHonorario.VIGENTE));
		int pago = honorarios.registrarPago(new Pago(null, id, LocalDate.of(2026, 10, 2), new BigDecimal("1000000"),
				MedioPago.EFECTIVO, null));

		assertThat(honorarios.valorLiquidado(id)).isEmpty();
		honorarios.anularPago(pago, Instant.parse("2026-10-04T15:00:00Z"), 79123456L, "Pago duplicado");
		honorarios.liquidar(id, new BigDecimal("50000000"), new BigDecimal("10000000"), LocalDate.of(2026, 10, 20));
		em.clear();

		assertThat(honorarios.pago(pago)).hasValueSatisfying(p -> {
			assertThat(p.anulado()).isTrue();
			assertThat(p.motivoAnulacion()).isEqualTo("Pago duplicado");
		});
		assertThat(honorarios.valorLiquidado(id)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("10000000"));
		assertThat(honorarios.buscar(id)).hasValueSatisfying(h -> {
			assertThat(h.valorLiquidado()).isEqualByComparingTo("10000000");
			assertThat(h.saldo()).isEqualByComparingTo("10000000");
		});
	}

	@Test
	void laCarteraSumaSaldosSinPagosAnuladosYGastosPorReembolsar() {
		int id = honorarios.crear(new Honorario(null, 34, idCliente, null, FormaCobro.FIJO, new BigDecimal("10000000"),
				null, null, LocalDate.of(2026, 10, 1), EstadoHonorario.VIGENTE));
		honorarios.registrarPago(new Pago(null, id, LocalDate.of(2026, 10, 2), new BigDecimal("4000000"),
				MedioPago.TRANSFERENCIA, "TRX-1"));
		int anulado = honorarios.registrarPago(new Pago(null, id, LocalDate.of(2026, 10, 9), new BigDecimal("1000000"),
				MedioPago.EFECTIVO, null));
		honorarios.anularPago(anulado, Instant.parse("2026-10-10T15:00:00Z"), 1L, "Error");
		gastos.crear(gasto(34, "2026-10-05", "45000", Gasto.PagadoPor.FIRMA, true));
		gastos.crear(gasto(34, "2026-10-06", "300000", Gasto.PagadoPor.CLIENTE, false));
		em.flush();

		assertThat(gastos.cartera()).singleElement().satisfies(c -> {
			assertThat(c.cliente()).isEqualTo("Inversiones Andina SAS");
			assertThat(c.acuerdos()).isEqualTo(1);
			assertThat(c.pactado()).isEqualByComparingTo("10000000");
			assertThat(c.pagado()).isEqualByComparingTo("4000000");
			assertThat(c.saldoHonorarios()).isEqualByComparingTo("6000000");
			assertThat(c.gastosPorReembolsar()).isEqualByComparingTo("45000");
			assertThat(c.totalPorCobrar()).isEqualByComparingTo("6045000");
			assertThat(c.ultimoPago()).isEqualTo(LocalDate.of(2026, 10, 2));
		});
		assertThat(gastos.cartera()).extracting(CarteraCliente::diasSinPagar).containsOnlyNulls();
	}

}
