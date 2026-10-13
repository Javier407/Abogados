package co.edu.unbosque.ciclo3back;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pruebas de integración de las 3 capas (API -> Service -> DAO -> H2).
 * Parten de los datos de data-h2.sql; @Transactional deshace los cambios
 * de cada prueba para que no se afecten entre sí.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
class GabineteAPITests {

	@Autowired
	private MockMvc mvc;

	private static final String CLIENTE_NUEVO = "{\"id_cliente\":1111222333,\"nombre_cliente\":\"Pedro Perez\","
			+ "\"direccion_cliente\":\"Cll 1 # 2-3\",\"telefono_cliente\":\"3000000000\",\"email_cliente\":\"pedro@correo.com\"}";

	private String asunto(String exp, String inicio, String archivo, String estado, long cliente, String abogados) {
		return "{\"numero_expediente\":\"" + exp + "\",\"fecha_inicio\":\"" + inicio + "\","
				+ "\"fecha_archivo\":" + (archivo == null ? "null" : "\"" + archivo + "\"") + ","
				+ "\"estado\":\"" + estado + "\",\"id_cliente\":" + cliente + ",\"ids_abogados\":" + abogados + "}";
	}

	// ------------------------- Clientes -------------------------

	@Test
	void listarClientes() throws Exception {
		mvc.perform(get("/clientes/listar")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)));
	}

	@Test
	void crearClienteValido() throws Exception {
		mvc.perform(post("/clientes/guardar").contentType(MediaType.APPLICATION_JSON).content(CLIENTE_NUEVO))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre_cliente").value("Pedro Perez"));
	}

	@Test
	void crearClienteDuplicadoDa409() throws Exception {
		mvc.perform(post("/clientes/guardar").contentType(MediaType.APPLICATION_JSON)
				.content(CLIENTE_NUEVO.replace("1111222333", "1020304050")))
				.andExpect(status().isConflict());
	}

	@Test
	void crearClienteConEmailInvalidoDa400() throws Exception {
		mvc.perform(post("/clientes/guardar").contentType(MediaType.APPLICATION_JSON)
				.content(CLIENTE_NUEVO.replace("pedro@correo.com", "pedro-correo")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.email_cliente").exists());
	}

	@Test
	void eliminarClienteConAsuntosDa409() throws Exception {
		mvc.perform(delete("/clientes/eliminar/1020304050"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error", containsString("asunto")));
	}

	// ------------------------- Abogados -------------------------

	@Test
	void crearAbogadoConTarjetaRepetidaDa409() throws Exception {
		String abogado = "{\"id_abogado\":12345678,\"tarjeta_profesional\":\"145236\",\"nombre_abogado\":\"Otro Abogado\","
				+ "\"direccion_abogado\":\"Cll 9 # 9-9\",\"telefono_abogado\":\"3009998877\",\"email_abogado\":\"otro@gabinete.com\"}";
		mvc.perform(post("/abogados/guardar").contentType(MediaType.APPLICATION_JSON).content(abogado))
				.andExpect(status().isConflict());
	}

	@Test
	void eliminarAbogadoConAsuntosDa409() throws Exception {
		mvc.perform(delete("/abogados/eliminar/79555111")).andExpect(status().isConflict());
	}

	// ------------------- Abogado ↔ cuenta de usuario (1 : 0..1) -------------------

	private static final String ANDRES = "{\"id_abogado\":80333444,\"tarjeta_profesional\":\"210987\",\"nombre_abogado\":\"Andres Lopez\","
			+ "\"direccion_abogado\":\"Cll 85 # 19-32, Bogotá\",\"telefono_abogado\":\"3014445566\",\"email_abogado\":\"alopez@gabinete.com\","
			+ "\"cedula_usuario\":%s}";

	@Test
	void abogadoMuestraSuCuentaSinExponerLaContrasena() throws Exception {
		mvc.perform(get("/abogados/79555111"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cedula_usuario").value(79555111))
				.andExpect(jsonPath("$.usuario").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());
		mvc.perform(get("/abogados/80333444")).andExpect(jsonPath("$.cedula_usuario").doesNotExist());
	}

	@Test
	void vincularCuentaLibreAUnAbogado() throws Exception {
		mvc.perform(put("/abogados/actualizar").contentType(MediaType.APPLICATION_JSON).content(String.format(ANDRES, "123456789")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cedula_usuario").value(123456789));
	}

	@Test
	void vincularCuentaInexistenteDa400() throws Exception {
		mvc.perform(put("/abogados/actualizar").contentType(MediaType.APPLICATION_JSON).content(String.format(ANDRES, "999")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", containsString("No existe un usuario")));
	}

	@Test
	void vincularCuentaDeOtroAbogadoDa409() throws Exception {
		mvc.perform(put("/abogados/actualizar").contentType(MediaType.APPLICATION_JSON).content(String.format(ANDRES, "79555111")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error", containsString("ya está vinculado")));
	}

	@Test
	void eliminarUsuarioQueEsCuentaDeAbogadoDa409() throws Exception {
		mvc.perform(delete("/usuarios/eliminar/79555111")).andExpect(status().isConflict());
	}

	// ------------------------- Asuntos -------------------------

	@Test
	void crearAsuntoConVariosAbogados() throws Exception {
		mvc.perform(post("/asuntos/guardar").contentType(MediaType.APPLICATION_JSON)
				.content(asunto("EXP-2025-010", "2025-06-01", null, "EN_TRAMITE", 1032165498L, "[79555111,52444222]")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.cliente.nombre_cliente").value("Ana Gomez"))
				.andExpect(jsonPath("$.abogados", hasSize(2)));
	}

	@Test
	void crearAsuntoSinAbogados() throws Exception {
		mvc.perform(post("/asuntos/guardar").contentType(MediaType.APPLICATION_JSON)
				.content(asunto("EXP-2025-011", "2025-06-01", null, "EN_TRAMITE", 1032165498L, "[]")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.abogados", hasSize(0)));
	}

	@Test
	void crearAsuntoConExpedienteRepetidoDa409() throws Exception {
		mvc.perform(post("/asuntos/guardar").contentType(MediaType.APPLICATION_JSON)
				.content(asunto("EXP-2025-001", "2025-06-01", null, "EN_TRAMITE", 1032165498L, "[]")))
				.andExpect(status().isConflict());
	}

	@Test
	void crearAsuntoConClienteInexistenteDa400() throws Exception {
		mvc.perform(post("/asuntos/guardar").contentType(MediaType.APPLICATION_JSON)
				.content(asunto("EXP-2025-012", "2025-06-01", null, "EN_TRAMITE", 999L, "[]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", containsString("cliente")));
	}

	@Test
	void crearAsuntoConAbogadoInexistenteDa400() throws Exception {
		mvc.perform(post("/asuntos/guardar").contentType(MediaType.APPLICATION_JSON)
				.content(asunto("EXP-2025-013", "2025-06-01", null, "EN_TRAMITE", 1032165498L, "[79555111,777]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", containsString("777")));
	}

	@Test
	void asuntoArchivadoSinFechaDa400() throws Exception {
		mvc.perform(post("/asuntos/guardar").contentType(MediaType.APPLICATION_JSON)
				.content(asunto("EXP-2025-014", "2025-06-01", null, "ARCHIVADO", 1032165498L, "[]")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void fechaArchivoAnteriorAInicioDa400() throws Exception {
		mvc.perform(post("/asuntos/guardar").contentType(MediaType.APPLICATION_JSON)
				.content(asunto("EXP-2025-015", "2025-06-01", "2025-05-01", "FINALIZADO", 1032165498L, "[]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", containsString("anterior")));
	}

	@Test
	void expedienteVacioDaSoloMensajeDeObligatorio() throws Exception {
		mvc.perform(post("/asuntos/guardar").contentType(MediaType.APPLICATION_JSON)
				.content(asunto("", "2025-06-01", null, "EN_TRAMITE", 1032165498L, "[]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.numero_expediente").value("El número de expediente es obligatorio"));
	}

	@Test
	void estadoInexistenteDa400() throws Exception {
		mvc.perform(post("/asuntos/guardar").contentType(MediaType.APPLICATION_JSON)
				.content(asunto("EXP-2025-016", "2025-06-01", null, "CERRADO", 1032165498L, "[]")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void consultarAsuntosPorClienteAbogadoYEstado() throws Exception {
		mvc.perform(get("/asuntos/cliente/1020304050")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
		mvc.perform(get("/asuntos/abogado/79555111")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
		mvc.perform(get("/asuntos/estado/EN_TRAMITE")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
	}

	@Test
	void archivarAsunto() throws Exception {
		mvc.perform(put("/asuntos/EXP-2025-001/archivar").param("fecha", "2025-12-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ARCHIVADO"))
				.andExpect(jsonPath("$.fecha_archivo").value("2025-12-01"));
	}

	@Test
	void archivarAsuntoYaArchivadoDa400() throws Exception {
		mvc.perform(put("/asuntos/EXP-2025-002/archivar")).andExpect(status().isBadRequest());
	}

	@Test
	void asuntoInexistenteDa404() throws Exception {
		mvc.perform(get("/asuntos/NO-EXISTE")).andExpect(status().isNotFound());
	}

	@Test
	void eliminarAsunto() throws Exception {
		mvc.perform(delete("/asuntos/eliminar/EXP-2026-003")).andExpect(status().isNoContent());
		mvc.perform(get("/asuntos/EXP-2026-003")).andExpect(status().isNotFound());
	}

}
