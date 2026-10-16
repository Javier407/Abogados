package co.abogados.procesos.dominio.puertos;

import java.time.LocalDate;
import java.util.List;

import co.abogados.procesos.dominio.modelo.DiaInhabil;

public interface CalendarioRepositorio {

	/** Todos los días inhábiles cargados (festivos, vacancia y Semana Santa). */
	List<DiaInhabil> diasInhabiles();

}
