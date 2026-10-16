package co.abogados.gabinete.infraestructura.persistencia;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "gabinete", name = "tarifas")
class TarifaEntidad {
	@Id
	@Column(name = "id_tarifa")
	Short idTarifa;
	String fuente;
	@Column(name = "codigo_rama")
	String codigoRama;
	String servicio;
	@Column(name = "tipo_servicio")
	String tipoServicio;
	@Column(name = "forma_cobro")
	String formaCobro;
	@Column(name = "valor_smmlv")
	BigDecimal valorSmmlv;
	@Column(name = "valor_smmlv_max")
	BigDecimal valorSmmlvMax;
	BigDecimal porcentaje;
	String nota;
	@Column(name = "vigente_desde")
	LocalDate vigenteDesde;
	@Column(name = "vigente_hasta")
	LocalDate vigenteHasta;
}
