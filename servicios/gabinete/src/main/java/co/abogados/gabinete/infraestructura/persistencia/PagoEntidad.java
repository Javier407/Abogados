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
@Table(schema = "gabinete", name = "pagos")
class PagoEntidad {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_pago")
	Integer idPago;
	@Column(name = "id_honorario")
	Integer idHonorario;
	LocalDate fecha;
	BigDecimal valor;
	String medio;
	String referencia;
}
