package co.abogados.identidad.aplicacion;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.identidad.dominio.ConflictoException;
import co.abogados.identidad.dominio.Cuenta;
import co.abogados.identidad.dominio.PoliticaClave;
import co.abogados.identidad.dominio.RecursoNoEncontradoException;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.AvisosSeguridad;
import co.abogados.identidad.dominio.puertos.Cifrador;
import co.abogados.identidad.dominio.puertos.CuentasRepositorio;

/**
 * Administración de cuentas: crear, cambiar datos y roles, activar o desactivar, restablecer la contraseña y
 * desbloquear. La contraseña temporal la escribe el administrador (nunca se genera ni se muestra aquí) y la
 * persona debe cambiarla al entrar. Siempre queda al menos un administrador activo, y nadie puede quitarse
 * a sí mismo el rol de administrador ni desactivar su propia cuenta.
 * Desactivar una cuenta, cambiarle los roles o restablecer su contraseña corta sus sesiones abiertas (F9).
 * Las cuentas de cliente se enlazan con su ficha en gabinete (idCliente) para el portal.
 */
@Service
@Transactional
public class CuentasServicio {

	private static final Logger log = LoggerFactory.getLogger(CuentasServicio.class);

	public record NuevaCuenta(long idUsuario, String usuario, String nombre, String email, Set<String> roles,
			String claveTemporal, Integer idCliente) {

		public NuevaCuenta(long idUsuario, String usuario, String nombre, String email, Set<String> roles,
				String claveTemporal) {
			this(idUsuario, usuario, nombre, email, roles, claveTemporal, null);
		}
	}

	public record CambioCuenta(String usuario, String nombre, String email, Set<String> roles, boolean activo,
			Integer idCliente) {

		public CambioCuenta(String usuario, String nombre, String email, Set<String> roles, boolean activo) {
			this(usuario, nombre, email, roles, activo, null);
		}
	}

	private final CuentasRepositorio cuentas;
	private final Cifrador cifrador;
	private final SesionesServicio sesiones;

	private AvisosSeguridad avisos = AvisosSeguridad.NINGUNO;

	/** F15: avisos de seguridad a la persona (opcional: sin mensajería no se avisa). */
	@Autowired(required = false)
	public void setAvisos(AvisosSeguridad avisos) {
		this.avisos = avisos;
	}

	public CuentasServicio(CuentasRepositorio cuentas, Cifrador cifrador, SesionesServicio sesiones) {
		this.cuentas = cuentas;
		this.cifrador = cifrador;
		this.sesiones = sesiones;
	}

	/** Cuentas activas de un cliente (las usa notificaciones para avisarle). */
	@Transactional(readOnly = true)
	public List<Cuenta> deCliente(int idCliente) {
		return cuentas.deCliente(idCliente);
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
		Cuenta.validar(usuario, n.nombre(), email, n.roles(), n.idCliente());
		if (cuentas.existe(n.idUsuario(), usuario, email, null)) {
			throw new ConflictoException("Ya existe una cuenta con ese documento, usuario o correo");
		}
		PoliticaClave.validar(n.claveTemporal(), comoUsuario(n.idUsuario(), usuario));
		cuentas.crear(n.idUsuario(), usuario, n.nombre().strip(), email, cifrador.cifrar(n.claveTemporal()), n.roles(),
				n.idCliente());
		log.info("Cuenta {} ({}) creada por {} con roles {}", usuario, n.idUsuario(), administrador, n.roles());
		return buscar(n.idUsuario());
	}

	public Cuenta actualizar(long idUsuario, CambioCuenta c, long administrador) {
		Cuenta actual = buscar(idUsuario);
		String usuario = minusculas(c.usuario());
		String email = minusculas(c.email());
		Cuenta.validar(usuario, c.nombre(), email, c.roles(), c.idCliente());
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
		cuentas.actualizar(idUsuario, usuario, c.nombre().strip(), email, c.activo(), c.roles(), c.idCliente());
		boolean cambiaAcceso = !c.activo() || !actual.roles().equals(c.roles())
				|| !java.util.Objects.equals(actual.idCliente(), c.idCliente());
		if (cambiaAcceso) {
			sesiones.cerrarTodas(idUsuario, c.activo() ? "ROLES_CAMBIADOS" : "CUENTA_DESACTIVADA", null);
		}
		log.info("Cuenta {} actualizada por {}: roles {}, activa {}", idUsuario, administrador, c.roles(), c.activo());
		return buscar(idUsuario);
	}

	public Cuenta restablecerClave(long idUsuario, String claveTemporal, long administrador) {
		Cuenta actual = buscar(idUsuario);
		PoliticaClave.validar(claveTemporal, comoUsuario(idUsuario, actual.usuario()));
		cuentas.restablecerClave(idUsuario, cifrador.cifrar(claveTemporal));
		sesiones.cerrarTodas(idUsuario, "CLAVE_RESTABLECIDA", null);
		log.info("Contraseña de la cuenta {} restablecida por {}", idUsuario, administrador);
		avisos.avisar(AvisosSeguridad.Tipo.CLAVE_RESTABLECIDA, new Usuario(idUsuario, actual.usuario(), actual.nombre(),
				actual.email(), null, actual.activo(), actual.roles(), 0, null, actual.idCliente()));
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
