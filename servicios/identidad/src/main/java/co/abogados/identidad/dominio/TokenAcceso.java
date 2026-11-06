package co.abogados.identidad.dominio;

import java.time.Instant;
import java.util.UUID;

/** Token firmado, su identificador (jti, para poder revocarlo) y su vencimiento. */
public record TokenAcceso(String token, UUID jti, Instant expira) {
}
