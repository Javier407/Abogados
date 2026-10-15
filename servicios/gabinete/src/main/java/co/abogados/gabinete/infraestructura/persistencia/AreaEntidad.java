package co.abogados.gabinete.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "gabinete", name = "areas_practica")
class AreaEntidad {
	@Id
	@Column(name = "id_area")
	Short idArea;
	@Column(name = "codigo_rama")
	String codigoRama;
	String nombre;
	@Column(name = "id_director")
	Long idDirector;
}
