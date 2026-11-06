package co.abogados.procesos.dominio.modelo;

import java.time.OffsetDateTime;

/** Una línea del historial de una regla de términos (F12). nombre lo completa gabinete (puede ser null). */
public record CambioRegla(OffsetDateTime momento, long idUsuario, String nombre, String accion, String detalle) {

	public CambioRegla conNombre(String n) {
		return new CambioRegla(momento, idUsuario, n, accion, detalle);
	}

}
