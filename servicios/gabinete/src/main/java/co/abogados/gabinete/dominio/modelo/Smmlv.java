package co.abogados.gabinete.dominio.modelo;

import java.math.BigDecimal;

/** Salario mínimo mensual legal vigente de un año: las tarifas en SMMLV se convierten a pesos con él. */
public record Smmlv(int anio, BigDecimal valor) {
}
