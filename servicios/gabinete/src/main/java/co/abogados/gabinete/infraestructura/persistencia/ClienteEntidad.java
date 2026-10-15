package co.abogados.gabinete.infraestructura.persistencia;

import java.time.LocalDate;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.modelo.TipoPersona;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "gabinete", name = "clientes")
class ClienteEntidad {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_cliente")
	Integer idCliente;
	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_persona")
	TipoPersona tipoPersona;
	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_documento")
	TipoDocumento tipoDocumento;
	@Column(name = "numero_documento")
	String numeroDocumento;
	String nombre;
	String email;
	String telefono;
	String direccion;
	String ciudad;
	@Column(name = "id_sede")
	Short idSede;
	LocalDate creado;
}
