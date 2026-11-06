package co.abogados.procesos.infraestructura.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import co.abogados.procesos.aplicacion.DocumentoServicio;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.Documento;

/** F10 por HTTP: subida multipart, descarga como adjunto con nosniff y el portal solo para clientes. */
@WebMvcTest(DocumentosRecurso.class)
class DocumentosRecursoMvcTest {

	@Autowired
	MockMvc mvc;

	@MockBean
	DocumentoServicio servicio;

	static Documento documento(boolean visible) {
		return new Documento(6, 31, UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"), "Poder especial.pdf",
				"application/pdf", 9L, "e3b0c4", Documento.Categoria.PODER, "Firmado", visible, 52345678L,
				Instant.parse("2026-10-15T15:00:00Z"));
	}

	static DocumentoServicio.Descarga descarga() {
		return new DocumentoServicio.Descarga(documento(true),
				new ByteArrayInputStream("%PDF-1.7".concat("\n").getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void subirDevuelve201SinElNombreInternoDelArchivo() throws Exception {
		given(servicio.subir(eq(31), eq("Poder especial.pdf"), any(), eq(Documento.Categoria.PODER), eq("Firmado"),
				eq(true), eq(52345678L))).willReturn(documento(true));

		mvc.perform(multipart("/procesos/31/documentos")
				.file(new MockMultipartFile("archivo", "Poder especial.pdf", "application/pdf", "%PDF-1.7".getBytes()))
				.param("categoria", "PODER").param("descripcion", "Firmado").param("visibleCliente", "true")
				.header("X-Usuario-Id", "52345678"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/procesos/31/documentos/6"))
				.andExpect(jsonPath("$.nombre").value("Poder especial.pdf"))
				.andExpect(jsonPath("$.archivo").doesNotExist())
				.andExpect(jsonPath("$.visibleCliente").value(true));
	}

	@Test
	void sinArchivoOConCategoriaInvalidaEsUn400() throws Exception {
		mvc.perform(multipart("/procesos/31/documentos").param("categoria", "PODER").header("X-Usuario-Id", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Adjunte el archivo y elija la categoría"));
		mvc.perform(multipart("/procesos/31/documentos")
				.file(new MockMultipartFile("archivo", "a.pdf", "application/pdf", new byte[] { 1 }))
				.param("categoria", "FOTO").header("X-Usuario-Id", "1"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(servicio);
	}

	@Test
	void unaReglaDelServicioEsUn422() throws Exception {
		given(servicio.subir(eq(31), any(), any(), any(), any(), eq(false), eq(1L)))
				.willThrow(new ReglaNegocioException("Tipo de archivo no permitido"));

		mvc.perform(multipart("/procesos/31/documentos")
				.file(new MockMultipartFile("archivo", "virus.exe", "application/octet-stream", new byte[] { 1 }))
				.param("categoria", "OTRO").header("X-Usuario-Id", "1"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("Tipo de archivo no permitido"));
	}

	@Test
	void laDescargaVaComoAdjuntoSinCacheYConNosniff() throws Exception {
		given(servicio.abrir(31, 6, 52345678L)).willReturn(descarga());

		mvc.perform(get("/procesos/31/documentos/6/archivo").header("X-Usuario-Id", "52345678"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/pdf"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Cache-Control", "private, no-store"))
				.andExpect(header().string("Content-Disposition",
						"attachment; filename=\"Poder especial.pdf\"; filename*=UTF-8''Poder%20especial.pdf"))
				.andExpect(content().string("%PDF-1.7\n"));
	}

	@Test
	void listarActualizarYEliminar() throws Exception {
		given(servicio.listar(31)).willReturn(List.of(documento(false)));
		given(servicio.actualizar(31, 6, Documento.Categoria.CONTRATO, "Copia", true, 52345678L))
				.willReturn(documento(true));

		mvc.perform(get("/procesos/31/documentos")).andExpect(jsonPath("$[0].idDocumento").value(6));
		mvc.perform(put("/procesos/31/documentos/6").header("X-Usuario-Id", "52345678")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoria\":\"CONTRATO\",\"descripcion\":\"Copia\",\"visibleCliente\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visibleCliente").value(true));
		mvc.perform(put("/procesos/31/documentos/6").header("X-Usuario-Id", "52345678")
				.contentType(MediaType.APPLICATION_JSON).content("{\"descripcion\":\"Copia\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.categoria").value("Elija la categoría"));
		mvc.perform(delete("/procesos/31/documentos/6").header("X-Usuario-Id", "52345678"))
				.andExpect(status().isNoContent());
		verify(servicio).eliminar(31, 6, 52345678L);
	}

	@Test
	void elPortalDeDocumentosEsSoloParaClientes() throws Exception {
		mvc.perform(get("/portal/procesos/31/documentos").header("X-Usuario-Roles", "ABOGADO"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/portal/procesos/31/documentos/6/archivo").header("X-Usuario-Id", "900")
				.header("X-Cliente-Id", "6").header("X-Usuario-Roles", "SOCIO"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("El portal es solo para clientes"));
		verifyNoInteractions(servicio);
	}

	@Test
	void elClienteListaYDescargaLosVisibles() throws Exception {
		given(servicio.visiblesParaCliente(6, 31)).willReturn(List.of(documento(true)));
		given(servicio.abrirParaCliente(6, 31, 6, 900L)).willReturn(descarga());

		mvc.perform(get("/portal/procesos/31/documentos").header("X-Cliente-Id", "6").header("X-Usuario-Roles", "CLIENTE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nombre").value("Poder especial.pdf"));
		mvc.perform(get("/portal/procesos/31/documentos/6/archivo").header("X-Usuario-Id", "900")
				.header("X-Cliente-Id", "6").header("X-Usuario-Roles", "CLIENTE"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

}
