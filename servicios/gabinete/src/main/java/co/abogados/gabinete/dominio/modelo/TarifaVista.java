package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Tarifa de referencia (Conalbos) o propia, con su valor en pesos para el salario mínimo del año. */
public record TarifaVista(
		short idTarifa,
		String fuente,
		String codigoRama,
		String servicio,
		String tipoServicio,
		String formaCobro,
		BigDecimal valorSmmlv,
		BigDecimal valorSmmlvMax,
		BigDecimal porcentaje,
		String nota,
		LocalDate vigenteDesde,
		LocalDate vigenteHasta,
		int anioSmmlv,
		BigDecimal desdePesos,
		BigDecimal hastaPesos) {
}
