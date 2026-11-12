package co.abogados.gateway.auditoria;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * La bitácora de auditoría: se anota cada acceso y se consulta y revisa la cadena. Anotar nunca bloquea ni falla:
 * si la base no responde, el acceso espera en memoria (y queda además en la línea AUDITORIA del log).
 */
public interface Bitacora {

	void anotar(Acceso acceso);

	/** Los más recientes primero. */
	List<Registro> buscar(Filtro filtro);

	Optional<Registro> registro(long id);

	/** Revisa la cadena desde el principio (selloId 0) o desde un sello ya conocido. */
	Cadena.Verificacion verificar(long selloId, String selloHash);

	/** Cómo va la escritura: accesos esperando a guardarse y perdidos porque la espera se llenó. */
	Estado estado();

	/**
	 * Filtro de búsqueda. Los null no filtran. antesDe: para pedir la página siguiente (número del último que se vio).
	 * soloFallos: respuestas 4xx y 5xx, y peticiones cortadas.
	 */
	record Filtro(String usuario, String metodo, String ruta, boolean soloFallos, Instant desde, Instant hasta,
			Long antesDe, int limite) {

		public Filtro {
			limite = Math.max(1, Math.min(limite, 200));
		}

		boolean incluye(Registro r) {
			Acceso a = r.acceso();
			return (usuario == null || usuario.equals(a.usuario()))
					&& (metodo == null || metodo.equalsIgnoreCase(a.metodo()))
					&& (ruta == null || a.ruta().toLowerCase().contains(ruta.toLowerCase()))
					&& (!soloFallos || a.estado() == null || a.estado() >= 400)
					&& (desde == null || !a.momento().isBefore(desde))
					&& (hasta == null || a.momento().isBefore(hasta))
					&& (antesDe == null || r.id() < antesDe);
		}
	}

	record Estado(String almacen, int pendientes, long perdidos) {
	}

}
