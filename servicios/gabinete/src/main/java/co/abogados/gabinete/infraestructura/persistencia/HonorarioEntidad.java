package co.abogados.gabinete.infraestructura.persistencia;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(schema = "gabinete", name = "honorarios_pactados")
class HonorarioEntidad {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_honorario")
	Integer idHonorario;
	/** procesos.procesos.id_proceso (otro servicio: sin llave foránea). */
	@Column(name = "id_proceso")
	Integer idProceso;
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "id_cliente")
	ClienteEntidad cliente;
	@Column(name = "id_tarifa")
	Short idTarifa;
	@Column(name = "forma_cobro")
	String formaCobro;
	@Column(name = "valor_fijo")
	BigDecimal valorFijo;
	BigDecimal porcentaje;
	@Column(name = "valor_estimado")
	BigDecimal valorEstimado;
	@Column(name = "fecha_pacto")
	LocalDate fechaPacto;
	String estado;
}
