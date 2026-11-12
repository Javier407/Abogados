package co.abogados.gateway.auditoria;

/** Un acceso ya guardado en la bitácora: su número en la cadena, el hash del anterior y el propio. */
public record Registro(long id, Acceso acceso, String hashAnterior, String hash) {
}
