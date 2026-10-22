package co.abogados.gabinete.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.gabinete.dominio.modelo.Consulta;

/** Consultas iniciales (gabinete.consultas). */
public interface ConsultaRepositorio {

	Consulta guardar(Consulta nueva);

	/** De la más reciente a la más antigua; estado null = todas. */
	List<Consulta> listar(Consulta.Estado estado);

	Optional<Consulta> buscar(int idConsulta);

	void cerrar(int idConsulta, Consulta.Estado estado, String motivo, Integer idCliente);

}
