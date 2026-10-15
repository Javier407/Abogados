package co.abogados.gabinete.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;

public interface ClienteRepositorio {

	/** texto null = todos; si no, busca en nombre y número de documento. */
	List<Cliente> listar(String texto);

	Optional<Cliente> buscar(int idCliente);

	Optional<Integer> idPorDocumento(TipoDocumento tipo, String numero);

	Cliente guardar(Cliente cliente);

	int contarHonorarios(int idCliente);

	void eliminar(int idCliente);

}
