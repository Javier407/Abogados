package co.abogados.gabinete.infraestructura.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.gabinete.aplicacion.AbogadoServicio;
import co.abogados.gabinete.aplicacion.PeritoServicio;
import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.modelo.Abogado;
import co.abogados.gabinete.dominio.modelo.Perito;

@WebMvcTest({ AbogadosRecurso.class, PeritosRecurso.class })
class AbogadosYPeritosRecursoTest {

	static final Abogado LAURA = new Abogado(1020456789L, "Laura", "Pineda", "lpineda@firma.co", null, (short) 2,
			"Abogado asociado", (short) 1, "Sede Bogotá", null, null, "201334", new BigDecimal("260000"),
			LocalDate.of(2018, 8, 13), true);

	static final Perito CLAUDIA = new Perito(3, "Claudia Ruiz", "Ingeniería civil", "Ingeniera civil", "IC-123", null,
			null, "Bogotá", new BigDecimal("4500000"), (short) 5);

	static final String ABOGADO = """
			{ "idPersona": 1020456789, "nombres": "Laura", "apellidos": "Pineda", "email": "LPineda@firma.co",
			  "idCargo": 2, "idSede": 1, "tarjetaProfesional": "201334", "tarifaHora": 260000,
			  "fechaIngreso": "2018-08-13" }
			""";

	static final String PERITO = """
			{ "nombre": "Claudia Ruiz", "especialidad": "Ingeniería civil", "profesion": "Ingeniera civil",
			  "ciudad": "Bogotá", "tarifaDictamen": 4500000, "calificacion": 5 }
			""";

	@Autowired
	MockMvc mvc;

	@MockBean
	AbogadoServicio abogados;

	@MockBean
	PeritoServicio peritos;

	@Test
	void crearAbogadoDevuelve201YLlegaActivoConElCorreoEnMinusculas() throws Exception {
		given(abogados.crear(any())).willReturn(LAURA);

		mvc.perform(post("/abogados").contentType(MediaType.APPLICATION_JSON).content(ABOGADO))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/abogados/1020456789"))
				.andExpect(jsonPath("$.cargo").value("Abogado asociado"));

		verify(abogados).crear(argThat(a -> a.activo() && a.email().equals("lpineda@firma.co")
				&& a.fechaIngreso().equals(LocalDate.of(2018, 8, 13))));
	}

	@Test
	void validacionesDelAbogado() throws Exception {
		mvc.perform(post("/abogados").contentType(MediaType.APPLICATION_JSON)
				.content(ABOGADO.replace("2018-08-13", "2999-01-01")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.fechaIngreso").value("La fecha de ingreso no puede ser futura"));
		mvc.perform(post("/abogados").contentType(MediaType.APPLICATION_JSON)
				.content(ABOGADO.replace("260000", "-1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.tarifaHora").value("La tarifa no puede ser negativa"));
		mvc.perform(post("/abogados").contentType(MediaType.APPLICATION_JSON)
				.content(ABOGADO.replace("\"201334\"", "\"TP-201334\"")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("La tarjeta profesional es obligatoria y solo lleva números"));
		verifyNoInteractions(abogados);
	}

	@Test
	void unaTarjetaRepetidaEsUn409() throws Exception {
		given(abogados.crear(any())).willThrow(new ConflictoException("Ya existe un abogado con la tarjeta 201334"));

		mvc.perform(post("/abogados").contentType(MediaType.APPLICATION_JSON).content(ABOGADO))
				.andExpect(status().isConflict());
	}

	@Test
	void listarActualizarYDesactivarAbogados() throws Exception {
		given(abogados.listar(true)).willReturn(List.of(LAURA));
		given(abogados.obtener(1020456789L)).willReturn(LAURA);
		given(abogados.actualizar(eq(1020456789L), any())).willReturn(LAURA);
		given(abogados.desactivar(1020456789L)).willReturn(LAURA);

		mvc.perform(get("/abogados").param("soloActivos", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].idPersona").value(1020456789));
		mvc.perform(get("/abogados/1020456789")).andExpect(status().isOk());
		mvc.perform(put("/abogados/1020456789").contentType(MediaType.APPLICATION_JSON)
				.content(ABOGADO.replace("\"fechaIngreso\": \"2018-08-13\"", "\"activo\": false")))
				.andExpect(status().isOk());
		verify(abogados).actualizar(eq(1020456789L), argThat(a -> !a.activo()));
		mvc.perform(delete("/abogados/1020456789")).andExpect(status().isOk());
		verify(abogados).desactivar(1020456789L);
	}

	@Test
	void crearPeritoDevuelve201() throws Exception {
		given(peritos.crear(any())).willReturn(CLAUDIA);

		mvc.perform(post("/peritos").contentType(MediaType.APPLICATION_JSON).content(PERITO))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/peritos/3"));
	}

	@Test
	void validacionesDelPerito() throws Exception {
		mvc.perform(post("/peritos").contentType(MediaType.APPLICATION_JSON).content(PERITO.replace("\"calificacion\": 5", "\"calificacion\": 6")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.calificacion").value("La calificación va de 1 a 5"));
		mvc.perform(post("/peritos").contentType(MediaType.APPLICATION_JSON).content(PERITO.replace("\"Bogotá\"", "\"\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.ciudad").value("La ciudad es obligatoria"));
		verifyNoInteractions(peritos);
	}

	@Test
	void buscarYActualizarPeritos() throws Exception {
		given(peritos.listar("civil")).willReturn(List.of(CLAUDIA));
		given(peritos.obtener(99)).willThrow(new RecursoNoEncontradoException("No existe el perito 99"));
		given(peritos.actualizar(eq(3), any())).willReturn(CLAUDIA);

		mvc.perform(get("/peritos").param("q", "civil"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nombre").value("Claudia Ruiz"));
		mvc.perform(get("/peritos/99")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("No existe el perito 99"));
		mvc.perform(put("/peritos/3").contentType(MediaType.APPLICATION_JSON).content(PERITO))
				.andExpect(status().isOk());
		verify(peritos).actualizar(eq(3), argThat(p -> p.idPerito() == null && p.calificacion() == 5));
	}

}
