package co.abogados.gabinete.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;

import co.abogados.gabinete.dominio.modelo.ConfigFacturacion;
import co.abogados.gabinete.dominio.modelo.Factura;
import co.abogados.gabinete.dominio.modelo.Factura.TipoLinea;

/** F14: el SQL de la facturación sobre H2 en modo PostgreSQL. */
@JdbcTest(properties = "spring.datasource.url=jdbc:h2:mem:facturacion;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FacturacionRepositorioJdbc.class)
@Sql("/facturacion-h2.sql")
class FacturacionRepositorioJdbcTest {

	@Autowired
	FacturacionRepositorioJdbc repo;

	@Autowired
	JdbcClient jdbc;

	static Factura factura(long numero, Integer gasto) {
		List<Factura.Linea> lineas = gasto == null
				? List.of(Factura.Linea.nueva(1, TipoLinea.SERVICIO, "Honorarios", BigDecimal.ONE, new BigDecimal("1000"),
						new BigDecimal("19"), 3, null))
				: List.of(Factura.Linea.nueva(1, TipoLinea.SERVICIO, "Honorarios", BigDecimal.ONE, new BigDecimal("1000"),
						new BigDecimal("19"), 3, null),
						Factura.Linea.nueva(2, TipoLinea.REEMBOLSO, "Reembolso: copias", BigDecimal.ONE, new BigDecimal("50"),
								BigDecimal.ZERO, null, gasto));
		return new Factura(null, "SETP", numero, "18760000001", "PRUEBAS", 1, 7,
				OffsetDateTime.parse("2026-10-22T10:00:00-05:00"), LocalDate.of(2026, 11, 21), Factura.FormaPago.CREDITO,
				Factura.Totales.de(lineas, new BigDecimal("11")), "Octubre", null, Factura.Estado.RECHAZADA, null, 5L,
				new Factura.Adquirente("Constructora Andina SAS", "NIT", "900456789", "Calle 80", "Bogotá", "pagos@andina.co"),
				lineas, null);
	}

	@Test
	void guardaLaFacturaConSusLineasYMarcaLosGastos() {
		int id = repo.guardar(factura(990000000, 11));
		repo.registrarEnvio(id, Factura.Estado.ACEPTADA, "a".repeat(96), "Validada");

		Factura f = repo.buscar(id).orElseThrow();
		assertThat(f.numeroCompleto()).isEqualTo("SETP990000000");
		assertThat(f.estado()).isEqualTo(Factura.Estado.ACEPTADA);
		assertThat(f.cufe()).hasSize(96);
		assertThat(f.lineas()).extracting(Factura.Linea::tipo).containsExactly(TipoLinea.SERVICIO, TipoLinea.REEMBOLSO);
		assertThat(f.totales().total()).isEqualByComparingTo("1240");
		assertThat(f.totales().neto()).isEqualByComparingTo("1130");
		assertThat(f.adquirente().documento()).isEqualTo("NIT 900456789");
		assertThat(f.emitida().toInstant()).isEqualTo(Instant.parse("2026-10-22T15:00:00Z"));
		assertThat(repo.gastosFacturados(1)).containsExactly(11);
		assertThat(repo.facturadoPorHonorario(1)).containsEntry(3, new BigDecimal("1000.00"));
		assertThat(repo.listar(1, null)).hasSize(1).allSatisfy(x -> assertThat(x.lineas()).isEmpty());
		assertThat(repo.listar(null, Factura.Estado.ANULADA)).isEmpty();
		assertThat(repo.listar(2, null)).isEmpty();
	}

	@Test
	void unaFacturaRechazadaOAnuladaLiberaSusGastosYNoCuentaComoFacturada() {
		int rechazada = repo.guardar(factura(990000000, 11));
		repo.registrarEnvio(rechazada, Factura.Estado.RECHAZADA, null, "x".repeat(400));
		assertThat(repo.gastosFacturados(1)).isEmpty();
		assertThat(repo.facturadoPorHonorario(1)).isEmpty();
		assertThat(repo.buscar(rechazada).orElseThrow().respuesta()).hasSize(300);

		int id = repo.guardar(factura(990000001, 12));
		repo.registrarEnvio(id, Factura.Estado.ACEPTADA, "b".repeat(96), "ok");
		repo.guardarNota(new Factura.NotaCredito(null, "NC", 1, id, "Valor equivocado", new BigDecimal("1240"),
				OffsetDateTime.parse("2026-10-23T09:00:00-05:00"), 5L, "c".repeat(96), Factura.Estado.ACEPTADA, "ok"));
		repo.anular(id);

		Factura f = repo.buscar(id).orElseThrow();
		assertThat(f.estado()).isEqualTo(Factura.Estado.ANULADA);
		assertThat(f.notaCredito().numeroCompleto()).isEqualTo("NC1");
		assertThat(f.notaCredito().motivo()).isEqualTo("Valor equivocado");
		assertThat(repo.gastosFacturados(1)).isEmpty();
		assertThat(repo.buscar(99)).isEmpty();
	}

	@Test
	void laConfiguracionSeLeeSeGuardaYAvanzaLaNumeracion() {
		ConfigFacturacion c = repo.configParaNumerar();
		assertThat(c.prefijo()).isEqualTo("SETP");
		assertThat(c.siguiente()).isEqualTo(990000000L);
		assertThat(c.validada()).isFalse();

		repo.avanzarNumero();
		repo.avanzarNota();
		repo.validarConfig(52345678L, Instant.parse("2026-10-22T15:00:00Z"));

		ConfigFacturacion d = repo.config();
		assertThat(d.siguiente()).isEqualTo(990000001L);
		assertThat(d.siguienteNota()).isEqualTo(2L);
		assertThat(d.validadaPor()).isEqualTo(52345678L);
		assertThat(d.validadaEn()).isEqualTo(Instant.parse("2026-10-22T15:00:00Z"));
		assertThat(d.tarifaIva()).isEqualByComparingTo("19");
	}

	@Test
	void elEmisorEsLaFirmaConSuSedePrincipal() {
		var e = repo.emisor();
		assertThat(e.nit()).isEqualTo("901234567-8");
		assertThat(e.direccion()).isEqualTo("Carrera 7 # 71-21");
		assertThat(e.telefono()).isEqualTo("6013216540");      // la sede no tiene: se usa el de la firma
	}

}
