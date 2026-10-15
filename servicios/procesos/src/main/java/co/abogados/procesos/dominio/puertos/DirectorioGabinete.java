package co.abogados.procesos.dominio.puertos;

import java.util.Optional;

/**
 * Consulta al servicio gabinete los clientes y abogados que referencia un proceso.
 * Si gabinete no responde, lanza ServicioNoDisponibleException.
 */
public interface DirectorioGabinete {

	Optional<Cliente> cliente(int idCliente);

	Optional<Abogado> abogado(long idPersona);

	record Cliente(int idCliente, String nombre) {
	}

	record Abogado(long idPersona, String nombres, String apellidos, boolean activo) {
	}

}
