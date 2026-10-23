package co.abogados.gabinete.infraestructura.facturacion;

import org.springframework.stereotype.Component;

import co.abogados.gabinete.dominio.modelo.ConfigFacturacion;
import co.abogados.gabinete.dominio.modelo.Cufe;
import co.abogados.gabinete.dominio.modelo.Factura;
import co.abogados.gabinete.dominio.puertos.FacturacionRepositorio;
import co.abogados.gabinete.dominio.puertos.ProveedorFacturacion;

/**
 * Proveedor SIMULADO (F14): no envía nada a la DIAN. Revisa los datos obligatorios como lo haría la DIAN, calcula
 * el CUFE/CUDE con la clave técnica pública del set de pruebas y responde "aceptada". Sirve para usar y probar el
 * flujo completo mientras la firma contrata un proveedor tecnológico real.
 */
@Component
public class ProveedorSimulado implements ProveedorFacturacion {

	/** Clave técnica del set de pruebas de habilitación de la DIAN (pública; no sirve en producción). */
	static final String CLAVE_PRUEBAS = "fc8eac422eba16e22ffd8c6f94b3f40a6e38162c";
	static final String PIN_PRUEBAS = "12345";

	@Override
	public String nombre() {
		return ConfigFacturacion.SIMULADO;
	}

	@Override
	public Envio enviarFactura(Factura f, FacturacionRepositorio.Emisor emisor, ConfigFacturacion config) {
		String falta = faltante(f, emisor);
		if (falta != null) {
			return new Envio(false, null, "Rechazada (simulado): " + falta);
		}
		String cufe = Cufe.calcular(f.numeroCompleto(), f.emitida(), f.totales().subtotal(), f.totales().iva(),
				f.totales().total(), emisor.nit(), f.adquirente().numeroDocumento(), CLAVE_PRUEBAS, f.ambiente());
		return new Envio(true, cufe, "Documento validado por la DIAN (SIMULADO, ambiente " + f.ambiente() + ")");
	}

	@Override
	public Envio enviarNotaCredito(Factura.NotaCredito n, Factura f, FacturacionRepositorio.Emisor emisor,
			ConfigFacturacion config) {
		String cude = Cufe.calcular(n.numeroCompleto(), n.emitida(), f.totales().subtotal(), f.totales().iva(),
				n.valor(), emisor.nit(), f.adquirente().numeroDocumento(), PIN_PRUEBAS, f.ambiente());
		return new Envio(true, cude, "Nota crédito validada por la DIAN (SIMULADO) sobre " + f.numeroCompleto());
	}

	/** Lo que la DIAN rechazaría por faltar. */
	static String faltante(Factura f, FacturacionRepositorio.Emisor e) {
		if (e == null || e.nit() == null || e.nit().isBlank()) {
			return "falta el NIT del emisor";
		}
		if (f.adquirente().email() == null || !f.adquirente().email().contains("@")) {
			return "el adquirente no tiene un correo válido";
		}
		if (f.totales().total().signum() <= 0) {
			return "el total debe ser mayor que cero";
		}
		return null;
	}

}
