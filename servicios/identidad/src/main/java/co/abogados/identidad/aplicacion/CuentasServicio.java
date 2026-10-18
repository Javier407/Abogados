package co.abogados.identidad.aplicacion;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.identidad.dominio.ConflictoException;
import co.abogados.identidad.dominio.Cuenta;
import co.abogados.identidad.dominio.PoliticaClave;
import co.abogados.identidad.dominio.RecursoNoEncontradoException;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.Cifrador;
import co.abogados.identidad.dominio.puertos.CuentasRepositorio;

/**
 * Administración de cuentas: crear, cambiar datos y roles, activar o desactivar, restablecer la contraseña y
 * desbloquear. La contraseña temporal la escribe el administrador (nunca se genera ni se muestra aquí) y la
 * persona debe cambiarla al entrar. Siempre queda al menos un administrador activo, y nadie puede quitarse
 * a sí mismo el rol de administrador ni desactivar su propia cuenta.
 */
@Service
@Transactional
public class CuentasServicio {

	private static final Logger log = LoggerFactory.getLogger(CuentasServicio.class);

	public record NuevaCuenta(long idUsuario, String usuario, String nombre, String email, Set<String> roles,
			String claveTemporal) {
	}

	public record CambioCuenta(String usuario, String nombre, String email, Set<String> roles, boolean activo) {
	}

	private final CuentasRepositorio cuentas;
	private final Cifrador cifrador;

	public CuentasServicio(CuentasRepositorio cuentas, Cifrador cifrador) {
		this.cuentas = cuentas;
		this.cifrador = cifrador;
	}

	@Transactional(readOnly = true)
	public List<Cuenta> listar() {
		return cuentas.listar();
	}

	public Cuenta crear(NuevaCuenta n, long administrador) {
		if (n.idUsuario() <= 0) {
			throw new ReglaNegocioException("El documento debe ser un número positivo");
		}
		String usuario = minusculas(n.usuario());
		String email = minusculas(n.email());
		Cuenta.validar(usuario, n.nombre(), email, n.roles());
		if (cuentas.existe(n.idUsuario(), usuario, email, null)) {
			throw new ConflictoException("Ya existe una cuenta con ese documento, usuario o correo");
		}
		PoliticaClave.validar(n.claveTemporal(), comoUsuario(n.idUsuario(), usuario));
		cuentas.crear(n.idUsuario(), usuario, n.nombre().strip(), email, cifrador.cifrar(n.claveTemporal()), n.roles());
		log.info("Cuenta {} ({}) creada por {} con roles {}", usuario, n.idUsuario(), administrador, n.roles());
		return buscar(n.idUsuario());
	}

	public Cuenta actualizar(long idUsuario, CambioCuenta c, long administrador) {
		Cuenta actual = buscar(idUsuario);
		String usuario = minusculas(c.usuario());
		String email = minusculas(c.email());
		Cuenta.validar(usuario, c.nombre(), email, c.roles());
		if (cuentas.existe(-1, usuario, email, idUsuario)) {
			throw new ConflictoException("Otra cuenta ya usa ese usuario o correo");
		}
		boolean dejaDeSerAdmin = actual.activo() && actual.roles().contains("ADMIN")
				&& (!c.activo() || !c.roles().contains("ADMIN"));
		if (dejaDeSerAdmin && idUsuario == administrador) {
			throw new ReglaNegocioException("No puede quitarse a sí mismo el rol de administrador ni desactivar su cuenta");
		}
		if (dejaDeSerAdmin && cuentas.administradoresActivos() <= 1) {
			throw new ReglaNegocioException("Debe quedar al menos un administrador activo");
		}
		cuentas.actualizar(idUsuario, usuario, c.nombre().strip(), email, c.activo(), c.roles());
		log.info("Cuenta {} actualizada por {}: roles {}, activa {}", idUsuario, administrador, c.roles(), c.activo());
		return buscar(idUsuario);
	}

	public Cuenta restablecerClave(long idUsuario, String claveTemporal, long administrador) {
		Cuenta actual = buscar(idUsuario);
		PoliticaClave.validar(claveTemporal, comoUsuario(idUsuario, actual.usuario()));
		cuentas.restablecerClave(idUsuario, cifrador.cifrar(claveTemporal));
		log.info("Contraseña de la cuenta {} restablecida por {}", idUsuario, administrador);
		return buscar(idUsuario);
	}

	public Cuenta desbloquear(long idUsuario, long administrador) {
		buscar(idUsuario);
		cuentas.desbloquear(idUsuario);
		log.info("Cuenta {} desbloqueada por {}", idUsuario, administrador);
		return buscar(idUsuario);
	}

	private Cuenta buscar(long idUsuario) {
		return cuentas.buscar(idUsuario)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la cuenta " + idUsuario));
	}

	/** La política de contraseñas solo mira el usuario y el documento. */
	private static Usuario comoUsuario(long id, String usuario) {
		return new Usuario(id, usuario, "", "", "", true, Set.of(), 0, null);
	}

	private static String minusculas(String s) {
		return s == null ? null : s.strip().toLowerCase(java.util.Locale.ROOT);
	}

}
