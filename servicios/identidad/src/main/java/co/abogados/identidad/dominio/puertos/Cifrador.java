package co.abogados.identidad.dominio.puertos;

/** Hash de contraseñas (BCrypt). */
public interface Cifrador {

	String cifrar(String clave);

	boolean coincide(String clave, String hash);

}
