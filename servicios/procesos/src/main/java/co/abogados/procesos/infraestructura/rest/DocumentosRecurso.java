package co.abogados.procesos.infraestructura.rest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.abogados.procesos.aplicacion.DocumentoServicio;
import co.abogados.procesos.dominio.modelo.Documento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Expediente digital (F10). El personal sube, lista, descarga, cambia y borra (los permisos por rol los pone el
 * gateway); el cliente, en /portal, solo lista y descarga los visibles de sus procesos.
 * Las descargas van siempre como adjunto y con nosniff: el navegador no interpreta el archivo dentro de la app.
 */
@RestController
public class DocumentosRecurso {

	/** Lo que se muestra de un documento (sin el nombre interno del archivo). */
	public record DocumentoVista(int idDocumento, int idProceso, String nombre, String tipoMime, long tamano,
			String sha256, Documento.Categoria categoria, String descripcion, boolean visibleCliente, long subidoPor,
			Instant subido) {
		static DocumentoVista de(Documento d) {
			return new DocumentoVista(d.idDocumento(), d.idProceso(), d.nombre(), d.tipoMime(), d.tamano(), d.sha256(),
					d.categoria(), d.descripcion(), d.visibleCliente(), d.subidoPor(), d.subido());
		}
	}

	public record CambioDocumento(@NotNull(message = "Elija la categoría") Documento.Categoria categoria,
			@Size(max = 300) String descripcion, boolean visibleCliente) {
	}

	private final DocumentoServicio servicio;

	public DocumentosRecurso(DocumentoServicio servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/procesos/{idProceso}/documentos")
	public List<DocumentoVista> listar(@PathVariable int idProceso) {
		return servicio.listar(idProceso).stream().map(DocumentoVista::de).toList();
	}

	@PostMapping(path = "/procesos/{idProceso}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<DocumentoVista> subir(@PathVariable int idProceso, @RequestParam("archivo") MultipartFile archivo,
			@RequestParam("categoria") Documento.Categoria categoria,
			@RequestParam(name = "descripcion", required = false) String descripcion,
			@RequestParam(name = "visibleCliente", defaultValue = "false") boolean visibleCliente,
			@RequestHeader("X-Usuario-Id") long usuario) {
		try {
			Documento d = servicio.subir(idProceso, archivo.getOriginalFilename(), archivo.getInputStream(), categoria,
					descripcion, visibleCliente, usuario);
			return ResponseEntity.created(URI.create("/procesos/" + idProceso + "/documentos/" + d.idDocumento()))
					.body(DocumentoVista.de(d));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@GetMapping("/procesos/{idProceso}/documentos/{idDocumento}/archivo")
	public ResponseEntity<InputStreamResource> descargar(@PathVariable int idProceso, @PathVariable int idDocumento,
			@RequestHeader("X-Usuario-Id") long usuario) {
		return archivo(servicio.abrir(idProceso, idDocumento, usuario));
	}

	@PutMapping("/procesos/{idProceso}/documentos/{idDocumento}")
	public DocumentoVista actualizar(@PathVariable int idProceso, @PathVariable int idDocumento,
			@Valid @RequestBody CambioDocumento c, @RequestHeader("X-Usuario-Id") long usuario) {
		return DocumentoVista.de(servicio.actualizar(idProceso, idDocumento, c.categoria(), c.descripcion(),
				c.visibleCliente(), usuario));
	}

	@DeleteMapping("/procesos/{idProceso}/documentos/{idDocumento}")
	public ResponseEntity<Void> eliminar(@PathVariable int idProceso, @PathVariable int idDocumento,
			@RequestHeader("X-Usuario-Id") long usuario) {
		servicio.eliminar(idProceso, idDocumento, usuario);
		return ResponseEntity.noContent().build();
	}

	// ------------------------------------------------------------------ portal del cliente

	@GetMapping("/portal/procesos/{idProceso}/documentos")
	public ResponseEntity<?> delCliente(@PathVariable int idProceso,
			@RequestHeader(name = "X-Cliente-Id", required = false) Integer cliente,
			@RequestHeader(name = "X-Usuario-Roles", required = false) String roles) {
		if (!esCliente(cliente, roles)) {
			return prohibido();
		}
		return ResponseEntity.ok(servicio.visiblesParaCliente(cliente, idProceso).stream().map(DocumentoVista::de).toList());
	}

	@GetMapping("/portal/procesos/{idProceso}/documentos/{idDocumento}/archivo")
	public ResponseEntity<?> descargarCliente(@PathVariable int idProceso, @PathVariable int idDocumento,
			@RequestHeader(name = "X-Cliente-Id", required = false) Integer cliente,
			@RequestHeader(name = "X-Usuario-Roles", required = false) String roles,
			@RequestHeader("X-Usuario-Id") long usuario) {
		if (!esCliente(cliente, roles)) {
			return prohibido();
		}
		return archivo(servicio.abrirParaCliente(cliente, idProceso, idDocumento, usuario));
	}

	// ------------------------------------------------------------------

	private static ResponseEntity<InputStreamResource> archivo(DocumentoServicio.Descarga d) {
		Documento doc = d.documento();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(doc.tipoMime()))
				.contentLength(doc.tamano())
				.header(HttpHeaders.CONTENT_DISPOSITION, adjunto(doc.nombre()))
				.header("X-Content-Type-Options", "nosniff")
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
				.body(new InputStreamResource(d.contenido()));
	}

	/**
	 * attachment; filename="version ASCII"; filename*=UTF-8''nombre%20real (RFC 6266). Los navegadores usan filename*;
	 * filename queda para los que no lo entienden.
	 */
	static String adjunto(String nombre) {
		String ascii = java.text.Normalizer.normalize(nombre, java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "").replace("\\", "_");
		String utf8 = java.net.URLEncoder.encode(nombre, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
		return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + utf8;
	}

	private static boolean esCliente(Integer cliente, String roles) {
		return cliente != null && roles != null && Arrays.asList(roles.split(",")).contains("CLIENTE");
	}

	private static ResponseEntity<Map<String, String>> prohibido() {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "El portal es solo para clientes"));
	}

}
