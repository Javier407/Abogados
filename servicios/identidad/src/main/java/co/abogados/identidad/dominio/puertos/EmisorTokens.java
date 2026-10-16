package co.abogados.identidad.dominio.puertos;

import java.time.Instant;

import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.Usuario;

public interface EmisorTokens {

	Sesion emitir(Usuario usuario, Instant ahora);

}
