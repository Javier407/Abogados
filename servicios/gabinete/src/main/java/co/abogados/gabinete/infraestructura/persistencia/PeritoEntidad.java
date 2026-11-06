package co.abogados.gabinete.infraestructura.persistencia;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "gabinete", name = "peritos")
class PeritoEntidad {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_perito")
	Integer idPerito;
	String nombre;
	String especialidad;
	String profesion;
	String registro;
	String telefono;
	String email;
	String ciudad;
	@Column(name = "tarifa_dictamen")
	BigDecimal tarifaDictamen;
	Short calificacion;
}
