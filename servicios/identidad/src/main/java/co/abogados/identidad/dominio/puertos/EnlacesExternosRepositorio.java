package co.abogados.identidad.dominio.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import co.abogados.identidad.dominio.AccesoExterno.Enlace;
import co.abogados.identidad.dominio.AccesoExterno.Estado;
import co.abogados.identidad.dominio.AccesoExterno.Identidad;
import co.abogados.identidad.dominio.AccesoExterno.Proveedor;

public interface EnlacesExternosRepositorio {

	/** A quién pertenece la cuenta externa (proveedor + sujeto), si está enlazada. */
	Optional<Long> duenoDe(Proveedor proveedor, String sujeto);

	/** ¿La persona ya tiene una cuenta de ese proveedor? */
	boolean tieneEnlace(long idUsuario, Proveedor proveedor);

	void enlazar(long idUsuario, Identidad identidad, Instant ahora);

	void registrarUso(Proveedor proveedor, String sujeto, Instant ahora);

	List<Enlace> enlaces(long idUsuario);

	/** true si había un enlace de ese proveedor y se quitó. */
	boolean quitar(long idUsuario, Proveedor proveedor);

	void guardarEstado(Estado estado);

	/** Lee y borra el estado (un solo uso). Vacío si no existe. */
	Optional<Estado> tomarEstado(String hashEstado);

	/** Borra los estados vencidos (flujos abandonados). */
	void limpiarVencidos(Instant ahora);

}
