package co.abogados.procesos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.procesos.dominio.modelo.Documento;

/** Datos de los documentos del expediente (procesos.documentos). */
public interface DocumentoRepositorio {

	Documento guardar(Documento nuevo);

	/** Del más reciente al más antiguo; con soloVisibles, solo los que el cliente puede ver. */
	List<Documento> listar(int idProceso, boolean soloVisibles);

	Optional<Documento> buscar(int idProceso, int idDocumento);

	void actualizar(int idDocumento, Documento.Categoria categoria, String descripcion, boolean visibleCliente);

	void eliminar(int idDocumento);

}
