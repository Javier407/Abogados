package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

/**
 * Abogado de la firma (personal cuyo cargo exige tarjeta profesional). idPersona es la cédula.
 * Los nombres de cargo, sede y área solo se llenan al leer; al guardar cuentan los id.
 */
public record Abogado(
		long idPersona,
		String nombres,
		String apellidos,
		String email,
		String telefono,
		short idCargo,
		String cargo,
		short idSede,
		String sede,
		Short idArea,
		String area,
		String tarjetaProfesional,
		BigDecimal tarifaHora,
		LocalDate fechaIngreso,
		boolean activo) {

	public Abogado {
		if (idPersona <= 0) {
			throw new ReglaNegocioException("La cédula del abogado es obligatoria");
		}
		nombres = Texto.limpio(nombres);
		apellidos = Texto.limpio(apellidos);
		if (nombres == null || apellidos == null) {
			throw new ReglaNegocioException("Los nombres y apellidos del abogado son obligatorios");
		}
		email = Texto.limpio(email);
		if (email == null || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			throw new ReglaNegocioException("El correo del abogado no es válido");
		}
		email = email.toLowerCase();
		tarjetaProfesional = Texto.limpio(tarjetaProfesional);
		if (tarjetaProfesional == null || !tarjetaProfesional.matches("\\d{3,10}")) {
			throw new ReglaNegocioException("La tarjeta profesional es obligatoria y solo lleva números");
		}
		if (tarifaHora != null && tarifaHora.signum() < 0) {
			throw new ReglaNegocioException("La tarifa por hora no puede ser negativa");
		}
		if (fechaIngreso == null) {
			throw new ReglaNegocioException("La fecha de ingreso es obligatoria");
		}
		telefono = Texto.limpio(telefono);
	}

	public String nombreCompleto() {
		return nombres + " " + apellidos;
	}

}
