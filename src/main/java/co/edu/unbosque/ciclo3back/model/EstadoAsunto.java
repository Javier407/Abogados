package co.edu.unbosque.ciclo3back.model;

/**
 * Estados posibles de un asunto del gabinete.
 * ARCHIVADO y FINALIZADO son estados de cierre: exigen fecha de archivo.
 */
public enum EstadoAsunto {
	EN_TRAMITE,
	SUSPENDIDO,
	ARCHIVADO,
	FINALIZADO;

	public boolean esCierre() {
		return this == ARCHIVADO || this == FINALIZADO;
	}
}
