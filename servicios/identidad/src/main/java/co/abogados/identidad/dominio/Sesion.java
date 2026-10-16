package co.abogados.identidad.dominio;

import java.time.Instant;
import java.util.Set;

/** Token emitido al iniciar sesión y los datos que la interfaz necesita mostrar. */
public record Sesion(String token, Instant expira, long idUsuario, String usuario, String nombre, Set<String> roles) {
}
