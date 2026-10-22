package co.abogados.identidad.dominio.puertos;

/** Cifra los secretos de la verificación en dos pasos antes de guardarlos (la llave no está en la base). */
public interface CifradorSecretos {

	String cifrar(byte[] secreto);

	byte[] descifrar(String cifrado);

}
