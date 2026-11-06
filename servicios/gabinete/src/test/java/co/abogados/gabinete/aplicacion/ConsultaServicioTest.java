package co.abogados.gabinete.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.Consulta;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.modelo.TipoPersona;
import co.abogados.gabinete.dominio.puertos.ClienteRepositorio;
import co.abogados.gabinete.dominio.puertos.ConsultaRepositorio;

/** F10: consulta inicial con aviso de conflicto de intereses y paso a cliente. */
class ConsultaServicioTest {

	static final Cliente ALTAMIRA = new Cliente(2, TipoPersona.JURIDICA, TipoDocumento.NIT, "900111222",
			"Constructora Altamira SAS", null, null, null, "Bogotá", (short) 1, LocalDate.of(2025, 1, 1));

	ConsultasEnMemoria consultas = new ConsultasEnMemoria();
	ClienteRepositorio clientes = mock(ClienteRepositorio.class);
	ClienteServicio clienteServicio = mock(ClienteServicio.class);
	ConsultaServicio servicio;

	@BeforeEach
	void preparar() {
		given(clientes.listar(null)).willReturn(List.of(ALTAMIRA));
		given(clientes.idPorDocumento(any(), any())).willReturn(Optional.empty());
		servicio = new ConsultaServicio(consultas, clientes, clienteServicio,
				Clock.fixed(java.time.Instant.parse("2026-10-22T15:00:00Z"), ZoneId.of("America/Bogota")));
	}

	static Consulta consulta(String contraparte, String contraparteDoc) {
		return new Consulta(null, null, "  Luis   Herrera ", TipoDocumento.CC, "1020304050", "luis@correo.co", null,
				contraparte, contraparteDoc, "CIVIL", "Cobro de facturas a un proveedor", null, null, null, null, null, null);
	}

	@Test
	void avisaElConflictoSiLaContraparteYaEsClientePorNombreODocumento() {
		var porNombre = servicio.registrar(consulta("Constructora Altamira S.A.S.", null), 1L);
		var porDocumento = servicio.registrar(consulta("Otra razón social", "900111222"), 1L);
		var sinConflicto = servicio.registrar(consulta("Inversiones del Norte", null), 1L);

		assertThat(porNombre.avisos()).singleElement().asString().contains("Conflicto de intereses", "cliente 2");
		assertThat(porDocumento.avisos()).hasSize(1);
		assertThat(sinConflicto.avisos()).isEmpty();
		assertThat(porNombre.consulta().nombre()).isEqualTo("Luis Herrera");
		assertThat(porNombre.consulta().fecha()).isEqualTo(LocalDate.of(2026, 10, 22));
		assertThat(porNombre.consulta().estado()).isEqualTo(Consulta.Estado.ABIERTA);
	}

	@Test
	void contratarCreaLaFichaDeClienteYCierraLaConsulta() {
		given(clienteServicio.crear(any())).willAnswer(i -> ((Cliente) i.getArgument(0)).conId(31, LocalDate.now()));
		int id = servicio.registrar(consulta("Inversiones del Norte", null), 1L).consulta().idConsulta();

		var r = servicio.contratar(id, new ConsultaServicio.DatosCliente("Bogotá", (short) 1, "Calle 1 # 2-3"), 1L);

		assertThat(r.consulta().estado()).isEqualTo(Consulta.Estado.CONTRATADA);
		assertThat(r.consulta().idCliente()).isEqualTo(31);
		verify(clienteServicio).crear(org.mockito.ArgumentMatchers.argThat(c -> c.tipoPersona() == TipoPersona.NATURAL
				&& c.numeroDocumento().equals("1020304050") && c.nombre().equals("Luis Herrera")));
		assertThatThrownBy(() -> servicio.contratar(id, new ConsultaServicio.DatosCliente("Bogotá", (short) 1, null), 1L))
				.isInstanceOf(ConflictoException.class);
	}

	@Test
	void siQuienConsultaYaEsClienteSeEnlazaSuFicha() {
		given(clientes.idPorDocumento(TipoDocumento.CC, "1020304050")).willReturn(Optional.of(7));
		int id = servicio.registrar(consulta(null, null), 1L).consulta().idConsulta();

		assertThat(servicio.contratar(id, new ConsultaServicio.DatosCliente("Bogotá", (short) 1, null), 1L)
				.consulta().idCliente()).isEqualTo(7);
		verify(clienteServicio, never()).crear(any());
	}

	@Test
	void cerrarSinContratarPideMotivo() {
		int id = servicio.registrar(consulta(null, null), 1L).consulta().idConsulta();

		assertThatThrownBy(() -> servicio.cerrar(id, Consulta.Estado.NO_CONTRATADA, " ", 1L))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> servicio.cerrar(id, Consulta.Estado.CONTRATADA, "x", 1L))
				.isInstanceOf(ReglaNegocioException.class);
		var r = servicio.cerrar(id, Consulta.Estado.REMITIDA, "Asunto penal: se remitió a un colega", 1L);
		assertThat(r.consulta().estado()).isEqualTo(Consulta.Estado.REMITIDA);
	}

	@Test
	void validaLosDatosDeLaConsulta() {
		assertThatThrownBy(() -> new Consulta(null, null, "Ana", TipoDocumento.CC, null, null, null, null, null, null,
				"Asunto", null, null, null, null, null, null)).hasMessageContaining("tipo y el número");
		assertThatThrownBy(() -> new Consulta(null, null, " ", null, null, null, null, null, null, null,
				"Asunto", null, null, null, null, null, null)).hasMessageContaining("nombre");
		assertThatThrownBy(() -> new Consulta(null, null, "Ana", null, null, "no-es-correo", null, null, null, null,
				"Asunto", null, null, null, null, null, null)).hasMessageContaining("correo");
	}

	// ----------------------------------------------------------------- doble

	static class ConsultasEnMemoria implements ConsultaRepositorio {
		final List<Consulta> datos = new ArrayList<>();

		public Consulta guardar(Consulta c) {
			Consulta con = new Consulta(datos.size() + 1, c.fecha(), c.nombre(), c.tipoDocumento(), c.numeroDocumento(),
					c.email(), c.telefono(), c.contraparte(), c.contraparteDocumento(), c.codigoRama(), c.asunto(),
					c.descripcion(), c.idAbogado(), c.estado(), c.motivoCierre(), c.idCliente(), c.registradaPor());
			datos.add(con);
			return con;
		}
		public List<Consulta> listar(Consulta.Estado e) {
			return datos.stream().filter(c -> e == null || c.estado() == e).toList();
		}
		public Optional<Consulta> buscar(int id) {
			return datos.stream().filter(c -> c.idConsulta() == id).findFirst();
		}
		public void cerrar(int id, Consulta.Estado e, String motivo, Integer cliente) {
			Consulta c = buscar(id).orElseThrow();
			datos.set(id - 1, new Consulta(id, c.fecha(), c.nombre(), c.tipoDocumento(), c.numeroDocumento(), c.email(),
					c.telefono(), c.contraparte(), c.contraparteDocumento(), c.codigoRama(), c.asunto(), c.descripcion(),
					c.idAbogado(), e, motivo, cliente, c.registradaPor()));
		}
	}

}
