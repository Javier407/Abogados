package co.abogados.identidad.dominio.puertos;

import co.abogados.identidad.dominio.AccesoExterno.Identidad;
import co.abogados.identidad.dominio.AccesoExterno.Proveedor;

/**
 * Un proveedor de identidad (OAuth 2.0 con código de autorización y PKCE). La aplicación no sabe de HTTP: arma la
 * URL de ida y, a la vuelta, canjea el código por la identidad de la persona.
 */
public interface ProveedorExterno {

	Proveedor proveedor();

	/** URL del proveedor a la que va el navegador. reto: el reto PKCE (S256) del verificador. */
	String urlAutorizacion(String estado, String reto, String urlVuelta);

	/** Canjea el código (servidor a servidor, con el secreto del cliente) y devuelve quién es la persona. */
	Identidad canjear(String codigo, String verificador, String urlVuelta);

}
