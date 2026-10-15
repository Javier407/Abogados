package co.abogados.gabinete.infraestructura.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.gabinete.aplicacion.ClienteServicio;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.modelo.TipoPersona;

@WebMvcTest(ClientesRecurso.class)
class ClientesRecursoTest {

	@Autowired
	MockMvc mvc;

	@MockBean
	ClienteServicio servicio;

	@Test
	void crearDevuelve201() throws Exception {
		given(servicio.crear(any())).willReturn(new Cliente(30, TipoPersona.JURIDICA, TipoDocumento.NIT, "901222333",
				"Textiles del Norte SAS", null, null, null, "Medellín", (short) 2, LocalDate.of(2026, 10, 15)));

		mvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content("""
				{ "tipoPersona": "JURIDICA", "tipoDocumento": "NIT", "numeroDocumento": "901222333",
				  "nombre": "Textiles del Norte SAS", "ciudad": "Medellín", "idSede": 2 }
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/clientes/30"))
				.andExpect(jsonPath("$.email").doesNotExist());
	}

	@Test
	void nitConPersonaNaturalEsUn422() throws Exception {
		mvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content("""
				{ "tipoPersona": "NATURAL", "tipoDocumento": "NIT", "numeroDocumento": "901222333",
				  "nombre": "Ana", "ciudad": "Cali", "idSede": 1 }
				"""))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void correoInvalidoEsUn400() throws Exception {
		mvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content("""
				{ "tipoPersona": "NATURAL", "tipoDocumento": "CC", "numeroDocumento": "52678123",
				  "nombre": "Ana", "email": "no-es-correo", "ciudad": "Cali", "idSede": 1 }
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.email").value("El correo no es válido"));
	}

	@Test
	void clienteInexistenteEsUn404() throws Exception {
		given(servicio.obtener(99)).willThrow(new RecursoNoEncontradoException("No existe el cliente 99"));

		mvc.perform(get("/clientes/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("No existe el cliente 99"));
	}

}
