package co.abogados.gabinete.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "gabinete", name = "cargos")
class CargoEntidad {
	@Id
	@Column(name = "id_cargo")
	Short idCargo;
	String codigo;
	String nombre;
	@Column(name = "es_abogado")
	boolean esAbogado;
}
