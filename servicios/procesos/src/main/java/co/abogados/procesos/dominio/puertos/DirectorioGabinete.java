package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

/**
 * Consulta al servicio gabinete los clientes y abogados que referencia un proceso.
 * Si gabinete no responde, lanza ServicioNoDisponibleException.
 */
public interface DirectorioGabinete {

	Optional<Cliente> cliente(int idCliente);

	Optional<Abogado> abogado(long idPersona);

	/** Clientes cuyo nombre o documento contienen el texto; con texto null, todos (conflictos de intereses). */
	List<ClienteResumen> buscarClientes(String texto);

	record Cliente(int idCliente, String nombre) {
	}

	record ClienteResumen(int idCliente, String nombre, String numeroDocumento) {
	}

	record Abogado(long idPersona, String nombres, String apellidos, boolean activo) {
	}

}
