package co.abogados.gabinete.dominio.puertos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import co.abogados.gabinete.dominio.modelo.Smmlv;
import co.abogados.gabinete.dominio.modelo.TarifaVista;

public interface TarifaRepositorio {

	/** Tarifas con el valor en pesos calculado con el salario mínimo del año indicado. */
	List<TarifaVista> listar(int anio, BigDecimal smmlv);

	boolean existe(short idTarifa);

	/** Salario mínimo del año; si no está cargado, el del último año disponible (anio, valor). */
	Optional<Smmlv> smmlv(int anio);

}
