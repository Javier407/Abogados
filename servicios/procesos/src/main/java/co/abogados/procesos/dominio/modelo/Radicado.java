package co.abogados.procesos.dominio.modelo;

import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;

/**
 * Número único de radicación de la Rama Judicial (23 dígitos):
 * DANE(5) + entidad(2) + especialidad(2) + despacho(3) + año(4) + consecutivo(5) + instancia(2).
 * Ejemplo: 11001-31-03-032-2024-00458-00.
 */
public record Radicado(String numero) {

	public Radicado {
		if (numero == null || !numero.matches("\\d{23}")) {
			throw new ReglaNegocioException("El radicado debe tener exactamente 23 dígitos (recibido: '" + numero + "')");
		}
		int anio = Integer.parseInt(numero.substring(12, 16));
		if (anio < 1990 || anio > 2100) {
			throw new ReglaNegocioException("El año del radicado (" + anio + ") no es válido");
		}
	}

	/** Acepta el número con guiones, puntos o espacios, como suele copiarse del portal. */
	public static Radicado de(String texto) {
		return new Radicado(texto == null ? null : texto.replaceAll("[\\s.\\-]", ""));
	}

	public String codDane()         { return numero.substring(0, 5); }
	public String codEntidad()      { return numero.substring(5, 7); }
	public String codEspecialidad() { return numero.substring(7, 9); }
	public String codDespacho()     { return numero.substring(9, 12); }
	public int anio()               { return Integer.parseInt(numero.substring(12, 16)); }
	public int consecutivo()        { return Integer.parseInt(numero.substring(16, 21)); }
	public int instancia()          { return Integer.parseInt(numero.substring(21, 23)); }

	/** Los 12 primeros dígitos identifican el despacho donde se radicó el proceso. */
	public String prefijoDespacho() { return numero.substring(0, 12); }

	public String legible() {
		return String.join("-", codDane(), codEntidad(), codEspecialidad(), codDespacho(),
				numero.substring(12, 16), numero.substring(16, 21), numero.substring(21, 23));
	}

	@Override
	public String toString() {
		return numero;
	}

}
