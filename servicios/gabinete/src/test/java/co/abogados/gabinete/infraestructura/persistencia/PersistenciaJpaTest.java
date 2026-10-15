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

import co.abogados.gabinete.dominio.modelo.Abogado;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.modelo.TipoPersona;

/**
 * Entidades, consultas JPQL y adaptadores contra una base H2 en memoria. Detecta al arrancar
 * los errores de mapeo o de consultas que las pruebas de la API (sin JPA) no ven.
 */
@DataJpaTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true" })
@Import({ Adaptadores.ClienteRepositorioJpa.class, Adaptadores.AbogadoRepositorioJpa.class,
		Adaptadores.CatalogoGabineteJpa.class })
class PersistenciaJpaTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	Adaptadores.ClienteRepositorioJpa clientes;

	@Autowired
	Adaptadores.AbogadoRepositorioJpa abogados;

	@Autowired
	Adaptadores.CatalogoGabineteJpa catalogo;

	@BeforeEach
	void catalogos() {
		SedeEntidad sede = new SedeEntidad();
		sede.idSede = 1;
		sede.nombre = "Sede Bogotá";
		sede.ciudad = "Bogotá";
		sede.direccion = "Carrera 7";
		sede.esPrincipal = true;
		em.persist(sede);
		em.persist(cargo((short) 2, "ASOCIADO", "Abogado asociado", true));
		em.persist(cargo((short) 6, "SECRETARIA", "Secretaria", false));
		em.flush();
	}

	static CargoEntidad cargo(short id, String codigo, String nombre, boolean esAbogado) {
		CargoEntidad c = new CargoEntidad();
		c.idCargo = id;
		c.codigo = codigo;
		c.nombre = nombre;
		c.esAbogado = esAbogado;
		return c;
	}

	static Abogado abogado(long cedula, String email, String tarjeta, boolean activo) {
		return new Abogado(cedula, "Laura", "Pineda", email, null, (short) 2, null, (short) 1, null, null, null,
				tarjeta, new BigDecimal("260000"), LocalDate.of(2018, 8, 13), activo);
	}

	@Test
	void guardaYBuscaClientes() {
		Cliente c = clientes.guardar(new Cliente(null, TipoPersona.JURIDICA, TipoDocumento.NIT, "900456789",
				"Inversiones Andina SAS", null, null, null, "Bogotá", (short) 1, LocalDate.of(2026, 10, 15)));

		assertThat(c.idCliente()).isNotNull();
		assertThat(clientes.idPorDocumento(TipoDocumento.NIT, "900456789")).contains(c.idCliente());
		assertThat(clientes.listar("andina")).extracting(Cliente::nombre).containsExactly("Inversiones Andina SAS");
		assertThat(clientes.listar("9004")).hasSize(1);
		assertThat(clientes.listar(null)).hasSize(1);
	}

	@Test
	void guardaAbogadosConLosNombresDeSuCargoYSede() {
		Abogado guardado = abogados.guardar(abogado(1020456789L, "lpineda@firma.co", "201334", true));

		assertThat(guardado.cargo()).isEqualTo("Abogado asociado");
		assertThat(guardado.sede()).isEqualTo("Sede Bogotá");
		assertThat(abogados.idPorEmail("LPINEDA@firma.co")).contains(1020456789L);
		assertThat(abogados.idPorTarjeta("201334")).contains(1020456789L);
	}

	@Test
	void listaSoloAbogadosYFiltraLosInactivos() {
		abogados.guardar(abogado(1L, "a@firma.co", "1001", true));
		abogados.guardar(abogado(2L, "b@firma.co", "1002", false));
		PersonalEntidad secretaria = new PersonalEntidad();
		secretaria.idPersona = 3L;
		secretaria.nombres = "Gloria";
		secretaria.apellidos = "Pardo";
		secretaria.email = "g@firma.co";
		secretaria.cargo = em.find(CargoEntidad.class, (short) 6);
		secretaria.sede = em.find(SedeEntidad.class, (short) 1);
		secretaria.fechaIngreso = LocalDate.of(2014, 9, 1);
		secretaria.activo = true;
		em.persist(secretaria);
		em.flush();

		assertThat(abogados.listar(false)).extracting(Abogado::idPersona).containsExactlyInAnyOrder(1L, 2L);
		assertThat(abogados.listar(true)).extracting(Abogado::idPersona).containsExactly(1L);
		assertThat(abogados.buscar(3L)).isEmpty();
		assertThat(abogados.existePersona(3L)).isTrue();
	}

	@Test
	void leeLosCatalogos() {
		assertThat(catalogo.sedes()).hasSize(1);
		assertThat(catalogo.cargo((short) 6)).hasValueSatisfying(c -> assertThat(c.esAbogado()).isFalse());
		assertThat(catalogo.area((short) 1)).isEmpty();
	}

}
