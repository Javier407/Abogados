package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

/** Perito que la firma contrata para dictámenes (pruebas periciales de los procesos). */
public record Perito(
		Integer idPerito,
		String nombre,
		String especialidad,
		String profesion,
		String registro,
		String telefono,
		String email,
		String ciudad,
		BigDecimal tarifaDictamen,
		Short calificacion) {

	public Perito {
		nombre = Texto.limpio(nombre);
		especialidad = Texto.limpio(especialidad);
		profesion = Texto.limpio(profesion);
		ciudad = Texto.limpio(ciudad);
		if (nombre == null || especialidad == null || profesion == null || ciudad == null) {
			throw new ReglaNegocioException("Nombre, especialidad, profesión y ciudad del perito son obligatorios");
		}
		registro = Texto.limpio(registro);
		telefono = Texto.limpio(telefono);
		email = Texto.limpio(email);
		if (email != null && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			throw new ReglaNegocioException("El correo del perito no es válido");
		}
		if (tarifaDictamen != null && tarifaDictamen.signum() < 0) {
			throw new ReglaNegocioException("La tarifa del dictamen no puede ser negativa");
		}
		if (calificacion != null && (calificacion < 1 || calificacion > 5)) {
			throw new ReglaNegocioException("La calificación va de 1 a 5");
		}
	}

	public Perito conId(int id) {
		return new Perito(id, nombre, especialidad, profesion, registro, telefono, email, ciudad, tarifaDictamen,
				calificacion);
	}

}
