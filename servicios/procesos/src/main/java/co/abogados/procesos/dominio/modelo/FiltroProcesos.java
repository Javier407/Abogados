package co.abogados.procesos.dominio.modelo;

/** Criterios opcionales para listar procesos (null = sin filtrar). texto busca en radicado y asunto. */
public record FiltroProcesos(EstadoProceso estado, String rama, Integer idCliente, Long idAbogado, String texto) {

	public static FiltroProcesos todos() {
		return new FiltroProcesos(null, null, null, null, null);
	}
}
