package co.abogados.identidad.dominio;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/** Tipos del acceso con cuentas externas (Google, Microsoft, GitHub): solo para cuentas ya existentes. */
public final class AccesoExterno {

	private AccesoExterno() {
	}

	public enum Proveedor {
		GOOGLE("Google"), MICROSOFT("Microsoft"), GITHUB("GitHub");

		private final String nombre;

		Proveedor(String nombre) {
			this.nombre = nombre;
		}

		public String nombre() {
			return nombre;
		}

		/** "google" o "GOOGLE" → GOOGLE; cualquier otro texto, vacío (la URL la escribe quien sea). */
		public static Optional<Proveedor> de(String texto) {
			if (texto == null) {
				return Optional.empty();
			}
			try {
				return Optional.of(valueOf(texto.strip().toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException e) {
				return Optional.empty();
			}
		}
	}

	/** ENTRAR: abrir sesión con una cuenta ya enlazada. ENLAZAR: la persona, ya dentro, enlaza su cuenta externa. */
	public enum Modo {
		ENTRAR, ENLAZAR
	}

	/** Quién es la persona según el proveedor. sujeto: su identificador estable (no cambia aunque cambie el correo). */
	public record Identidad(Proveedor proveedor, String sujeto, String correo, String nombre) {
		public Identidad {
			if (sujeto == null || sujeto.isBlank()) {
				throw new IllegalArgumentException("El proveedor no entregó un identificador");
			}
		}
	}

	/** Una cuenta externa enlazada, como la ve la persona en «Mi cuenta». */
	public record Enlace(Proveedor proveedor, String correo, Instant creado, Instant ultimoUso) {
	}

	/**
	 * Ida al proveedor guardada en el servidor. Solo hashes del estado y del comprobante del navegador; el
	 * verificador PKCE no sale del servidor. idUsuario: solo al enlazar (quién pidió enlazar).
	 */
	public record Estado(String hashEstado, String hashComprobante, Proveedor proveedor, Modo modo, Long idUsuario,
			String verificador, Instant vence) {
	}

	/** Lo que hay que hacer al volver del proveedor. */
	public sealed interface Resultado {
		/** Sesión abierta (con o sin verificación en dos pasos ya resuelta). */
		record SesionAbierta(Sesion sesion) implements Resultado {
		}

		/** Falta el segundo paso: la interfaz lo pide con este desafío. */
		record SegundoPaso(String desafio, boolean configurar) implements Resultado {
		}

		/** La cuenta externa quedó enlazada a la persona. */
		record Enlazada(Proveedor proveedor) implements Resultado {
		}

		/** No se pudo; motivo es un código corto que la interfaz traduce (no detalles internos). */
		record Fallo(Modo modo, String motivo) implements Resultado {
		}
	}

}
