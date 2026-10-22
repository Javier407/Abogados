package co.abogados.procesos.dominio.puertos;

import java.io.InputStream;
import java.util.UUID;

/** Dónde se guarda el contenido de los documentos (en el servidor: el volumen "documentos"). */
public interface AlmacenArchivos {

	/** Tamaño y SHA-256 de lo que se guardó. */
	record Guardado(long tamano, String sha256) {
	}

	/**
	 * Guarda el contenido con ese nombre. Si pasa de maximoBytes, no guarda nada y lanza ReglaNegocioException.
	 */
	Guardado guardar(UUID archivo, InputStream contenido, long maximoBytes);

	InputStream abrir(UUID archivo);

	/** Borra el archivo (si no existe, no hace nada). */
	void borrar(UUID archivo);

}
