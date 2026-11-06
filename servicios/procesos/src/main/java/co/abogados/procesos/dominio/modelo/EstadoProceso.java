package co.abogados.procesos.dominio.modelo;

public enum EstadoProceso {
	ACTIVO, SUSPENDIDO, TERMINADO, ARCHIVADO;

	/** Un proceso terminado o archivado ya no avanza: su etapa debe ser CERRADO. */
	public boolean esCerrado() {
		return this == TERMINADO || this == ARCHIVADO;
	}
}
