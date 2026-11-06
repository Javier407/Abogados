package co.abogados.procesos.infraestructura.eventos;

import java.time.Duration;
import java.util.List;

/**
 * Tabla procesos.outbox: eventos por publicar. Se agregan en la misma transacción que el cambio que anuncian,
 * así un evento existe si y solo si el cambio se guardó.
 */
public interface Outbox {

	record Pendiente(long id, String clave, String cuerpo) {
	}

	void agregar(String clave, String cuerpoJson);

	/** Los más antiguos sin enviar (bloqueados para este envío: otra instancia no los toma). */
	List<Pendiente> pendientes(int maximo);

	void marcarEnviado(long id);

	void marcarFallo(long id, String error);

	/** Borra los ya enviados hace más de la antigüedad indicada. */
	int purgar(Duration antiguedad);

}
