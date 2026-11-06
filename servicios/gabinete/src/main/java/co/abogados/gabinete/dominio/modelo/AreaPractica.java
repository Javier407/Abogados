package co.abogados.gabinete.dominio.modelo;

/** Área de práctica: una por rama del derecho (codigoRama = procesos.ramas.codigo). */
public record AreaPractica(short idArea, String codigoRama, String nombre, long idDirector) {
}
