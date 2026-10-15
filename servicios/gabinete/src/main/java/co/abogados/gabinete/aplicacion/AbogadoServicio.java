package co.abogados.gabinete.aplicacion;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.excepciones.ConflictoException;
import co.abogados.gabinete.dominio.excepciones.RecursoNoEncontradoException;
import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.Abogado;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.puertos.AbogadoRepositorio;
import co.abogados.gabinete.dominio.puertos.CatalogoGabinete;

/**
 * Casos de uso de los abogados. Un abogado no se borra: se desactiva, porque sus procesos,
 * términos y honorarios lo siguen referenciando.
 */
@Service
@Transactional
public class AbogadoServicio {

	private final AbogadoRepositorio abogados;
	private final CatalogoGabinete catalogo;

	public AbogadoServicio(AbogadoRepositorio abogados, CatalogoGabinete catalogo) {
		this.abogados = abogados;
		this.catalogo = catalogo;
	}

	@Transactional(readOnly = true)
	public List<Abogado> listar(boolean soloActivos) {
		return abogados.listar(soloActivos);
	}

	@Transactional(readOnly = true)
	public Abogado obtener(long idPersona) {
		return abogados.buscar(idPersona)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe un abogado con la cédula " + idPersona));
	}

	public Abogado crear(Abogado nuevo) {
		if (abogados.existePersona(nuevo.idPersona())) {
			throw new ConflictoException("Ya hay una persona registrada con la cédula " + nuevo.idPersona());
		}
		validar(nuevo);
		return abogados.guardar(nuevo);
	}

	public Abogado actualizar(long idPersona, Abogado datos) {
		obtener(idPersona);
		if (datos.idPersona() != idPersona) {
			throw new ReglaNegocioException("La cédula no se puede cambiar");
		}
		validar(datos);
		return abogados.guardar(datos);
	}

	public Abogado desactivar(long idPersona) {
		Abogado a = obtener(idPersona);
		return abogados.guardar(new Abogado(a.idPersona(), a.nombres(), a.apellidos(), a.email(), a.telefono(),
				a.idCargo(), null, a.idSede(), null, a.idArea(), null, a.tarjetaProfesional(), a.tarifaHora(),
				a.fechaIngreso(), false));
	}

	private void validar(Abogado a) {
		Cargo cargo = catalogo.cargo(a.idCargo())
				.orElseThrow(() -> new ReglaNegocioException("No existe el cargo " + a.idCargo()));
		if (!cargo.esAbogado()) {
			throw new ReglaNegocioException("El cargo " + cargo.nombre() + " no es de abogado");
		}
		if (catalogo.sede(a.idSede()).isEmpty()) {
			throw new ReglaNegocioException("No existe la sede " + a.idSede());
		}
		if (a.idArea() != null && catalogo.area(a.idArea()).isEmpty()) {
			throw new ReglaNegocioException("No existe el área de práctica " + a.idArea());
		}
		unico(abogados.idPorEmail(a.email()), a.idPersona(), "el correo " + a.email());
		unico(abogados.idPorTarjeta(a.tarjetaProfesional()), a.idPersona(),
				"la tarjeta profesional " + a.tarjetaProfesional());
	}

	private static void unico(Optional<Long> duenio, long idPropio, String que) {
		duenio.filter(id -> id != idPropio).ifPresent(id -> {
			throw new ConflictoException("Ya hay otra persona registrada con " + que);
		});
	}

}
