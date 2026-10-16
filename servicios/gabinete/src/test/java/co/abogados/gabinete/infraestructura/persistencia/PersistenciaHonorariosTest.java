package co.abogados.gabinete.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import co.abogados.gabinete.dominio.modelo.EstadoHonorario;
import co.abogados.gabinete.dominio.modelo.FormaCobro;
import co.abogados.gabinete.dominio.modelo.Honorario;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.MedioPago;
import co.abogados.gabinete.dominio.modelo.Pago;
import co.abogados.gabinete.dominio.modelo.Perito;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.modelo.TipoPersona;

/** Entidades y consultas de F4 (peritos, tarifas, honorarios y pagos) sobre H2. */
@DataJpaTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true" })
@Import({ AdaptadoresHonorarios.PeritoRepositorioJpa.class, AdaptadoresHonorarios.TarifaRepositorioJpa.class,
		AdaptadoresHonorarios.HonorarioRepositorioJpa.class })
class PersistenciaHonorariosTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	AdaptadoresHonorarios.PeritoRepositorioJpa peritos;

	@Autowired
	AdaptadoresHonorarios.TarifaRepositorioJpa tarifas;

	@Autowired
	AdaptadoresHonorarios.HonorarioRepositorioJpa honorarios;

	int idCliente;

	@BeforeEach
	void datos() {
		ClienteEntidad c = new ClienteEntidad();
		c.tipoPersona = TipoPersona.NATURAL;
		c.tipoDocumento = TipoDocumento.CC;
		c.numeroDocumento = "52678123";
		c.nombre = "María Fernanda López";
		c.ciudad = "Bogotá";
		c.idSede = 1;
		c.creado = LocalDate.of(2026, 4, 28);
		idCliente = em.persistAndFlush(c).idCliente;

		SmmlvEntidad s = new SmmlvEntidad();
		s.anio = 2026;
		s.valor = new BigDecimal("1750905");
		em.persist(s);
		TarifaEntidad t = new TarifaEntidad();
		t.idTarifa = 1;
		t.fuente = "CONALBOS";
		t.codigoRama = "FAMILIA";
		t.servicio = "Divorcio contencioso";
		t.tipoServicio = "CUALQUIERA";
		t.formaCobro = "FIJO";
		t.valorSmmlv = new BigDecimal("5.00");
		t.vigenteDesde = LocalDate.of(2026, 1, 1);
		em.persist(t);
		em.flush();
	}

	@Test
	void tarifasEnPesosYSalarioMinimoDelUltimoAnioSiFaltaElPedido() {
		assertThat(tarifas.smmlv(2026)).hasValueSatisfying(s -> assertThat(s.valor()).isEqualByComparingTo("1750905"));
		assertThat(tarifas.smmlv(2030)).hasValueSatisfying(s -> assertThat(s.anio()).isEqualTo(2026));
		assertThat(tarifas.listar(2026, new BigDecimal("1750905"))).singleElement()
				.satisfies(t -> assertThat(t.desdePesos()).isEqualByComparingTo("8754525"));
		assertThat(tarifas.existe((short) 1)).isTrue();
	}

	@Test
	void honorariosConLoPagadoYFiltros() {
		int id = honorarios.crear(new Honorario(null, 34, idCliente, (short) 1, FormaCobro.FIJO,
				new BigDecimal("8754525"), null, null, LocalDate.of(2026, 10, 1), EstadoHonorario.VIGENTE));
		honorarios.registrarPago(new Pago(null, id, LocalDate.of(2026, 10, 2), new BigDecimal("3000000"),
				MedioPago.TRANSFERENCIA, "TRX-1"));
		honorarios.registrarPago(new Pago(null, id, LocalDate.of(2026, 10, 9), new BigDecimal("1000000"),
				MedioPago.EFECTIVO, null));

		HonorarioVista h = honorarios.buscar(id).orElseThrow();
		assertThat(h.cliente()).isEqualTo("María Fernanda López");
		assertThat(h.pagado()).isEqualByComparingTo("4000000");
		assertThat(h.saldo()).isEqualByComparingTo("4754525");
		assertThat(h.pagos()).isEqualTo(2);
		assertThat(honorarios.totalPagado(id)).isEqualByComparingTo("4000000");
		assertThat(honorarios.pagos(id)).extracting(Pago::referencia).containsExactly(null, "TRX-1");

		assertThat(honorarios.listar(null, null, null)).hasSize(1);
		assertThat(honorarios.listar(idCliente, 34, "VIGENTE")).hasSize(1);
		assertThat(honorarios.listar(null, 99, null)).isEmpty();
		assertThat(honorarios.existeParaProceso(34)).isTrue();

		honorarios.cambiarEstado(id, "CANCELADO");
		assertThat(honorarios.acuerdo(id)).hasValueSatisfying(a -> assertThat(a.estado()).isEqualTo(EstadoHonorario.CANCELADO));
	}

	@Test
	void unaMensualidadNoTieneSaldo() {
		int id = honorarios.crear(new Honorario(null, 35, idCliente, null, FormaCobro.MENSUAL, new BigDecimal("875453"),
				null, null, LocalDate.of(2026, 10, 1), EstadoHonorario.VIGENTE));
		assertThat(honorarios.buscar(id).orElseThrow().saldo()).isNull();
	}

	@Test
	void peritosSeGuardanYSeBuscanPorEspecialidad() {
		Perito p = peritos.guardar(new Perito(null, "Claudia Ruiz", "Ingeniería civil", "Ingeniera civil", "IC-123", null,
				null, "Bogotá", new BigDecimal("4500000"), (short) 5));
		assertThat(p.idPerito()).isNotNull();
		assertThat(peritos.listar("civil")).extracting(Perito::nombre).containsExactly("Claudia Ruiz");
		assertThat(peritos.listar(null)).hasSize(1);
	}

}
