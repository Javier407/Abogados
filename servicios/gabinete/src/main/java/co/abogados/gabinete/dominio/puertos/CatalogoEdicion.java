package co.abogados.gabinete.dominio.puertos;

import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Sede;

/**
 * Cambios en sedes, cargos y áreas de práctica (F11). Crear asigna el siguiente id; borrar algo que está en uso
 * (clientes o personal que lo referencian) lanza ConflictoException.
 */
public interface CatalogoEdicion {

	Sede guardarSede(Short idSede, Sede datos);

	/** Quita la marca de principal a las demás sedes (antes de marcar una). */
	void desmarcarPrincipales();

	void eliminarSede(short idSede);

	Cargo guardarCargo(Short idCargo, Cargo datos);

	void eliminarCargo(short idCargo);

	AreaPractica guardarArea(Short idArea, AreaPractica datos);

	void eliminarArea(short idArea);

	/** La persona existe en el personal de la firma y está activa. */
	boolean personalActivo(long idPersona);

}
