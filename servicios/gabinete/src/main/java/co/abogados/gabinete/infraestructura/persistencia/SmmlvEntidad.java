package co.abogados.gabinete.infraestructura.persistencia;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "gabinete", name = "smmlv")
class SmmlvEntidad {
	@Id
	Short anio;
	BigDecimal valor;
}
