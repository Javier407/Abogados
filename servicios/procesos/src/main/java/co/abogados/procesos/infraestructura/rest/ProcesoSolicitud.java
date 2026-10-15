package co.abogados.procesos.infraestructura.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.abogados.procesos.dominio.modelo.ClaseCuantia;
import co.abogados.procesos.dominio.modelo.EstadoProceso;
import co.abogados.procesos.dominio.modelo.EtapaProceso;
import co.abogados.procesos.dominio.modelo.Proceso;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.modelo.RolCliente;
import co.abogados.procesos.dominio.modelo.TipoServicio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST y PUT /procesos. El radicado se acepta con o sin guiones. */
public record ProcesoSolicitud(
		@NotBlank(message = "El radicado es obligatorio") String radicado,
		@NotNull(message = "El tipo de proceso es obligatorio") Short idTipo,
		Short idDespachoActual,
		@NotNull(message = "El cliente es obligatorio") @Positive Integer idCliente,
		@NotNull(message = "El rol del cliente es obligatorio") RolCliente rolCliente,
		@NotBlank(message = "El asunto es obligatorio") @Size(max = 200, message = "Máximo 200 caracteres") String asunto,
		@PositiveOrZero(message = "La cuantía no puede ser negativa") BigDecimal cuantia,
		@NotNull(message = "La clase de cuantía es obligatoria") ClaseCuantia claseCuantia,
		@NotNull(message = "El tipo de servicio es obligatorio") TipoServicio tipoServicio,
		EstadoProceso estado,
		@NotNull(message = "La etapa es obligatoria") EtapaProceso etapa,
		@NotNull(message = "La fecha de radicación es obligatoria")
		@PastOrPresent(message = "La fecha de radicación no puede ser futura") LocalDate fechaRadicacion,
		Boolean vigilado,
		@NotNull(message = "El abogado responsable es obligatorio") @Positive Long idResponsable) {

	Proceso aDominio() {
		return new Proceso(null, Radicado.de(radicado), idTipo, idDespachoActual, idCliente, rolCliente, asunto,
				cuantia, claseCuantia, tipoServicio, estado, etapa, fechaRadicacion,
				vigilado == null || vigilado, idResponsable);
	}

}
