package co.abogados.procesos.dominio.puertos;

import java.time.LocalDate;
import java.util.List;

import co.abogados.procesos.dominio.modelo.Reportes.CargaAbogado;
import co.abogados.procesos.dominio.modelo.Reportes.CumplimientoTerminos;
import co.abogados.procesos.dominio.modelo.Reportes.ProcesoDeAbogado;
import co.abogados.procesos.dominio.modelo.Reportes.ResultadoRama;

public interface ReporteRepositorio {

	List<CargaAbogado> carga(LocalDate hoy);

	List<CumplimientoTerminos> cumplimiento(LocalDate desde, LocalDate hasta, LocalDate hoy);

	List<ResultadoRama> resultados(LocalDate desde, LocalDate hasta);

	/**
	 * Los procesos en los que está la persona, con su rol. Primero los abiertos y, entre ellos, los que tiene a
	 * cargo; los cerrados al final.
	 */
	List<ProcesoDeAbogado> procesosDe(long idPersona);

}
