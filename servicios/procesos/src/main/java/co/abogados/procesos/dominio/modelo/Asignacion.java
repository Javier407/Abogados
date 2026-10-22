package co.abogados.procesos.dominio.modelo;

import java.time.LocalDate;

/**
 * Quién trabaja en el proceso: el abogado RESPONSABLE (uno solo, se cambia al editar el proceso), abogados de APOYO
 * y DEPENDIENTEs judiciales. nombre viene de gabinete (null si gabinete no respondió).
 */
public record Asignacion(int idProceso, long idPersona, Rol rol, LocalDate desde, String nombre) {

	public enum Rol { RESPONSABLE, APOYO, DEPENDIENTE }

	public Asignacion conNombre(String nombre) {
		return new Asignacion(idProceso, idPersona, rol, desde, nombre);
	}

}
