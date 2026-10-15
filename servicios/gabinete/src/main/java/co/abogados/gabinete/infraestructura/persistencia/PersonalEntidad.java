package co.abogados.gabinete.infraestructura.persistencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(schema = "gabinete", name = "personal")
class PersonalEntidad {
	@Id
	@Column(name = "id_persona")
	Long idPersona;
	String nombres;
	String apellidos;
	String email;
	String telefono;
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "id_cargo")
	CargoEntidad cargo;
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "id_sede")
	SedeEntidad sede;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "id_area")
	AreaEntidad area;
	@Column(name = "tarjeta_profesional")
	String tarjetaProfesional;
	@Column(name = "tarifa_hora")
	BigDecimal tarifaHora;
	@Column(name = "fecha_ingreso")
	LocalDate fechaIngreso;
	/** Cuenta en identidad.usuarios; la administra el servicio identidad (fase F5). */
	@Column(name = "id_usuario")
	Long idUsuario;
	boolean activo;
}
