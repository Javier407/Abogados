package co.edu.unbosque.ciclo3back.model;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Entidad que representa la tabla abogados.
 * Un abogado puede llevar varios asuntos y un asunto puede ser llevado por
 * varios abogados: la relación N:M se mapea en Asuntos (tabla asunto_abogado).
 */
@Entity
@Table(name = "abogados")
public class Abogados {

	@Id
	@Positive(message = "La identificación debe ser un número positivo")
	@Column(name = "id_abogado")
	private long id_abogado;

	@NotBlank(message = "La tarjeta profesional es obligatoria")
	@Pattern(regexp = "^[0-9]{4,10}$", message = "La tarjeta profesional debe tener entre 4 y 10 dígitos")
	@Column(name = "tarjeta_profesional", nullable = false, unique = true, length = 10)
	private String tarjeta_profesional;

	@NotBlank(message = "El nombre es obligatorio")
	@Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
	@Column(name = "nombre_abogado", nullable = false, length = 100)
	private String nombre_abogado;

	@NotBlank(message = "La dirección es obligatoria")
	@Size(max = 150, message = "La dirección no puede superar 150 caracteres")
	@Column(name = "direccion_abogado", nullable = false, length = 150)
	private String direccion_abogado;

	@NotBlank(message = "El teléfono es obligatorio")
	@Pattern(regexp = "^[0-9+ ]{7,15}$", message = "El teléfono debe tener entre 7 y 15 dígitos")
	@Column(name = "telefono_abogado", nullable = false, length = 15)
	private String telefono_abogado;

	@NotBlank(message = "El email es obligatorio")
	@Email(message = "El email no tiene un formato válido")
	@Column(name = "email_abogado", nullable = false, length = 120)
	private String email_abogado;

	/**
	 * Cuenta con la que el abogado entra al sistema (relación 1 : 0..1 con usuarios).
	 * Es opcional y única: un usuario solo puede pertenecer a un abogado.
	 * No se serializa para no exponer los datos de la cuenta (incluida la contraseña);
	 * en el JSON solo viaja la cédula, mediante cedula_usuario.
	 */
	@OneToOne(optional = true)
	@JoinColumn(name = "cedula_usuario", unique = true)
	@JsonIgnore
	private Usuarios usuario;

	/** Cédula del usuario que llega del frontend; AbogadosService la convierte en la relación. */
	@Transient
	private Long cedula_usuario_solicitada;

	public Abogados() {
	}

	public Abogados(long id_abogado, String tarjeta_profesional, String nombre_abogado, String direccion_abogado,
			String telefono_abogado, String email_abogado) {
		this.id_abogado = id_abogado;
		this.tarjeta_profesional = tarjeta_profesional;
		this.nombre_abogado = nombre_abogado;
		this.direccion_abogado = direccion_abogado;
		this.telefono_abogado = telefono_abogado;
		this.email_abogado = email_abogado;
	}

	// ---------- Getters y Setters ----------

	public long getId_abogado() {
		return id_abogado;
	}

	public void setId_abogado(long id_abogado) {
		this.id_abogado = id_abogado;
	}

	public String getTarjeta_profesional() {
		return tarjeta_profesional;
	}

	public void setTarjeta_profesional(String tarjeta_profesional) {
		this.tarjeta_profesional = tarjeta_profesional;
	}

	public String getNombre_abogado() {
		return nombre_abogado;
	}

	public void setNombre_abogado(String nombre_abogado) {
		this.nombre_abogado = nombre_abogado;
	}

	public String getDireccion_abogado() {
		return direccion_abogado;
	}

	public void setDireccion_abogado(String direccion_abogado) {
		this.direccion_abogado = direccion_abogado;
	}

	public String getTelefono_abogado() {
		return telefono_abogado;
	}

	public void setTelefono_abogado(String telefono_abogado) {
		this.telefono_abogado = telefono_abogado;
	}

	public String getEmail_abogado() {
		return email_abogado;
	}

	public void setEmail_abogado(String email_abogado) {
		this.email_abogado = email_abogado;
	}

	public Usuarios getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuarios usuario) {
		this.usuario = usuario;
	}

	/** En el JSON: la cédula del usuario vinculado, o null si el abogado no tiene cuenta. */
	public Long getCedula_usuario() {
		if (usuario != null) {
			return usuario.getCedula_usuario();
		}
		return cedula_usuario_solicitada;
	}

	public void setCedula_usuario(Long cedula_usuario) {
		this.cedula_usuario_solicitada = cedula_usuario;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Abogados)) return false;
		return id_abogado == ((Abogados) o).id_abogado;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id_abogado);
	}

	@Override
	public String toString() {
		return "Abogados{" +
				"id_abogado=" + id_abogado +
				", nombre_abogado='" + nombre_abogado + '\'' +
				'}';
	}

}
