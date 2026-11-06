package co.abogados.identidad.dominio.puertos;

import java.time.Instant;

import co.abogados.identidad.dominio.TokenAcceso;
import co.abogados.identidad.dominio.Usuario;

/** Firma los tokens de acceso (RS256). */
public interface EmisorTokens {

	/** Token de una persona. Con claveTemporal el gateway solo le permite cambiar la contraseña. */
	TokenAcceso acceso(Usuario usuario, boolean claveTemporal, Instant ahora);

	/** Token de un servicio interno (rol SERVICIO, solo lectura) para llamar a otro servicio. */
	TokenAcceso servicio(String nombre, Instant ahora);

}
