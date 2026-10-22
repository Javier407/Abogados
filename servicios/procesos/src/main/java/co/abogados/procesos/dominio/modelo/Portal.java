package co.abogados.procesos.dominio.modelo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lo que ve el cliente en el portal. Solo datos de sus procesos y sin lo interno de la firma: no hay términos,
 * notas ni documentos, y el resumen es el que el abogado revisó y publicó.
 */
public final class Portal {

	private Portal() {
	}

	/** Un proceso en la lista del portal. */
	public record ProcesoCliente(int idProceso, String radicado, String tipoProceso, String rama, String despacho,
			String asunto, String rolCliente, String etapa, String estado, LocalDate fechaRadicacion, String abogado,
			Audiencia proximaAudiencia, LocalDate ultimaNovedad) {
	}

	/** El expediente visto por el cliente. */
	public record Detalle(ProcesoCliente proceso, List<Novedad> novedades, List<Audiencia> audiencias,
			ResumenPublicado resumen, List<DocumentoCliente> documentos) {
	}

	/** Documento que el abogado dejó visible para el cliente (F10). */
	public record DocumentoCliente(int idDocumento, String nombre, String categoria, String descripcion, long tamano,
			Instant subido) {
	}

	/** Una actuación de la firma o una decisión del juez, en palabras simples. */
	public record Novedad(LocalDate fecha, String origen, String titulo, String detalle) {
	}

	public record Audiencia(LocalDate fecha, LocalTime hora, String lugar, String descripcion) {
	}

	/** Resumen en lenguaje sencillo que el abogado publicó (siempre revisado por él). */
	public record ResumenPublicado(int idResumen, int idProceso, String texto, long publicadoPor, Instant publicadoEn) {

		/** Lo mínimo para que un resumen sirva; el máximo es el de la columna. */
		public static String validar(String texto) {
			String t = texto == null ? "" : texto.strip();
			if (t.length() < 20) {
				throw new co.abogados.procesos.dominio.excepciones.ReglaNegocioException(
						"El resumen debe tener al menos 20 caracteres");
			}
			if (t.length() > 4000) {
				throw new co.abogados.procesos.dominio.excepciones.ReglaNegocioException(
						"El resumen admite máximo 4000 caracteres");
			}
			return t;
		}
	}

	/** Evento portal.resumen_publicado: notificaciones avisa al cliente. */
	public record ResumenAvisado(int idResumen, int idProceso, String radicado, int idCliente, Instant ocurrido) {
	}

	/** Traduce un evento de la línea de tiempo a lo que se le muestra al cliente (null = no se muestra). */
	public static Novedad novedad(EventoExpediente e) {
		return switch (e.categoria()) {
			case "ACTUACION" -> new Novedad(e.fecha(), "Su abogado", e.titulo(), e.detalle());
			case "PROVIDENCIA" -> new Novedad(e.fecha(), "El juzgado",
					("SENTENCIA".equals(e.tipo()) ? "Sentencia: " : "Auto: ") + e.titulo(), e.detalle());
			default -> null;    // los términos son la agenda interna de la firma
		};
	}

}
