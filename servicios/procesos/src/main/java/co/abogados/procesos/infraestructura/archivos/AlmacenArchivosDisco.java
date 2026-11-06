package co.abogados.procesos.infraestructura.archivos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.puertos.AlmacenArchivos;

/**
 * Archivos en disco (en el servidor, el volumen "documentos", que también entra en la copia diaria).
 * Ruta: base/ab/abcdef...-uuid. Se escribe primero en un temporal y se mueve al final, así nunca queda un
 * archivo a medias con el nombre definitivo.
 */
@Component
public class AlmacenArchivosDisco implements AlmacenArchivos {

	private final Path base;

	public AlmacenArchivosDisco(@Value("${documentos.ruta:/datos/documentos}") String ruta) {
		this.base = Path.of(ruta);
	}

	@Override
	public Guardado guardar(UUID archivo, InputStream contenido, long maximoBytes) {
		Path destino = ruta(archivo);
		Path temporal = destino.resolveSibling(archivo + ".parcial");
		try {
			Files.createDirectories(destino.getParent());
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			long total = 0;
			try (InputStream entrada = new DigestInputStream(contenido, sha);
					OutputStream salida = Files.newOutputStream(temporal)) {
				byte[] bloque = new byte[64 * 1024];
				for (int n; (n = entrada.read(bloque)) != -1;) {
					total += n;
					if (total > maximoBytes) {
						throw new ReglaNegocioException("El archivo pasa del máximo permitido ("
								+ maximoBytes / (1024 * 1024) + " MB)");
					}
					salida.write(bloque, 0, n);
				}
			}
			Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
			return new Guardado(total, HexFormat.of().formatHex(sha.digest()));
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo guardar el archivo", e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		} finally {
			try {
				Files.deleteIfExists(temporal);
			} catch (IOException e) {
				// nada que hacer: el temporal quedará como basura identificable (.parcial)
			}
		}
	}

	@Override
	public InputStream abrir(UUID archivo) {
		try {
			return Files.newInputStream(ruta(archivo));
		} catch (java.nio.file.NoSuchFileException e) {
			throw new RecursoNoEncontradoException("El archivo del documento no está en el almacén");
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo abrir el archivo", e);
		}
	}

	@Override
	public void borrar(UUID archivo) {
		try {
			Files.deleteIfExists(ruta(archivo));
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo borrar el archivo", e);
		}
	}

	private Path ruta(UUID archivo) {
		String nombre = archivo.toString();
		return base.resolve(nombre.substring(0, 2)).resolve(nombre);
	}

}
