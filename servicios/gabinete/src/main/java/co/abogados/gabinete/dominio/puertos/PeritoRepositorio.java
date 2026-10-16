package co.abogados.gabinete.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.gabinete.dominio.modelo.Perito;

public interface PeritoRepositorio {

	/** texto null = todos; si no, busca en nombre y especialidad. */
	List<Perito> listar(String texto);

	Optional<Perito> buscar(int idPerito);

	Perito guardar(Perito perito);

}
