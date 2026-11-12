package co.abogados.gateway.auditoria;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * La cadena de hashes de la bitácora. Cada registro guarda el hash del anterior y el suyo:
 *
 * <pre>
 * hash = SHA-256( id \n momento UTC con microsegundos \n usuario \n roles \n método \n ruta \n estado|- \n
 *                 duración \n ip|- \n hash anterior )
 * </pre>
 *
 * El primero encadena con {@link #GENESIS}. Es el mismo cálculo que hace PostgreSQL al guardar
 * (auditoria.huella, migración 015): aquí se repite por cuenta propia para revisar la cadena sin confiar en las
 * funciones de la base.
 */
public final class Cadena {

	public static final String GENESIS = "0".repeat(64);

	private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
			.withZone(ZoneOffset.UTC);

	private Cadena() {
	}

	public static String huella(long id, Acceso a, String hashAnterior) {
		String texto = String.join("\n",
				Long.toString(id),
				MOMENTO.format(a.momento()),
				a.usuario(), a.roles(), a.metodo(), a.ruta(),
				a.estado() == null ? "-" : a.estado().toString(),
				Integer.toString(a.duracionMs()),
				a.ip() == null ? "-" : a.ip(),
				hashAnterior);
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(texto.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);   // toda JVM trae SHA-256
		}
	}

	/** El siguiente eslabón después de (ultimoId, ultimoHash). */
	public static Registro encadenar(long ultimoId, String ultimoHash, Acceso a) {
		long id = ultimoId + 1;
		return new Registro(id, a, ultimoHash, huella(id, a, ultimoHash));
	}

	/**
	 * Resultado de revisar la cadena.
	 *
	 * @param integra   true si todos los registros revisados cuadran
	 * @param revisados cuántos registros cuadraron
	 * @param ultimoId  último número que cuadró (0 si ninguno): junto con ultimoHash, el sello actual
	 * @param roturaEn  primer número que no cuadra, o null
	 * @param motivo    por qué no cuadra, en palabras, o null
	 */
	public record Verificacion(boolean integra, long revisados, long ultimoId, String ultimoHash, Long roturaEn,
			String motivo) {
	}

	/**
	 * Revisa los registros en orden, uno por uno, sin tenerlos todos en memoria. Desde el principio o desde un
	 * sello ya conocido (número y hash que se guardaron antes, por ejemplo en el PC).
	 */
	public static final class Verificador {

		private final long selloId;
		private final String selloHash;
		private long revisados;
		private long ultimoId;
		private String ultimoHash;
		private boolean selloVisto;
		private Long roturaEn;
		private String motivo;

		public Verificador() {
			this(0, GENESIS);
		}

		public Verificador(long selloId, String selloHash) {
			this.selloId = selloId;
			this.selloHash = selloHash;
			this.ultimoId = selloId;
			this.ultimoHash = selloHash;
			this.selloVisto = selloId == 0;
		}

		/** Número desde el que hay que leer (inclusive): el del sello, para comprobar que sigue igual. */
		public long leerDesde() {
			return selloId == 0 ? 1 : selloId;
		}

		/** Revisa el siguiente registro. Devuelve false cuando ya encontró una rotura (no hace falta seguir). */
		public boolean revisar(Registro r) {
			if (roturaEn != null) {
				return false;
			}
			if (!selloVisto) {
				selloVisto = true;
				if (r.id() != selloId || !r.hash().equals(selloHash)) {
					return romper(selloId, "El sello guardado ya no coincide con el registro " + selloId);
				}
				return true;
			}
			if (r.id() != ultimoId + 1) {
				return romper(ultimoId + 1, "Falta el registro " + (ultimoId + 1));
			}
			if (!r.hashAnterior().equals(ultimoHash)) {
				return romper(r.id(), "El registro " + r.id() + " no encadena con el anterior");
			}
			if (!r.hash().equals(huella(r.id(), r.acceso(), r.hashAnterior()))) {
				return romper(r.id(), "El contenido del registro " + r.id() + " cambió después de guardarlo");
			}
			revisados++;
			ultimoId = r.id();
			ultimoHash = r.hash();
			return true;
		}

		private boolean romper(long id, String porque) {
			roturaEn = id;
			motivo = porque;
			return false;
		}

		public Verificacion resultado() {
			if (roturaEn == null && !selloVisto) {
				romper(selloId, "El registro " + selloId + " del sello ya no está");
			}
			return new Verificacion(roturaEn == null, revisados, ultimoId, ultimoHash, roturaEn, motivo);
		}

	}

}
