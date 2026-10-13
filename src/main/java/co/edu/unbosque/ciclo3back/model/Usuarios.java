package co.edu.unbosque.ciclo3back.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/**
 * Entidad que representa la tabla usuarios de la base de datos.
 * La anotación @Entity indica que es una estructura de datos que se
 * conecta con una tabla equivalente en la BD, y @Id marca la llave primaria.
 *
 * Las anotaciones de Bean Validation (@NotBlank, @Email, etc.) validan los
 * datos que llegan del frontend antes de guardarlos: si algo no cumple, el
 * controlador responde 400 con el detalle de cada error.
 */
@Entity
@Table(name = "usuarios")
public class Usuarios {

	@Id
	@Positive(message = "La cédula debe ser un número positivo")
	@Column(name = "cedula_usuario")
	private long cedula_usuario;

	@NotBlank(message = "El nombre es obligatorio")
	@Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
	@Column(name = "nombre_usuario")
	private String nombre_usuario;

	@NotBlank(message = "El email es obligatorio")
	@Email(message = "El email no tiene un formato válido")
	@Column(name = "email_usuario")
	private String email_usuario;

	@NotBlank(message = "El usuario es obligatorio")
	@Size(min = 3, max = 30, message = "El usuario debe tener entre 3 y 30 caracteres")
	@Column(name = "usuario")
	private String usuario;

	@NotBlank(message = "La contraseña es obligatoria")
	@Size(min = 6, max = 100, message = "La contraseña debe tener al menos 6 caracteres")
	@Column(name = "password")
	private String password;

	public Usuarios() {
	}

	public Usuarios(long cedula_usuario, String nombre_usuario, String email_usuario, String usuario, String password) {
		this.cedula_usuario = cedula_usuario;
		this.nombre_usuario = nombre_usuario;
		this.email_usuario = email_usuario;
		this.usuario = usuario;
		this.password = password;
	}

	// ---------- Getters y Setters ----------

	public long getCedula_usuario() {
		return cedula_usuario;
	}

	public void setCedula_usuario(long cedula_usuario) {
		this.cedula_usuario = cedula_usuario;
	}

	public String getNombre_usuario() {
		return nombre_usuario;
	}

	public void setNombre_usuario(String nombre_usuario) {
		this.nombre_usuario = nombre_usuario;
	}

	public String getEmail_usuario() {
		return email_usuario;
	}

	public void setEmail_usuario(String email_usuario) {
		this.email_usuario = email_usuario;
	}

	public String getUsuario() {
		return usuario;
	}

	public void setUsuario(String usuario) {
		this.usuario = usuario;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public String toString() {
		return "Usuarios{" +
				"cedula_usuario=" + cedula_usuario +
				", nombre_usuario='" + nombre_usuario + '\'' +
				", email_usuario='" + email_usuario + '\'' +
				", usuario='" + usuario + '\'' +
				'}';
	}

}
