package co.edu.unbosque.ciclo3back.model;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/**
 * Entidad que representa la tabla clientes.
 * Un cliente puede tener varios asuntos, pero cada asunto pertenece a un solo cliente
 * (la relación se mapea en Asuntos con @ManyToOne).
 */
@Entity
@Table(name = "clientes")
public class Clientes {

	@Id
	@Positive(message = "La identificación debe ser un número positivo")
	@Column(name = "id_cliente")
	private long id_cliente;

	@NotBlank(message = "El nombre es obligatorio")
	@Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
	@Column(name = "nombre_cliente", nullable = false, length = 100)
	private String nombre_cliente;

	@NotBlank(message = "La dirección es obligatoria")
	@Size(max = 150, message = "La dirección no puede superar 150 caracteres")
	@Column(name = "direccion_cliente", nullable = false, length = 150)
	private String direccion_cliente;

	@NotBlank(message = "El teléfono es obligatorio")
	@Pattern(regexp = "^[0-9+ ]{7,15}$", message = "El teléfono debe tener entre 7 y 15 dígitos")
	@Column(name = "telefono_cliente", nullable = false, length = 15)
	private String telefono_cliente;

	@NotBlank(message = "El email es obligatorio")
	@Email(message = "El email no tiene un formato válido")
	@Column(name = "email_cliente", nullable = false, length = 120)
	private String email_cliente;

	public Clientes() {
	}

	public Clientes(long id_cliente, String nombre_cliente, String direccion_cliente, String telefono_cliente,
			String email_cliente) {
		this.id_cliente = id_cliente;
		this.nombre_cliente = nombre_cliente;
		this.direccion_cliente = direccion_cliente;
		this.telefono_cliente = telefono_cliente;
		this.email_cliente = email_cliente;
	}

	// ---------- Getters y Setters ----------

	public long getId_cliente() {
		return id_cliente;
	}

	public void setId_cliente(long id_cliente) {
		this.id_cliente = id_cliente;
	}

	public String getNombre_cliente() {
		return nombre_cliente;
	}

	public void setNombre_cliente(String nombre_cliente) {
		this.nombre_cliente = nombre_cliente;
	}

	public String getDireccion_cliente() {
		return direccion_cliente;
	}

	public void setDireccion_cliente(String direccion_cliente) {
		this.direccion_cliente = direccion_cliente;
	}

	public String getTelefono_cliente() {
		return telefono_cliente;
	}

	public void setTelefono_cliente(String telefono_cliente) {
		this.telefono_cliente = telefono_cliente;
	}

	public String getEmail_cliente() {
		return email_cliente;
	}

	public void setEmail_cliente(String email_cliente) {
		this.email_cliente = email_cliente;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Clientes)) return false;
		return id_cliente == ((Clientes) o).id_cliente;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id_cliente);
	}

	@Override
	public String toString() {
		return "Clientes{" +
				"id_cliente=" + id_cliente +
				", nombre_cliente='" + nombre_cliente + '\'' +
				'}';
	}

}
