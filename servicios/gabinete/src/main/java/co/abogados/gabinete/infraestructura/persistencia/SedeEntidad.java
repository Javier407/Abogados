package co.abogados.gabinete.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "gabinete", name = "sedes")
class SedeEntidad {
	@Id
	@Column(name = "id_sede")
	Short idSede;
	String nombre;
	String ciudad;
	String direccion;
	String telefono;
	@Column(name = "es_principal")
	boolean esPrincipal;
}
