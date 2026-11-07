package co.abogados.identidad.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.identidad.dominio.AccesoExterno.Enlace;
import co.abogados.identidad.dominio.AccesoExterno.Estado;
import co.abogados.identidad.dominio.AccesoExterno.Identidad;
import co.abogados.identidad.dominio.AccesoExterno.Modo;
import co.abogados.identidad.dominio.AccesoExterno.Proveedor;
import co.abogados.identidad.dominio.AccesoExterno.Resultado;
import co.abogados.identidad.dominio.ConflictoException;
import co.abogados.identidad.dominio.CredencialesInvalidasException;
import co.abogados.identidad.dominio.CuentaBloqueadaException;
import co.abogados.identidad.dominio.MfaRequeridoException;
import co.abogados.identidad.dominio.RecursoNoEncontradoException;
import co.abogados.identidad.dominio.ReglaNegocioException;
import co.abogados.identidad.dominio.puertos.AvisosSeguridad;
import co.abogados.identidad.dominio.puertos.EnlacesExternosRepositorio;
import co.abogados.identidad.dominio.puertos.ProveedorExterno;
import co.abogados.identidad.dominio.puertos.UsuarioRepositorio;

/**
 * Entrar con Google, Microsoft o GitHub, solo en cuentas que ya existen.
 * - Enlazar: la persona, ya dentro con su contraseña, enlaza su cuenta externa desde «Mi cuenta». Nunca se crea una
 *   cuenta ni se enlaza sola por coincidir el correo (alguien podría tener ese correo en otro proveedor).
 * - Entrar: si la cuenta externa está enlazada, se abre la sesión con las mismas reglas que la contraseña
 *   (bloqueo, cuenta inactiva, verificación en dos pasos).
 * - Cada ida al proveedor lleva un estado de un solo uso (10 min), atado al navegador que la inició con un
 *   comprobante en cookie, y PKCE: el código de la vuelta no sirve en otro navegador ni dos veces.
 */
@Service
public class AccesoExternoServicio {

	private static final Logger log = LoggerFactory.getLogger(AccesoExternoServicio.class);
	public static final Duration VIDA_ESTADO = Duration.ofMinutes(10);
	private static final SecureRandom AZAR = new SecureRandom();

	/** Los proveedores con identificador y secreto en infra/.env (puede no haber ninguno). */
	public record Configurados(List<ProveedorExterno> lista) {
	}

	/** Lo que se devuelve al iniciar la ida: la URL del proveedor y el comprobante que va en la cookie. */
	public record Ida(String url, String comprobante) {
	}

	private final Map<Proveedor, ProveedorExterno> proveedores;
	private final EnlacesExternosRepositorio enlaces;
	private final UsuarioRepositorio usuarios;
	private final AutenticacionServicio autenticacion;
	private final Clock reloj;
	private AvisosSeguridad avisos = AvisosSeguridad.NINGUNO;

	public AccesoExternoServicio(Configurados proveedores, EnlacesExternosRepositorio enlaces,
			UsuarioRepositorio usuarios, AutenticacionServicio autenticacion, Clock reloj) {
		this.proveedores = proveedores.lista().stream().collect(Collectors.toMap(ProveedorExterno::proveedor, Function.identity()));
		this.enlaces = enlaces;
		this.usuarios = usuarios;
		this.autenticacion = autenticacion;
		this.reloj = reloj;
	}

	@Autowired(required = false)
	public void setAvisos(AvisosSeguridad avisos) {
		this.avisos = avisos;
	}

	/** Los proveedores configurados (con identificador y secreto en infra/.env), en orden fijo. */
	public List<Proveedor> disponibles() {
		return java.util.Arrays.stream(Proveedor.values()).filter(proveedores::containsKey).toList();
	}

	/** Prepara la ida al proveedor. idUsuario: quién enlaza (solo en modo ENLAZAR). */
	@Transactional
	public Ida iniciar(Proveedor proveedor, Modo modo, Long idUsuario, String urlVuelta) {
		ProveedorExterno p = proveedor(proveedor);
		if (modo == Modo.ENLAZAR) {
			if (idUsuario == null) {
				throw new ReglaNegocioException("Inicie sesión para enlazar una cuenta");
			}
			if (enlaces.tieneEnlace(idUsuario, proveedor)) {
				throw new ConflictoException("Ya tiene una cuenta de " + proveedor.nombre() + " enlazada; quítela primero");
			}
		}
		Instant ahora = Instant.now(reloj);
		enlaces.limpiarVencidos(ahora);
		String estado = aleatorio(32);
		String comprobante = aleatorio(32);
		String verificador = aleatorio(48);   // 64 caracteres: dentro de los 43-128 de PKCE
		enlaces.guardarEstado(new Estado(sha256(estado), sha256(comprobante), proveedor, modo,
				modo == Modo.ENLAZAR ? idUsuario : null, verificador, ahora.plus(VIDA_ESTADO)));
		return new Ida(p.urlAutorizacion(estado, reto(verificador), urlVuelta), comprobante);
	}

	/**
	 * La vuelta del proveedor. Nunca lanza: todo termina en un Resultado que la interfaz sabe mostrar.
	 * errorProveedor: lo que manda el proveedor si la persona canceló o algo falló de su lado.
	 */
	@Transactional(noRollbackFor = { CredencialesInvalidasException.class, CuentaBloqueadaException.class,
			MfaRequeridoException.class })
	public Resultado volver(Proveedor proveedor, String codigo, String estado, String comprobante, String errorProveedor,
			String urlVuelta, String ip) {
		Instant ahora = Instant.now(reloj);
		Optional<Estado> guardado = estado == null || estado.isBlank() ? Optional.empty() : enlaces.tomarEstado(sha256(estado));
		if (guardado.isEmpty()) {
			return new Resultado.Fallo(Modo.ENTRAR, "vencido");
		}
		Estado e = guardado.get();
		if (e.vence().isBefore(ahora) || e.proveedor() != proveedor) {
			return new Resultado.Fallo(e.modo(), "vencido");
		}
		if (comprobante == null || !MessageDigest.isEqual(sha256(comprobante).getBytes(StandardCharsets.US_ASCII),
				e.hashComprobante().getBytes(StandardCharsets.US_ASCII))) {
			log.warn("Vuelta de {} con un estado válido pero desde otro navegador (IP {})", proveedor, ip);
			return new Resultado.Fallo(e.modo(), "otro-navegador");
		}
		if (errorProveedor != null && !errorProveedor.isBlank()) {
			return new Resultado.Fallo(e.modo(), "cancelado");
		}
		if (codigo == null || codigo.isBlank()) {
			return new Resultado.Fallo(e.modo(), "proveedor");
		}
		Identidad identidad;
		try {
			identidad = proveedor(proveedor).canjear(codigo, e.verificador(), urlVuelta);
		} catch (RuntimeException ex) {
			log.warn("No se pudo canjear el código de {}: {}", proveedor, ex.getMessage());
			return new Resultado.Fallo(e.modo(), "proveedor");
		}
		return e.modo() == Modo.ENLAZAR ? enlazar(e.idUsuario(), identidad, ahora) : entrar(identidad, ahora, ip);
	}

	@Transactional(readOnly = true)
	public List<Enlace> enlaces(long idUsuario) {
		return enlaces.enlaces(idUsuario);
	}

	@Transactional
	public void quitar(long idUsuario, Proveedor proveedor) {
		if (!enlaces.quitar(idUsuario, proveedor)) {
			throw new RecursoNoEncontradoException("No tiene una cuenta de " + proveedor.nombre() + " enlazada");
		}
		usuarios.porId(idUsuario).ifPresent(u -> avisos.avisar(AvisosSeguridad.Tipo.CUENTA_EXTERNA_QUITADA, u));
	}

	private Resultado enlazar(Long idUsuario, Identidad identidad, Instant ahora) {
		if (idUsuario == null) {
			return new Resultado.Fallo(Modo.ENLAZAR, "vencido");
		}
		Optional<Long> dueno = enlaces.duenoDe(identidad.proveedor(), identidad.sujeto());
		if (dueno.isPresent()) {
			return dueno.get().equals(idUsuario) ? new Resultado.Enlazada(identidad.proveedor())
					: new Resultado.Fallo(Modo.ENLAZAR, "de-otra-persona");
		}
		if (enlaces.tieneEnlace(idUsuario, identidad.proveedor())) {
			return new Resultado.Fallo(Modo.ENLAZAR, "ya-enlazada");
		}
		enlaces.enlazar(idUsuario, identidad, ahora);
		usuarios.porId(idUsuario).ifPresent(u -> avisos.avisar(AvisosSeguridad.Tipo.CUENTA_EXTERNA_ENLAZADA, u));
		log.info("Usuario {} enlazó su cuenta de {}", idUsuario, identidad.proveedor());
		return new Resultado.Enlazada(identidad.proveedor());
	}

	private Resultado entrar(Identidad identidad, Instant ahora, String ip) {
		Optional<Long> dueno = enlaces.duenoDe(identidad.proveedor(), identidad.sujeto());
		if (dueno.isEmpty()) {
			// Sin crear cuentas ni enlazar por correo: la persona debe enlazarla primero desde «Mi cuenta».
			return new Resultado.Fallo(Modo.ENTRAR, "sin-enlace");
		}
		try {
			var sesion = autenticacion.iniciarSesionExterna(dueno.get(), identidad.proveedor().name(), ip);
			enlaces.registrarUso(identidad.proveedor(), identidad.sujeto(), ahora);
			return new Resultado.SesionAbierta(sesion);
		} catch (MfaRequeridoException m) {
			enlaces.registrarUso(identidad.proveedor(), identidad.sujeto(), ahora);
			return new Resultado.SegundoPaso(m.desafio(), m.configurar());
		} catch (CuentaBloqueadaException b) {
			return new Resultado.Fallo(Modo.ENTRAR, "bloqueada");
		} catch (CredencialesInvalidasException c) {
			return new Resultado.Fallo(Modo.ENTRAR, "inactiva");
		}
	}

	private ProveedorExterno proveedor(Proveedor proveedor) {
		ProveedorExterno p = proveedores.get(proveedor);
		if (p == null) {
			throw new RecursoNoEncontradoException("Entrar con " + proveedor.nombre() + " no está configurado");
		}
		return p;
	}

	static String aleatorio(int bytes) {
		byte[] b = new byte[bytes];
		AZAR.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	/** Reto PKCE S256: BASE64URL(SHA-256(verificador)). */
	public static String reto(String verificador) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(verificador));
	}

	static String sha256(String texto) {
		return HexFormat.of().formatHex(digest(texto));
	}

	private static byte[] digest(String texto) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.US_ASCII));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
