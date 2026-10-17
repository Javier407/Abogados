package co.abogados.procesos.dominio.modelo;

/** Lo que hace la firma (o el juzgado, en audiencia) dentro del proceso. */
public enum TipoActuacion {
	PODER, DEMANDA, CONTESTACION, MEMORIAL, RECURSO, AUDIENCIA, ALEGATOS, NOTIFICACION, OTRO;

	/** Etapa a la que pasa el proceso cuando se registra esta actuación (null = no cambia). */
	public EtapaProceso etapaQueAbre() {
		return switch (this) {
			case DEMANDA -> EtapaProceso.DEMANDA;
			case CONTESTACION -> EtapaProceso.CONTESTACION;
			case RECURSO -> EtapaProceso.RECURSO;
			default -> null;
		};
	}
}
