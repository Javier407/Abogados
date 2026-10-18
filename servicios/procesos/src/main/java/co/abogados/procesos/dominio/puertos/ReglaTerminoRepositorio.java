package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.procesos.dominio.modelo.ReglaTermino;

public interface ReglaTerminoRepositorio {

	List<ReglaTermino> reglas();

	/** Días hábiles para contestar en ese tipo de proceso (vacío si no aplica, como en una tutela). */
	Optional<Integer> diasContestacion(short idTipo);

}
