package co.abogados.procesos.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.procesos.dominio.excepciones.ConflictoException;
import co.abogados.procesos.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.procesos.dominio.excepciones.ReglaNegocioException;
import co.abogados.procesos.dominio.modelo.DesgloseRadicado;
import co.abogados.procesos.dominio.modelo.Despacho;
import co.abogados.procesos.dominio.modelo.FiltroProcesos;
import co.abogados.procesos.dominio.modelo.Proceso;
import co.abogados.procesos.dominio.modelo.ProcesoCreado;
import co.abogados.procesos.dominio.modelo.ProcesoVista;
import co.abogados.procesos.dominio.modelo.Radicado;
import co.abogados.procesos.dominio.puertos.CatalogoRepositorio;
import co.abogados.procesos.dominio.puertos.DirectorioGabinete;
import co.abogados.procesos.dominio.puertos.ProcesoRepositorio;
import co.abogados.procesos.dominio.puertos.PublicadorEventos;

/**
 * Casos de uso de los procesos. Comprueba las reglas que dependen de otros datos:
 * catálogos (tipo y despacho del radicado), unicidad del radicado y existencia del
 * cliente y del abogado en el servicio gabinete.
 */
@Service
@Transactional
public class ProcesoServicio {

	private final ProcesoRepositorio procesos;
	private final CatalogoRepositorio catalogos;
	private final DirectorioGabinete gabinete;
	private final PublicadorEventos eventos;
	private final Clock reloj;

	public ProcesoServicio(ProcesoRepositorio procesos, CatalogoRepositorio catalogos, DirectorioGabinete gabinete,
			PublicadorEventos eventos, Clock reloj) {
		this.procesos = procesos;
		this.catalogos = catalogos;
		this.gabinete = gabinete;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public List<ProcesoVista> listar(FiltroProcesos filtro) {
		return procesos.listar(filtro);
	}

	@Transactional(readOnly = true)
	public ProcesoVista obtener(int idProceso) {
		return procesos.buscar(idProceso)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el proceso " + idProceso));
	}

	/** Analiza un radicado sin guardarlo; la interfaz lo usa para validar mientras se escribe. */
	@Transactional(readOnly = true)
	public DesgloseRadicado desglosar(String numero) {
		Radicado radicado = Radicado.de(numero);
		return DesgloseRadicado.de(radicado,
				catalogos.despachoDeRadicado(radicado).orElse(null),
				procesos.idPorRadicado(radicado).orElse(null));
	}

	public ProcesoVista crear(Proceso nuevo) {
		validarCatalogos(nuevo);
		validarRadicadoLibre(nuevo.radicado(), null);
		DirectorioGabinete.Cliente cliente = clienteExistente(nuevo.idCliente());
		validarResponsable(nuevo.idResponsable());

		int id = procesos.crear(nuevo, cliente.nombre());
		eventos.procesoCreado(new ProcesoCreado(id, nuevo.radicado().numero(), nuevo.idResponsable(),
				Instant.now(reloj)));
		return obtener(id);
	}

	public ProcesoVista actualizar(int idProceso, Proceso datos) {
		ProcesoVista actual = obtener(idProceso);
		validarCatalogos(datos);
		validarRadicadoLibre(datos.radicado(), idProceso);
		// Solo se consulta gabinete si cambió la referencia: así editar el asunto no depende de otro servicio.
		if (actual.idCliente() != datos.idCliente()) {
			clienteExistente(datos.idCliente());
		}
		if (!Objects.equals(actual.idResponsable(), datos.idResponsable())) {
			validarResponsable(datos.idResponsable());
		}
		procesos.actualizar(datos.conId(idProceso));
		return obtener(idProceso);
	}

	/** Un proceso con providencias tiene historia judicial: se archiva, no se borra. */
	public void eliminar(int idProceso) {
		obtener(idProceso);
		int providencias = procesos.contarProvidencias(idProceso);
		if (providencias > 0) {
			throw new ConflictoException("El proceso " + idProceso + " tiene " + providencias
					+ " providencia(s); cámbielo a ARCHIVADO en lugar de eliminarlo");
		}
		procesos.eliminar(idProceso);
	}

	private void validarCatalogos(Proceso p) {
		if (p.fechaRadicacion().isAfter(LocalDate.now(reloj))) {
			throw new ReglaNegocioException("La fecha de radicación no puede ser futura");
		}
		if (catalogos.tipo(p.idTipo()).isEmpty()) {
			throw new ReglaNegocioException("No existe el tipo de proceso " + p.idTipo());
		}
		if (catalogos.despachoDeRadicado(p.radicado()).isEmpty()) {
			Radicado r = p.radicado();
			throw new ReglaNegocioException("El radicado " + r.legible() + " corresponde a un despacho que no está "
					+ "registrado (DANE " + r.codDane() + ", entidad " + r.codEntidad() + ", especialidad "
					+ r.codEspecialidad() + ", despacho " + r.codDespacho() + ")");
		}
		if (p.idDespachoActual() != null) {
			Despacho actual = catalogos.despacho(p.idDespachoActual())
					.orElseThrow(() -> new ReglaNegocioException("No existe el despacho " + p.idDespachoActual()));
			if (p.radicado().instancia() == 0 && !actual.prefijo().equals(p.radicado().prefijoDespacho())) {
				throw new ReglaNegocioException("En primera instancia el despacho actual debe ser el del radicado");
			}
		}
	}

	private void validarRadicadoLibre(Radicado radicado, Integer idPropio) {
		procesos.idPorRadicado(radicado)
				.filter(id -> !id.equals(idPropio))
				.ifPresent(id -> {
					throw new ConflictoException("El radicado " + radicado.legible() + " ya está registrado en el proceso " + id);
				});
	}

	private DirectorioGabinete.Cliente clienteExistente(int idCliente) {
		return gabinete.cliente(idCliente)
				.orElseThrow(() -> new ReglaNegocioException("No existe el cliente " + idCliente));
	}

	private void validarResponsable(long idPersona) {
		DirectorioGabinete.Abogado abogado = gabinete.abogado(idPersona)
				.orElseThrow(() -> new ReglaNegocioException("No existe un abogado con la cédula " + idPersona));
		if (!abogado.activo()) {
			throw new ReglaNegocioException("El abogado " + abogado.nombres() + " " + abogado.apellidos()
					+ " está inactivo y no puede ser responsable de un proceso");
		}
	}

}
