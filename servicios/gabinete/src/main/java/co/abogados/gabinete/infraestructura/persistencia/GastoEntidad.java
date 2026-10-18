package co.abogados.gabinete.infraestructura.persistencia;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "gabinete", name = "gastos")
class GastoEntidad {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_gasto")
	Integer idGasto;
	/** procesos.procesos.id_proceso (otro servicio: sin llave foránea). */
	@Column(name = "id_proceso")
	Integer idProceso;
	@Column(name = "id_cliente")
	Integer idCliente;
	LocalDate fecha;
	String concepto;
	BigDecimal valor;
	@Column(name = "pagado_por")
	String pagadoPor;
	boolean reembolsable;
	@Column(name = "reembolsado_en")
	LocalDate reembolsadoEn;
	@Column(name = "soporte_url")
	String soporteUrl;
	@Column(name = "registrado_por")
	Long registradoPor;
}
