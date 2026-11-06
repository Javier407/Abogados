package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.procesos.dominio.modelo.CambioRegla;
import co.abogados.procesos.dominio.modelo.ReglaDatos;
import co.abogados.procesos.dominio.modelo.ReglaTermino;
import co.abogados.procesos.dominio.modelo.ReglaVista;

public interface ReglaTerminoRepositorio {

	/** Las reglas activas, para sugerir términos. */
	List<ReglaTermino> reglas();

	/** Días hábiles para contestar en ese tipo de proceso (vacío si no aplica, como en una tutela). */
	Optional<Integer> diasContestacion(short idTipo);

	// ------------------------------------------------------------------ edición (F12)

	/** Todas, activas o no. */
	List<ReglaVista> listar();

	Optional<ReglaVista> buscar(int idRegla);

	/** Crea la regla sin validar y devuelve su id. */
	int crear(ReglaDatos datos, long usuario);

	/** Guarda el contenido; con desvalidar, la regla queda otra vez sin validar. */
	void actualizar(int idRegla, ReglaDatos datos, long usuario, boolean desvalidar);

	void validar(int idRegla, long usuario);

	/** Deja la regla sin validar (cambió algo de lo que depende, como los días de contestación). */
	void desvalidar(int idRegla, long usuario);

	void registrar(int idRegla, long usuario, String accion, String detalle);

	/** Del más reciente al más antiguo. */
	List<CambioRegla> historial(int idRegla);

	void cambiarContestacion(short idTipo, Integer dias);

}
