package co.abogados.procesos.dominio.modelo;

/** Etapas en orden cronológico: el orden de declaración se usa para saber si una etapa es posterior a otra. */
public enum EtapaProceso {
	PODER, DEMANDA, ADMISION, NOTIFICACION, CONTESTACION, INVESTIGACION,
	PRUEBAS, AUDIENCIA, JUICIO, SENTENCIA, RECURSO, CERRADO;

	/**
	 * Un hecho del expediente (una actuación, una sentencia) mueve el proceso a otra etapa solo si el proceso
	 * sigue activo y la nueva etapa es posterior: registrar tarde el poder no devuelve el proceso al inicio.
	 */
	public boolean avanzaA(EtapaProceso nueva, String estadoProceso) {
		return "ACTIVO".equals(estadoProceso) && this != CERRADO && nueva != CERRADO && nueva.ordinal() > ordinal();
	}
}
