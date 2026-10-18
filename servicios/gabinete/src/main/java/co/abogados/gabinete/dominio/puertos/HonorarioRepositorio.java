package co.abogados.gabinete.dominio.puertos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import co.abogados.gabinete.dominio.modelo.Honorario;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.Pago;

public interface HonorarioRepositorio {

	List<HonorarioVista> listar(Integer idCliente, Integer idProceso, String estado);

	Optional<HonorarioVista> buscar(int idHonorario);

	Optional<Honorario> acuerdo(int idHonorario);

	boolean existeParaProceso(int idProceso);

	int crear(Honorario honorario);

	void cambiarEstado(int idHonorario, String estado);

	List<Pago> pagos(int idHonorario);

	BigDecimal totalPagado(int idHonorario);

	int registrarPago(Pago pago);

	Optional<Pago> pago(int idPago);

	/** Marca el pago como anulado (no se borra); deja de contar en totalPagado. */
	void anularPago(int idPago, Instant cuando, Long quien, String motivo);

	/** Total de la parte variable ya liquidada (vacío si no se ha liquidado). */
	Optional<BigDecimal> valorLiquidado(int idHonorario);

	void liquidar(int idHonorario, BigDecimal base, BigDecimal valor, LocalDate fecha);

}
