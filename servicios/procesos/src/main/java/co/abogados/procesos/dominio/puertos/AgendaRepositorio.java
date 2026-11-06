package co.abogados.procesos.dominio.puertos;

import java.time.LocalDate;
import java.util.List;

import co.abogados.procesos.dominio.modelo.AudienciaVista;

public interface AgendaRepositorio {

	/** Audiencias entre las dos fechas (inclusive), por fecha y hora; con idAsistente, solo las de esa persona. */
	List<AudienciaVista> audiencias(LocalDate desde, LocalDate hasta, Long idAsistente);

}
