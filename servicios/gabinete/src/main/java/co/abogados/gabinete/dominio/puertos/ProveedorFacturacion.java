package co.abogados.gabinete.dominio.puertos;

import co.abogados.gabinete.dominio.modelo.ConfigFacturacion;
import co.abogados.gabinete.dominio.modelo.Factura;

/**
 * Proveedor tecnológico autorizado por la DIAN que firma y transmite los documentos electrónicos (F14).
 * Hoy solo existe el SIMULADO; conectar uno real (Alegra, Siigo, The Factory HKA…) es escribir otro adaptador de
 * esta interfaz con sus credenciales, sin tocar el resto.
 */
public interface ProveedorFacturacion {

	/** aceptado: la DIAN validó el documento. codigo: CUFE (factura) o CUDE (nota). mensaje: lo que respondió. */
	record Envio(boolean aceptado, String codigo, String mensaje) {
	}

	String nombre();

	Envio enviarFactura(Factura factura, FacturacionRepositorio.Emisor emisor, ConfigFacturacion config);

	Envio enviarNotaCredito(Factura.NotaCredito nota, Factura factura, FacturacionRepositorio.Emisor emisor,
			ConfigFacturacion config);

}
