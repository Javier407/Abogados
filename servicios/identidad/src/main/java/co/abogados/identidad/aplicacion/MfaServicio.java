package co.abogados.identidad.aplicacion;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.identidad.dominio.ConflictoException;
import co.abogados.identidad.dominio.CredencialesInvalidasException;
import co.abogados.identidad.dominio.CuentaBloqueadaException;
import co.abogados.identidad.dominio.EstadoMfa;
import co.abogados.identidad.dominio.MfaRequeridoException;
import co.abogados.identidad.dominio.RecursoNoEncontradoException;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.Sesion;
import co.abogados.identidad.dominio.Totp;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.AvisosSeguridad;
import co.abogados.identidad.dominio.puertos.Cifrador;
import co.abogados.identidad.dominio.puertos.CifradorSecretos;
import co.abogados.identidad.dominio.puertos.MfaRepositorio;
import co.abogados.identidad.dominio.puertos.UsuarioRepositorio;

/**
 * Verificación en dos pasos (F13): además de la contraseña, un código de 6 dígitos de una app autenticadora.
 * - Cada persona la activa en "Mi cuenta": escanea el código QR y confirma un código. Recibe 10 códigos de
 *   recuperación de un solo uso, por si pierde el teléfono (se muestran una sola vez).
 * - Con la verificación activa, la contraseña correcta no abre la sesión: entrega un desafío (5 minutos) que se
 *   presenta con el código. Los códigos incorrectos cuentan como intentos fallidos (5 bloquean la cuenta).
 * - Un código no sirve dos veces (se guarda el último intervalo usado).
 * - Los roles de mfa.obligatorio-roles (p. ej. ADMIN,SOCIO) deben activarla para entrar y no pueden desactivarla.
 * - El administrador puede reiniciarla a quien perdió el teléfono y los códigos: se cortan sus sesiones.
 */
@Service
public class MfaServicio {

	private static final Logger log = LoggerFactory.getLogger(MfaServicio.class);
	private static final SecureRandom AZAR = new SecureRandom();
	static final String EMISOR = "Gestión Jurídica";
	static final long VIGENCIA_DESAFIO_SEGUNDOS = 300;
	static final int CODIGOS = 10;
	private static final char[] ALFABETO_CODIGOS = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();   // sin 0/o, 1/l/i

	public record Inicio(String secreto, String uri) {
	}

	public record Estado(boolean activo, Instant desde, int codigosRestantes, boolean obligatorio) {
	}

	public record Activacion(Sesion sesion, List<String> codigos) {
	}

	private final MfaRepositorio mfa;
	private final UsuarioRepositorio usuarios;
	private final SesionesServicio sesiones;
	private final CifradorSecretos secretos;
	private final Cifrador cifrador;
	private final Clock reloj;
	private final Set<String> rolesObligatorios;
	/** Llave de los desafíos: nueva en cada arranque (un desafío pendiente vence si identidad se reinicia). */
	private final byte[] llaveDesafios = new byte[32];

	private AvisosSeguridad avisos = AvisosSeguridad.NINGUNO;

	/** F15: avisos de seguridad a la persona (opcional: sin mensajería no se avisa). */
	@Autowired(required = false)
	public void setAvisos(AvisosSeguridad avisos) {
		this.avisos = avisos;
	}

	public MfaServicio(MfaRepositorio mfa, UsuarioRepositorio usuarios, SesionesServicio sesiones,
			CifradorSecretos secretos, Cifrador cifrador, Clock reloj,
			@Value("${mfa.obligatorio-roles:}") String rolesObligatorios) {
		this.mfa = mfa;
		this.usuarios = usuarios;
		this.sesiones = sesiones;
		this.secretos = secretos;
		this.cifrador = cifrador;
		this.reloj = reloj;
		this.rolesObligatorios = Arrays.stream(rolesObligatorios.split(",")).map(String::strip)
				.filter(r -> !r.isEmpty()).map(r -> r.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
		AZAR.nextBytes(llaveDesafios);
	}

	public boolean obligatorio(Usuario u) {
		return u.roles().stream().anyMatch(rolesObligatorios::contains);
	}

	/**
	 * Tras verificar la contraseña: si la cuenta usa (o debe usar) el segundo paso, no se abre la sesión todavía.
	 * La excepción no deshace la transacción del login (que guarda la bitácora).
	 */
	@Transactional(readOnly = true, noRollbackFor = MfaRequeridoException.class)
	public void exigirSiCorresponde(Usuario u) {
		boolean activo = mfa.estado(u.idUsuario()).map(EstadoMfa::activo).orElse(false);
		if (activo) {
			throw new MfaRequeridoException(desafio(u.idUsuario(), false), false);
		}
		if (obligatorio(u)) {
			throw new MfaRequeridoException(desafio(u.idUsuario(), true), true);
		}
	}

	/** Segundo paso del inicio de sesión: código de la app o de recuperación. */
	@Transactional(noRollbackFor = { CredencialesInvalidasException.class, CuentaBloqueadaException.class })
	public Sesion completar(String desafio, String codigo, String ip) {
		Usuario u = usuarioDe(desafio, false);
		Instant ahora = Instant.now(reloj);
		if (u.bloqueado(ahora)) {
			usuarios.registrarIntento(u.usuario(), u.idUsuario(), false, "BLOQUEADO", ip);
			throw new CuentaBloqueadaException(u.minutosDeBloqueo(ahora));
		}
		EstadoMfa estado = mfa.estado(u.idUsuario()).filter(EstadoMfa::activo)
				.orElseThrow(() -> new CredencialesInvalidasException(VENCIDO));
		String motivo = verificar(u.idUsuario(), estado, codigo, ahora);
		if (motivo != null) {
			Usuario.Fallo fallo = u.registrarFallo(ahora);
			usuarios.guardarFallo(u.idUsuario(), fallo.intentos(), fallo.bloqueadoHasta());
			usuarios.registrarIntento(u.usuario(), u.idUsuario(), false, motivo, ip);
			if (fallo.bloquea()) {
				log.warn("Cuenta {} bloqueada por códigos de verificación incorrectos (IP {})", u.usuario(), ip);
				avisos.avisar(AvisosSeguridad.Tipo.CUENTA_BLOQUEADA, u);
				throw new CuentaBloqueadaException(Usuario.BLOQUEO.toMinutes());
			}
			throw new CredencialesInvalidasException("El código no es correcto o ya se usó");
		}
		return entrar(u, ip, ahora);
	}

	@Transactional(readOnly = true)
	public Estado estado(long idUsuario) {
		Usuario u = usuario(idUsuario);
		return mfa.estado(idUsuario).filter(EstadoMfa::activo)
				.map(e -> new Estado(true, e.desde(), e.codigosRestantes(), obligatorio(u)))
				.orElse(new Estado(false, null, 0, obligatorio(u)));
	}

	/** Genera el secreto (aún sin activar) y lo devuelve para la app autenticadora. */
	@Transactional
	public Inicio iniciar(long idUsuario) {
		Usuario u = usuario(idUsuario);
		if (mfa.estado(idUsuario).map(EstadoMfa::activo).orElse(false)) {
			throw new ConflictoException("La verificación en dos pasos ya está activa");
		}
		byte[] secreto = Totp.nuevoSecreto(AZAR);
		mfa.guardarPendiente(idUsuario, secretos.cifrar(secreto));
		String base32 = Totp.base32(secreto);
		String etiqueta = url(EMISOR + ":" + u.usuario());
		return new Inicio(base32, "otpauth://totp/" + etiqueta + "?secret=" + base32 + "&issuer=" + url(EMISOR)
				+ "&algorithm=SHA1&digits=" + Totp.DIGITOS + "&period=" + Totp.INTERVALO_SEGUNDOS);
	}

	/** Igual que iniciar, para quien debe activarla antes de entrar (con el desafío del inicio de sesión). */
	@Transactional
	public Inicio iniciarConDesafio(String desafio) {
		return iniciar(usuarioDe(desafio, true).idUsuario());
	}

	/** Confirma con un código de la app y devuelve los códigos de recuperación (solo esta vez). */
	@Transactional
	public List<String> activar(long idUsuario, String codigo) {
		EstadoMfa e = mfa.estado(idUsuario)
				.orElseThrow(() -> new ReglaNegocioException("Primero genere el código QR"));
		if (e.activo()) {
			throw new ConflictoException("La verificación en dos pasos ya está activa");
		}
		Instant ahora = Instant.now(reloj);
		OptionalLong paso = Totp.verificar(secretos.descifrar(e.secretoCifrado()), codigo, ahora, e.ultimoPaso());
		if (paso.isEmpty()) {
			throw new ReglaNegocioException("El código no coincide. Revise que la hora del teléfono esté al día y escriba el código actual.");
		}
		mfa.activar(idUsuario, ahora, paso.getAsLong());
		log.info("Verificación en dos pasos activada en la cuenta {}", idUsuario);
		avisos.avisar(AvisosSeguridad.Tipo.MFA_ACTIVADA, usuario(idUsuario));
		return nuevosCodigos(idUsuario);
	}

	/** Activa con el desafío y abre la sesión (cuenta que debía activarla para entrar). */
	@Transactional(noRollbackFor = CredencialesInvalidasException.class)
	public Activacion activarConDesafio(String desafio, String codigo, String ip) {
		Usuario u = usuarioDe(desafio, true);
		List<String> codigos = activar(u.idUsuario(), codigo);
		return new Activacion(entrar(u, ip, Instant.now(reloj)), codigos);
	}

	@Transactional
	public List<String> regenerarCodigos(long idUsuario, String clave) {
		Usuario u = usuario(idUsuario);
		comprobarClave(u, clave);
		if (!mfa.estado(idUsuario).map(EstadoMfa::activo).orElse(false)) {
			throw new ReglaNegocioException("La verificación en dos pasos no está activa");
		}
		log.info("Códigos de recuperación renovados en la cuenta {}", idUsuario);
		avisos.avisar(AvisosSeguridad.Tipo.CODIGOS_RENOVADOS, u);
		return nuevosCodigos(idUsuario);
	}

	/** La persona la desactiva con su contraseña y un código (si su rol no la exige). */
	@Transactional
	public void desactivar(long idUsuario, String clave, String codigo) {
		Usuario u = usuario(idUsuario);
		if (obligatorio(u)) {
			throw new ReglaNegocioException("Su rol exige la verificación en dos pasos: no se puede desactivar");
		}
		comprobarClave(u, clave);
		EstadoMfa e = mfa.estado(idUsuario).filter(EstadoMfa::activo)
				.orElseThrow(() -> new ReglaNegocioException("La verificación en dos pasos no está activa"));
		if (verificar(idUsuario, e, codigo, Instant.now(reloj)) != null) {
			throw new CredencialesInvalidasException("El código no es correcto o ya se usó");
		}
		mfa.desactivar(idUsuario);
		log.info("Verificación en dos pasos desactivada por la cuenta {}", idUsuario);
		avisos.avisar(AvisosSeguridad.Tipo.MFA_DESACTIVADA, u);
	}

	/** El administrador la quita a quien perdió el teléfono y los códigos; al entrar la configura de nuevo. */
	@Transactional
	public void reiniciar(long idAdministrador, long idUsuario) {
		Usuario u = usuario(idUsuario);
		mfa.desactivar(idUsuario);
		sesiones.cerrarTodas(idUsuario, "MFA_REINICIADO", null);
		log.warn("Verificación en dos pasos de la cuenta {} reiniciada por el administrador {}", idUsuario, idAdministrador);
		avisos.avisar(AvisosSeguridad.Tipo.MFA_REINICIADA, u);
	}

	@Transactional(readOnly = true)
	public Set<Long> conMfa() {
		return mfa.conMfa();
	}

	// ------------------------------------------------------------------

	private static final String VENCIDO = "El inicio de sesión venció; escriba de nuevo su usuario y contraseña";

	private Sesion entrar(Usuario u, String ip, Instant ahora) {
		usuarios.guardarIngreso(u.idUsuario(), ahora);
		usuarios.registrarIntento(u.usuario(), u.idUsuario(), true, null, ip);
		return sesiones.abrir(u, ip);
	}

	/** null si el código sirve (y queda gastado); si no, el motivo para la bitácora. */
	private String verificar(long idUsuario, EstadoMfa e, String codigo, Instant ahora) {
		String limpio = codigo == null ? "" : codigo.strip();
		if (limpio.matches("\\d{" + Totp.DIGITOS + "}")) {
			OptionalLong paso = Totp.verificar(secretos.descifrar(e.secretoCifrado()), limpio, ahora, e.ultimoPaso());
			return paso.isPresent() && mfa.usarPaso(idUsuario, paso.getAsLong()) ? null : "MFA_INCORRECTO";
		}
		String normal = normalizarCodigo(limpio);
		return normal.length() == 8 && mfa.usarCodigo(idUsuario, sha256(normal), ahora) ? null : "MFA_RECUPERACION_INCORRECTO";
	}

	private List<String> nuevosCodigos(long idUsuario) {
		List<String> codigos = new ArrayList<>();
		for (int i = 0; i < CODIGOS; i++) {
			StringBuilder c = new StringBuilder();
			for (int j = 0; j < 8; j++) {
				c.append(ALFABETO_CODIGOS[AZAR.nextInt(ALFABETO_CODIGOS.length)]);
			}
			codigos.add(c.substring(0, 4) + "-" + c.substring(4));
		}
		mfa.guardarCodigos(idUsuario, codigos.stream().map(c -> sha256(normalizarCodigo(c))).toList());
		return codigos;
	}

	static String normalizarCodigo(String codigo) {
		return codigo.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
	}

	private void comprobarClave(Usuario u, String clave) {
		if (clave == null || !cifrador.coincide(clave, u.hashClave())) {
			throw new CredencialesInvalidasException("La contraseña no es correcta");
		}
	}

	private Usuario usuario(long idUsuario) {
		return usuarios.porId(idUsuario)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + idUsuario));
	}

	// ------------------------------------------------------------------ desafíos

	/** "id:vence:configurar" en Base64 URL, un punto y su HMAC-SHA256. */
	String desafio(long idUsuario, boolean configurar) {
		long vence = Instant.now(reloj).getEpochSecond() + VIGENCIA_DESAFIO_SEGUNDOS;
		String datos = Base64.getUrlEncoder().withoutPadding()
				.encodeToString((idUsuario + ":" + vence + ":" + (configurar ? 1 : 0)).getBytes(StandardCharsets.UTF_8));
		return datos + "." + firma(datos);
	}

	/** El usuario del desafío; falla si está alterado, vencido o es de la otra clase (activar / verificar). */
	private Usuario usuarioDe(String desafio, boolean configurar) {
		try {
			String[] partes = desafio == null ? new String[0] : desafio.split("\\.");
			if (partes.length != 2 || !MessageDigest.isEqual(firma(partes[0]).getBytes(), partes[1].getBytes())) {
				throw new CredencialesInvalidasException(VENCIDO);
			}
			String[] d = new String(Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8).split(":");
			if (Long.parseLong(d[1]) < Instant.now(reloj).getEpochSecond() || ("1".equals(d[2])) != configurar) {
				throw new CredencialesInvalidasException(VENCIDO);
			}
			Usuario u = usuario(Long.parseLong(d[0]));
			if (!u.activo()) {
				throw new CredencialesInvalidasException(VENCIDO);
			}
			return u;
		} catch (IllegalArgumentException | ArrayIndexOutOfBoundsException | RecursoNoEncontradoException e) {
			throw new CredencialesInvalidasException(VENCIDO);
		}
	}

	private String firma(String datos) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(llaveDesafios, "HmacSHA256"));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(datos.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	static String sha256(String texto) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String url(String texto) {
		return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
	}

}
