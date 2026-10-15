package co.abogados.gabinete.dominio.modelo;

import java.time.LocalDate;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;

/**
 * Cliente de la firma: persona natural o jurídica. Una persona jurídica se identifica con NIT
 * y una natural con cédula, cédula de extranjería o pasaporte.
 */
public record Cliente(
		Integer idCliente,
		TipoPersona tipoPersona,
		TipoDocumento tipoDocumento,
		String numeroDocumento,
		String nombre,
		String email,
		String telefono,
		String direccion,
		String ciudad,
		short idSede,
		LocalDate creado) {

	public Cliente {
		if (tipoPersona == null || tipoDocumento == null) {
			throw new ReglaNegocioException("El tipo de persona y el tipo de documento son obligatorios");
		}
		if ((tipoPersona == TipoPersona.JURIDICA) != (tipoDocumento == TipoDocumento.NIT)) {
			throw new ReglaNegocioException(tipoPersona == TipoPersona.JURIDICA
					? "Una persona jurídica se identifica con NIT"
					: "Una persona natural no se identifica con NIT");
		}
		numeroDocumento = Texto.limpio(numeroDocumento);
		if (numeroDocumento == null) {
			throw new ReglaNegocioException("El número de documento es obligatorio");
		}
		String patron = tipoDocumento == TipoDocumento.PAS ? "[A-Za-z0-9]{5,15}" : "\\d{5,15}";
		if (!numeroDocumento.matches(patron)) {
			throw new ReglaNegocioException(tipoDocumento == TipoDocumento.PAS
					? "El pasaporte debe tener entre 5 y 15 letras o números"
					: "El documento debe tener entre 5 y 15 dígitos, sin puntos ni dígito de verificación");
		}
		nombre = Texto.limpio(nombre);
		ciudad = Texto.limpio(ciudad);
		if (nombre == null || ciudad == null) {
			throw new ReglaNegocioException("El nombre y la ciudad del cliente son obligatorios");
		}
		email = Texto.limpio(email);
		telefono = Texto.limpio(telefono);
		direccion = Texto.limpio(direccion);
	}

	public Cliente conId(int id, LocalDate fechaCreacion) {
		return new Cliente(id, tipoPersona, tipoDocumento, numeroDocumento, nombre, email, telefono, direccion, ciudad,
				idSede, fechaCreacion);
	}

}
