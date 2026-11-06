package co.abogados.procesos.dominio.modelo;

import java.util.List;

/**
 * Resultado de agregar o editar una parte: la parte guardada, los posibles conflictos de intereses y si se
 * pudo verificar (si gabinete no responde, la parte se guarda igual pero conflictosVerificados = false).
 */
public record ParteRegistrada(Parte parte, List<ConflictoInteres> conflictos, boolean conflictosVerificados) {
}
