package co.abogados.gabinete.dominio.modelo;

public enum FormaCobro {
	FIJO(true, false), MENSUAL(true, false), CUOTA_LITIS(false, true), MIXTO(true, true),
	PORCENTAJE_CUANTIA(false, true);

	private final boolean valorFijo;
	private final boolean porcentaje;

	FormaCobro(boolean valorFijo, boolean porcentaje) {
		this.valorFijo = valorFijo;
		this.porcentaje = porcentaje;
	}

	public boolean llevaValorFijo() {
		return valorFijo;
	}

	public boolean llevaPorcentaje() {
		return porcentaje;
	}
}
