package co.edu.unbosque.ciclo3back.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PastOrPresent;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

/**
 * DTO con los datos que envía el frontend para crear o actualizar un asunto.
 * En lugar de objetos completos, trae solo las llaves del cliente y de los
 * abogados; la capa de servicio se encarga de buscarlos en la BD.
 */
public class AsuntoRequest {

	@NotBlank(message = "El número de expediente es obligatorio")
	// El vacío lo reporta @NotBlank; así cada campo devuelve un solo mensaje
	@Pattern(regexp = "^([A-Za-z0-9-]{3,20})?$", message = "El expediente debe tener entre 3 y 20 caracteres (letras, números o guiones)")
	private String numero_expediente;

	@NotNull(message = "La fecha de inicio es obligatoria")
	@PastOrPresent(message = "La fecha de inicio no puede ser futura")
	private LocalDate fecha_inicio;

	private LocalDate fecha_archivo;

	@NotNull(message = "El estado es obligatorio")
	private EstadoAsunto estado;

	@Positive(message = "Debe indicar el cliente del asunto")
	private long id_cliente;

	@NotNull(message = "La lista de abogados no puede ser nula")
	private List<Long> ids_abogados = new ArrayList<>();

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

	public long getId_cliente() {
		return id_cliente;
	}

	public void setId_cliente(long id_cliente) {
		this.id_cliente = id_cliente;
	}

	public List<Long> getIds_abogados() {
		return ids_abogados;
	}

	public void setIds_abogados(List<Long> ids_abogados) {
		this.ids_abogados = ids_abogados;
	}

}
