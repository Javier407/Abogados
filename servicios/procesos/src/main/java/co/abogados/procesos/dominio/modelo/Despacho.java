package co.abogados.procesos.dominio.modelo;

public record Despacho(short idDespacho, String nombre, String ciudad, String codDane, String codEntidad,
		String codEspecialidad, String codDespacho, String codigoRama) {

	/** Los 12 dígitos con que empieza el radicado de un proceso radicado en este despacho. */
	public String prefijo() {
		return codDane + codEntidad + codEspecialidad + codDespacho;
	}
}
