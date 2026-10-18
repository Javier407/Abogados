package co.abogados.gabinete.dominio.puertos;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import co.abogados.gabinete.dominio.modelo.CarteraCliente;
import co.abogados.gabinete.dominio.modelo.Gasto;

public interface GastoRepositorio {

	/** Filtros opcionales (null = sin filtrar); del más reciente al más antiguo. */
	List<Gasto> listar(Integer idProceso, Integer idCliente);

	Optional<Gasto> buscar(int idGasto);

	int crear(Gasto gasto);

	void marcarReembolsado(int idGasto, LocalDate fecha);

	void eliminar(int idGasto);

	/** Cartera por cliente (honorarios con total conocido y gastos por reembolsar), sin calcular diasSinPagar. */
	List<CarteraCliente> cartera();

}
