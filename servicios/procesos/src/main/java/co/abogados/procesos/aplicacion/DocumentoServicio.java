package co.abogados.procesos.aplicacion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.Documento;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.TipoArchivo;
import co.abogados.procesos.dominio.puertos.AlmacenArchivos;
import co.abogados.procesos.dominio.puertos.DocumentoRepositorio;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;

/**
 * Expediente digital (F10).
 * - Se aceptan PDF, Word, Excel, imágenes y texto, de hasta 20 MB; el tipo se comprueba por el contenido.
 * - El archivo se guarda con un nombre aleatorio; si guardar los datos falla, se borra el archivo.
 * - El cliente solo ve (y descarga) los documentos que el abogado marcó como visibles, y solo de sus procesos.
 * - Cada subida, descarga y borrado queda en el log con quién lo hizo.
 */
@Service
public class DocumentoServicio {

	private static final Logger log = LoggerFactory.getLogger(DocumentoServicio.class);

	/** Un documento abierto para descargar: sus datos y el contenido. */
	public record Descarga(Documento documento, InputStream contenido) {
	}

	private final DocumentoRepositorio documentos;
	private final ProcesoRepositorio procesos;
	private final AlmacenArchivos almacen;
	private final Clock reloj;
	private final long maximoBytes;

	public DocumentoServicio(DocumentoRepositorio documentos, ProcesoRepositorio procesos, AlmacenArchivos almacen,
			Clock reloj, @Value("${documentos.maximo-mb:20}") long maximoMb) {
		this.documentos = documentos;
		this.procesos = procesos;
		this.almacen = almacen;
		this.reloj = reloj;
		this.maximoBytes = maximoMb * 1024 * 1024;
	}

	public Documento subir(int idProceso, String nombreOriginal, InputStream contenido, Documento.Categoria categoria,
			String descripcion, boolean visibleCliente, long usuario) {
		existe(idProceso);
		String nombre = Documento.nombreSeguro(nombreOriginal);
		BufferedInputStream entrada = new BufferedInputStream(contenido);
		String mime = TipoArchivo.detectar(nombre, primerosBytes(entrada));
		UUID archivo = UUID.randomUUID();
		AlmacenArchivos.Guardado g = almacen.guardar(archivo, entrada, maximoBytes);
		if (g.tamano() == 0) {
			almacen.borrar(archivo);
			throw new ReglaNegocioException("El archivo está vacío");
		}
		try {
			Documento d = documentos.guardar(new Documento(null, idProceso, archivo, nombre, mime, g.tamano(), g.sha256(),
					categoria, descripcion, visibleCliente, usuario, Instant.now(reloj)));
			log.info("Documento {} ({}, {} bytes) subido al proceso {} por {}", d.idDocumento(), nombre, g.tamano(),
					idProceso, usuario);
			return d;
		} catch (RuntimeException e) {
			almacen.borrar(archivo);   // sin datos, el archivo no debe quedar huérfano
			throw e;
		}
	}

	public List<Documento> listar(int idProceso) {
		existe(idProceso);
		return documentos.listar(idProceso, false);
	}

	public Descarga abrir(int idProceso, int idDocumento, long usuario) {
		Documento d = buscar(idProceso, idDocumento);
		log.info("Documento {} del proceso {} descargado por {}", idDocumento, idProceso, usuario);
		return new Descarga(d, almacen.abrir(d.archivo()));
	}

	public Documento actualizar(int idProceso, int idDocumento, Documento.Categoria categoria, String descripcion,
			boolean visibleCliente, long usuario) {
		Documento d = buscar(idProceso, idDocumento);
		Documento cambiado = new Documento(d.idDocumento(), idProceso, d.archivo(), d.nombre(), d.tipoMime(), d.tamano(),
				d.sha256(), categoria, descripcion, visibleCliente, d.subidoPor(), d.subido());   // valida
		documentos.actualizar(idDocumento, cambiado.categoria(), cambiado.descripcion(), visibleCliente);
		if (d.visibleCliente() != visibleCliente) {
			log.info("Documento {} del proceso {} {} para el cliente por {}", idDocumento, idProceso,
					visibleCliente ? "visible" : "oculto", usuario);
		}
		return cambiado;
	}

	public void eliminar(int idProceso, int idDocumento, long usuario) {
		Documento d = buscar(idProceso, idDocumento);
		documentos.eliminar(idDocumento);
		almacen.borrar(d.archivo());
		log.info("Documento {} ({}) del proceso {} borrado por {}", idDocumento, d.nombre(), idProceso, usuario);
	}

	// ------------------------------------------------------------------ portal del cliente

	public List<Documento> visiblesParaCliente(int idCliente, int idProceso) {
		delCliente(idCliente, idProceso);
		return documentos.listar(idProceso, true);
	}

	public Descarga abrirParaCliente(int idCliente, int idProceso, int idDocumento, long usuario) {
		delCliente(idCliente, idProceso);
		Documento d = documentos.buscar(idProceso, idDocumento).filter(Documento::visibleCliente)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el documento " + idDocumento));
		log.info("Documento {} del proceso {} descargado por el cliente {} (cuenta {})", idDocumento, idProceso,
				idCliente, usuario);
		return new Descarga(d, almacen.abrir(d.archivo()));
	}

	// ------------------------------------------------------------------

	private ProcesoVista existe(int idProceso) {
		return procesos.buscar(idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
	}

	/** Un proceso de otro cliente "no existe" para este cliente (no se revela que existe). */
	private void delCliente(int idCliente, int idProceso) {
		procesos.buscar(idProceso).filter(p -> p.idCliente() == idCliente)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
	}

	private Documento buscar(int idProceso, int idDocumento) {
		return documentos.buscar(idProceso, idDocumento)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el documento " + idDocumento));
	}

	private static byte[] primerosBytes(BufferedInputStream entrada) {
		try {
			entrada.mark(512);
			byte[] inicio = entrada.readNBytes(512);
			entrada.reset();
			return inicio;
		} catch (IOException e) {
			throw new ReglaNegocioException("No se pudo leer el archivo");
		}
	}

}
