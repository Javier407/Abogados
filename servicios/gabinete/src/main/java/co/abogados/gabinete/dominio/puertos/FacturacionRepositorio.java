package co.abogados.gabinete.dominio.puertos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import co.abogados.gabinete.dominio.modelo.ConfigFacturacion;
import co.abogados.gabinete.dominio.modelo.Factura;

public interface FacturacionRepositorio {

	/** Datos del emisor (la firma y su sede principal) que van en cada factura. */
	record Emisor(String nit, String razonSocial, String direccion, String ciudad, String telefono, String email) {
	}

	ConfigFacturacion config();

	/** La configuración bloqueada hasta el fin de la transacción: nadie más toma el mismo número. */
	ConfigFacturacion configParaNumerar();

	void guardarConfig(ConfigFacturacion config);

	void avanzarNumero();

	void avanzarNota();

	Emisor emisor();

	/** Guarda la factura con sus líneas y marca los gastos cobrados en ella. Devuelve su id. */
	int guardar(Factura factura);

	void registrarEnvio(int idFactura, Factura.Estado estado, String cufe, String respuesta);

	/** Sin líneas; de la más reciente a la más antigua. Filtros opcionales. */
	List<Factura> listar(Integer idCliente, Factura.Estado estado);

	/** Con líneas y nota crédito. */
	Optional<Factura> buscar(int idFactura);

	/** Lo ya facturado de cada acuerdo de honorarios del cliente (sin contar facturas anuladas ni rechazadas). */
	Map<Integer, BigDecimal> facturadoPorHonorario(int idCliente);

	/** Gastos del cliente ya cobrados en una factura vigente. */
	Set<Integer> gastosFacturados(int idCliente);

	int guardarNota(Factura.NotaCredito nota);

	/** Marca la factura ANULADA y libera sus gastos para otra factura. */
	void anular(int idFactura);

	/** Para el historial de la configuración: cuándo la validó el contador. */
	default void validarConfig(long quien, Instant cuando) {
		guardarConfig(config().conValidacion(quien, cuando));
	}

}
