package co.abogados.identidad.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.identidad.dominio.CredencialesInvalidasException;
import co.abogados.identidad.dominio.CuentaBloqueadaException;
import co.abogados.identidad.dominio.MfaRequeridoException;
import co.abogados.identidad.dominio.PoliticaClave;
import co.abogados.identidad.dominio.RecursoNoEncontradoException;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.AvisosSeguridad;
import co.abogados.identidad.dominio.puertos.Cifrador;
import co.abogados.identidad.dominio.puertos.UsuarioRepositorio;

/**
 * Inicio de sesión y cambio de contraseña.
 * - El mensaje de error no dice si falló el usuario o la contraseña (no revela qué cuentas existen).
 * - Si el usuario no existe se verifica igual un hash ficticio, para que el tiempo de respuesta no lo delate.
 * - 5 fallos seguidos bloquean la cuenta 15 minutos. Cada intento queda en la bitácora identidad.ingresos.
 * - Al entrar se abre una sesión renovable (SesionesServicio); cambiar la contraseña corta las demás sesiones.
 * - Si la cuenta usa la verificación en dos pasos (F13), la contraseña correcta no abre la sesión: MfaServicio
 *   entrega un desafío (MfaRequeridoException) y la sesión se abre con el código.
 */
@Service
public class AutenticacionServicio {

	private static final Logger log = LoggerFactory.getLogger(AutenticacionServicio.class);
	private static final String MENSAJE = "Usuario o contraseña incorrectos";

	private final UsuarioRepositorio usuarios;
	private final Cifrador cifrador;
	private final SesionesServicio sesiones;
	private final Clock reloj;
	private final String hashFicticio;
	private final MfaServicio mfa;

	private AvisosSeguridad avisos = AvisosSeguridad.NINGUNO;

	/** F15: avisos de seguridad a la persona (opcional: sin mensajería no se avisa). */
	@Autowired(required = false)
	public void setAvisos(AvisosSeguridad avisos) {
		this.avisos = avisos;
	}

	public AutenticacionServicio(UsuarioRepositorio usuarios, Cifrador cifrador, SesionesServicio sesiones, Clock reloj) {
		this(usuarios, cifrador, sesiones, reloj, null);
	}

	@Autowired
	public AutenticacionServicio(UsuarioRepositorio usuarios, Cifrador cifrador, SesionesServicio sesiones, Clock reloj,
			MfaServicio mfa) {
		this.usuarios = usuarios;
		this.cifrador = cifrador;
		this.sesiones = sesiones;
		this.reloj = reloj;
		this.mfa = mfa;
		this.hashFicticio = cifrador.cifrar("clave-de-relleno-para-igualar-tiempos");
	}

	/** Los fallos deben quedar guardados aunque el método termine con excepción: no se deshacen. */
	@Transactional(noRollbackFor = { CredencialesInvalidasException.class, CuentaBloqueadaException.class,
			MfaRequeridoException.class })
	public Sesion iniciarSesion(String usuarioOEmail, String clave, String ip) {
		String texto = usuarioOEmail == null ? "" : usuarioOEmail.strip().toLowerCase(Locale.ROOT);
		Instant ahora = Instant.now(reloj);
		Optional<Usuario> encontrado = texto.isEmpty() ? Optional.empty() : usuarios.porUsuarioOEmail(texto);

		if (encontrado.isEmpty()) {
			cifrador.coincide(clave == null ? "" : clave, hashFicticio);
			usuarios.registrarIntento(recortar(texto), null, false, "NO_EXISTE", ip);
			throw new CredencialesInvalidasException(MENSAJE);
		}
		Usuario u = encontrado.get();
		if (u.bloqueado(ahora)) {
			usuarios.registrarIntento(recortar(texto), u.idUsuario(), false, "BLOQUEADO", ip);
			throw new CuentaBloqueadaException(u.minutosDeBloqueo(ahora));
		}
		if (clave == null || !cifrador.coincide(clave, u.hashClave())) {
			Usuario.Fallo fallo = u.registrarFallo(ahora);
			usuarios.guardarFallo(u.idUsuario(), fallo.intentos(), fallo.bloqueadoHasta());
			usuarios.registrarIntento(recortar(texto), u.idUsuario(), false, "CLAVE_INCORRECTA", ip);
			if (fallo.bloquea()) {
				log.warn("Cuenta {} bloqueada {} min por {} intentos fallidos (IP {})", u.usuario(),
						Usuario.BLOQUEO.toMinutes(), Usuario.MAX_INTENTOS, ip);
				avisos.avisar(AvisosSeguridad.Tipo.CUENTA_BLOQUEADA, u);
				throw new CuentaBloqueadaException(Usuario.BLOQUEO.toMinutes());
			}
			throw new CredencialesInvalidasException(MENSAJE);
		}
		if (!u.activo()) {
			// Después de verificar la contraseña: solo quien la conoce sabe que la cuenta está inactiva.
			usuarios.registrarIntento(recortar(texto), u.idUsuario(), false, "INACTIVO", ip);
			throw new CredencialesInvalidasException("La cuenta está desactivada; hable con el administrador");
		}
		if (mfa != null) {
			mfa.exigirSiCorresponde(u);   // lanza MfaRequeridoException si falta el segundo paso
		}
		usuarios.guardarIngreso(u.idUsuario(), ahora);
		usuarios.registrarIntento(recortar(texto), u.idUsuario(), true, null, ip);
		return sesiones.abrir(u, ip);
	}

	@Transactional(readOnly = true)
	public Usuario yo(long idUsuario) {
		return usuarios.porId(idUsuario)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + idUsuario));
	}

	@Transactional
	public void cambiarClave(long idUsuario, String actual, String nueva) {
		cambiarClave(idUsuario, actual, nueva, null);
	}

	/** Cambia la contraseña y corta las demás sesiones abiertas (la del refresh token refrescoActual sigue). */
	@Transactional
	public void cambiarClave(long idUsuario, String actual, String nueva, String refrescoActual) {
		Usuario u = yo(idUsuario);
		if (actual == null || !cifrador.coincide(actual, u.hashClave())) {
			throw new CredencialesInvalidasException("La contraseña actual no es correcta");
		}
		PoliticaClave.validar(nueva, u);
		if (cifrador.coincide(nueva, u.hashClave())) {
			throw new ReglaNegocioException("La contraseña nueva debe ser distinta de la actual");
		}
		usuarios.cambiarClave(idUsuario, cifrador.cifrar(nueva), Instant.now(reloj));
		sesiones.cerrarTodas(idUsuario, "CLAVE_CAMBIADA", refrescoActual);
		avisos.avisar(AvisosSeguridad.Tipo.CLAVE_CAMBIADA, u);
	}

	private static String recortar(String texto) {
		return texto.length() > 120 ? texto.substring(0, 120) : texto;
	}

}
