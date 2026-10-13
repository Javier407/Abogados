package co.edu.unbosque.ciclo3back.model;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Entidad que representa la tabla asuntos.
 * - numero_expediente es la llave primaria (identifica al asunto).
 * - Cada asunto pertenece a UN cliente (@ManyToOne -> FK id_cliente).
 * - Un asunto puede ser llevado por uno o varios abogados (@ManyToMany ->
 *   tabla intermedia asunto_abogado).
 *
 * Las validaciones de entrada se hacen sobre AsuntoRequest; las reglas de
 * negocio (fechas, estado, existencia de cliente/abogados) en AsuntosService.
 */
@Entity
@Table(name = "asuntos")
public class Asuntos {

	@Id
	@Column(name = "numero_expediente", length = 20)
	private String numero_expediente;

	@Column(name = "fecha_inicio", nullable = false)
	private LocalDate fecha_inicio;

	@Column(name = "fecha_archivo")
	private LocalDate fecha_archivo;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoAsunto estado;

	@ManyToOne(optional = false)
	@JoinColumn(name = "id_cliente", nullable = false)
	private Clientes cliente;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "asunto_abogado",
			joinColumns = @JoinColumn(name = "numero_expediente"),
			inverseJoinColumns = @JoinColumn(name = "id_abogado"))
	private Set<Abogados> abogados = new HashSet<>();

	public Asuntos() {
	}

	// ---------- Getters y Setters ----------

	public String getNumero_expediente() {
		return numero_expediente;
	}

	public void setNumero_expediente(String numero_expediente) {
		this.numero_expediente = numero_expediente;
	}

	public LocalDate getFecha_inicio() {
		return fecha_inicio;
	}

	public void setFecha_inicio(LocalDate fecha_inicio) {
		this.fecha_inicio = fecha_inicio;
	}

	public LocalDate getFecha_archivo() {
		return fecha_archivo;
	}

	public void setFecha_archivo(LocalDate fecha_archivo) {
		this.fecha_archivo = fecha_archivo;
	}

	public EstadoAsunto getEstado() {
		return estado;
	}

	public void setEstado(EstadoAsunto estado) {
		this.estado = estado;
	}

	public Clientes getCliente() {
		return cliente;
	}

	public void setCliente(Clientes cliente) {
		this.cliente = cliente;
	}

	public Set<Abogados> getAbogados() {
		return abogados;
	}

	public void setAbogados(Set<Abogados> abogados) {
		this.abogados = abogados;
	}

	@Override
	public String toString() {
		return "Asuntos{" +
				"numero_expediente='" + numero_expediente + '\'' +
				", estado=" + estado +
				", fecha_inicio=" + fecha_inicio +
				", fecha_archivo=" + fecha_archivo +
				'}';
	}

}
