package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.abogados.identidad.aplicacion.AccesoExternoServicio;
import co.abogados.identidad.aplicacion.AccesoExternoServicio.Ida;
import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.aplicacion.MfaServicio;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.AccesoExterno.Enlace;
import co.abogados.identidad.dominio.AccesoExterno.Estado;
import co.abogados.identidad.dominio.AccesoExterno.Identidad;
import co.abogados.identidad.dominio.AccesoExterno.Modo;
import co.abogados.identidad.dominio.AccesoExterno.Proveedor;
import co.abogados.identidad.dominio.AccesoExterno.Resultado;
import co.abogados.identidad.dominio.ConflictoException;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.EnlacesExternosRepositorio;
import co.abogados.identidad.dominio.puertos.ProveedorExterno;
import co.abogados.identidad.infraestructura.CifradorAesGcm;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;

/**
 * Entrar con Google, Microsoft o GitHub: solo cuentas ya existentes, enlazadas por la propia persona. El proveedor
 * es un doble (devuelve la identidad según el código); la base y el reloj, en memoria.
 */
class AccesoExternoTest {

	static final long ABOGADA = 1019087654L;
	static final long ADMIN = 79123456L;
	static final long INACTIVA = 52987654L;
	static final String VUELTA = "https://lexora.test/api/auth/externo/google/vuelta";

	AutenticacionTest.UsuariosEnMemoria usuarios;
	AutenticacionTest.MutableReloj reloj;
	EnlacesEnMemoria enlaces;
	ProveedorFalso google;
	AccesoExternoServicio servicio;
	final List<String> avisos = new ArrayList<>();

	@BeforeEach
	void preparar() {
		usuarios = new AutenticacionTest.UsuariosEnMemoria();
		usuarios.datos.put(ABOGADA, new Usuario(ABOGADA, "crojas", "Carolina Rojas", "crojas@firma.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("ABOGADO"), 0, null));
		usuarios.datos.put(ADMIN, new Usuario(ADMIN, "arodriguez", "Andrés Rodríguez", "arodriguez@firma.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("ADMIN", "SOCIO"), 0, null));
		usuarios.datos.put(INACTIVA, new Usuario(INACTIVA, "gpardo", "Gloria Pardo", "gpardo@firma.co",
				AutenticacionTest.HASH_PRUEBA, false, Set.of("ADMINISTRATIVO"), 0, null));
		reloj = new AutenticacionTest.MutableReloj(AutenticacionTest.AHORA);
		SesionesServicio sesiones = new SesionesServicio(new SesionesEnMemoria(), usuarios,
				new EmisorJwtRsa("", Duration.ofMinutes(15)), reloj, Duration.ofHours(12), Duration.ofDays(7));
		// La verificación en dos pasos es obligatoria para ADMIN: debe seguir pidiéndose al entrar con Google
		MfaServicio mfa = new MfaServicio(new MfaTest.MfaEnMemoria(), usuarios, sesiones, new CifradorAesGcm(""),
				AutenticacionTest.CIFRADOR, reloj, "ADMIN");
		AutenticacionServicio auth = new AutenticacionServicio(usuarios, AutenticacionTest.CIFRADOR, sesiones, reloj, mfa);
		enlaces = new EnlacesEnMemoria();
		google = new ProveedorFalso(Proveedor.GOOGLE);
		servicio = new AccesoExternoServicio(new AccesoExternoServicio.Configurados(List.of(google)), enlaces, usuarios,
				auth, reloj);
		servicio.setAvisos((tipo, u) -> avisos.add(tipo + ":" + u.usuario()));
	}

	/** Simula el navegador: va al proveedor, la persona elige su cuenta (código) y vuelve con estado y cookie. */
	Resultado irYVolver(Modo modo, Long idUsuario, String codigo) {
		Ida ida = servicio.iniciar(Proveedor.GOOGLE, modo, idUsuario, VUELTA);
		return servicio.volver(Proveedor.GOOGLE, codigo, estadoDe(ida), ida.comprobante(), null, VUELTA, "100.64.0.9");
	}

	static String estadoDe(Ida ida) {
		return parametro(ida.url(), "state");
	}

	static String parametro(String url, String nombre) {
		for (String par : URI.create(url).getRawQuery().split("&")) {
			String[] kv = par.split("=", 2);
			if (kv[0].equals(nombre)) {
				return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
			}
		}
		return null;
	}

	@Test
	void laPersonaEnlazaSuCuentaYDespuesEntraConElla() {
		google.personas.put("cod-1", new Identidad(Proveedor.GOOGLE, "g-1001", "carolina.rojas@gmail.com", "Carolina"));

		Resultado enlace = irYVolver(Modo.ENLAZAR, ABOGADA, "cod-1");
		assertThat(enlace).isEqualTo(new Resultado.Enlazada(Proveedor.GOOGLE));
		assertThat(servicio.enlaces(ABOGADA)).extracting(Enlace::proveedor, Enlace::correo)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(Proveedor.GOOGLE, "carolina.rojas@gmail.com"));
		assertThat(avisos).containsExactly("CUENTA_EXTERNA_ENLAZADA:crojas");

		Resultado entrada = irYVolver(Modo.ENTRAR, null, "cod-1");
		assertThat(entrada).isInstanceOf(Resultado.SesionAbierta.class);
		assertThat(((Resultado.SesionAbierta) entrada).sesion().idUsuario()).isEqualTo(ABOGADA);
		assertThat(usuarios.intentos).last().isEqualTo("crojas (google)|" + ABOGADA + "|true|EXTERNO_GOOGLE");
		assertThat(enlaces.filas.get("GOOGLE|g-1001").ultimoUso()).isEqualTo(AutenticacionTest.AHORA);
	}

	@Test
	void unaCuentaExternaSinEnlazarNoEntraNiCreaCuentaAunqueElCorreoCoincida() {
		// Mismo correo que una cuenta de la firma: no se enlaza solo (alguien puede tener ese correo en otro proveedor)
		google.personas.put("cod-x", new Identidad(Proveedor.GOOGLE, "g-9999", "crojas@firma.co", "Impostor"));

		assertThat(irYVolver(Modo.ENTRAR, null, "cod-x")).isEqualTo(new Resultado.Fallo(Modo.ENTRAR, "sin-enlace"));
		assertThat(enlaces.filas).isEmpty();
		assertThat(usuarios.datos).hasSize(3);
	}

	@Test
	void elEstadoEsDeUnSoloUsoDeCortaVidaYDelMismoNavegador() {
		google.personas.put("cod-1", new Identidad(Proveedor.GOOGLE, "g-1001", null, null));
		enlaces.enlazar(ABOGADA, google.personas.get("cod-1"), AutenticacionTest.AHORA);

		// Otro navegador (sin la cookie o con otra): alguien intenta hacer entrar a la víctima con su vuelta
		Ida ida = servicio.iniciar(Proveedor.GOOGLE, Modo.ENTRAR, null, VUELTA);
		assertThat(servicio.volver(Proveedor.GOOGLE, "cod-1", estadoDe(ida), "otra-cookie", null, VUELTA, "ip"))
				.isEqualTo(new Resultado.Fallo(Modo.ENTRAR, "otro-navegador"));
		// El estado ya se gastó: repetir la vuelta, aun con la cookie correcta, no sirve
		assertThat(servicio.volver(Proveedor.GOOGLE, "cod-1", estadoDe(ida), ida.comprobante(), null, VUELTA, "ip"))
				.isEqualTo(new Resultado.Fallo(Modo.ENTRAR, "vencido"));

		// Más de 10 minutos en el proveedor: vencido
		Ida lenta = servicio.iniciar(Proveedor.GOOGLE, Modo.ENTRAR, null, VUELTA);
		reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(11));
		assertThat(servicio.volver(Proveedor.GOOGLE, "cod-1", estadoDe(lenta), lenta.comprobante(), null, VUELTA, "ip"))
				.isEqualTo(new Resultado.Fallo(Modo.ENTRAR, "vencido"));
		// Un estado inventado tampoco
		assertThat(servicio.volver(Proveedor.GOOGLE, "cod-1", "inventado", "x", null, VUELTA, "ip"))
				.isEqualTo(new Resultado.Fallo(Modo.ENTRAR, "vencido"));
	}

	@Test
	void laIdaUsaPkceS256YNoExponeElVerificador() {
		Ida ida = servicio.iniciar(Proveedor.GOOGLE, Modo.ENTRAR, null, VUELTA);
		Estado guardado = enlaces.estados.values().iterator().next();

		assertThat(parametro(ida.url(), "code_challenge")).isEqualTo(AccesoExternoServicio.reto(guardado.verificador()));
		assertThat(ida.url()).doesNotContain(guardado.verificador());
		// En la base solo hay hashes del estado y del comprobante
		assertThat(guardado.hashEstado()).isNotEqualTo(estadoDe(ida)).hasSize(64);
		assertThat(guardado.hashComprobante()).isNotEqualTo(ida.comprobante()).hasSize(64);
		// Vector de prueba del RFC 7636 (apéndice B)
		assertThat(AccesoExternoServicio.reto("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
	}

	@Test
	void conCuentaExternaSeSiguePidiendoLaVerificacionEnDosPasos() {
		Identidad g = new Identidad(Proveedor.GOOGLE, "g-admin", null, null);
		google.personas.put("cod-a", g);
		enlaces.enlazar(ADMIN, g, AutenticacionTest.AHORA);

		Resultado r = irYVolver(Modo.ENTRAR, null, "cod-a");
		assertThat(r).isInstanceOf(Resultado.SegundoPaso.class);
		assertThat(((Resultado.SegundoPaso) r).configurar()).isTrue();   // ADMIN debe activarla antes de entrar
	}

	@Test
	void unaCuentaInactivaOConElEnlaceDeOtraPersonaNoSirve() {
		Identidad g = new Identidad(Proveedor.GOOGLE, "g-gloria", null, null);
		google.personas.put("cod-g", g);
		enlaces.enlazar(INACTIVA, g, AutenticacionTest.AHORA);
		assertThat(irYVolver(Modo.ENTRAR, null, "cod-g")).isEqualTo(new Resultado.Fallo(Modo.ENTRAR, "inactiva"));

		// La abogada intenta enlazar la cuenta de Google que ya es de Gloria
		assertThat(irYVolver(Modo.ENLAZAR, ABOGADA, "cod-g")).isEqualTo(new Resultado.Fallo(Modo.ENLAZAR, "de-otra-persona"));
		assertThat(enlaces.duenoDe(Proveedor.GOOGLE, "g-gloria")).contains(INACTIVA);
	}

	@Test
	void unaPersonaTieneUnaSolaCuentaPorProveedorYPuedeQuitarla() {
		google.personas.put("cod-1", new Identidad(Proveedor.GOOGLE, "g-1", null, null));
		irYVolver(Modo.ENLAZAR, ABOGADA, "cod-1");

		assertThatThrownBy(() -> servicio.iniciar(Proveedor.GOOGLE, Modo.ENLAZAR, ABOGADA, VUELTA))
				.isInstanceOf(ConflictoException.class);

		servicio.quitar(ABOGADA, Proveedor.GOOGLE);
		assertThat(servicio.enlaces(ABOGADA)).isEmpty();
		assertThat(avisos).containsExactly("CUENTA_EXTERNA_ENLAZADA:crojas", "CUENTA_EXTERNA_QUITADA:crojas");
		assertThat(irYVolver(Modo.ENTRAR, null, "cod-1")).isEqualTo(new Resultado.Fallo(Modo.ENTRAR, "sin-enlace"));
	}

	@Test
	void siLaPersonaCancelaOElProveedorFallaSeVuelveConUnMotivo() {
		Ida ida = servicio.iniciar(Proveedor.GOOGLE, Modo.ENLAZAR, ABOGADA, VUELTA);
		assertThat(servicio.volver(Proveedor.GOOGLE, null, estadoDe(ida), ida.comprobante(), "access_denied", VUELTA, "ip"))
				.isEqualTo(new Resultado.Fallo(Modo.ENLAZAR, "cancelado"));

		Ida otra = servicio.iniciar(Proveedor.GOOGLE, Modo.ENTRAR, null, VUELTA);
		assertThat(servicio.volver(Proveedor.GOOGLE, "codigo-que-el-proveedor-rechaza", estadoDe(otra), otra.comprobante(),
				null, VUELTA, "ip")).isEqualTo(new Resultado.Fallo(Modo.ENTRAR, "proveedor"));
	}

	@Test
	void soloSeOfrecenLosProveedoresConfigurados() {
		assertThat(servicio.disponibles()).containsExactly(Proveedor.GOOGLE);
		assertThat(Proveedor.de("github")).contains(Proveedor.GITHUB);
		assertThat(Proveedor.de("../etc")).isEmpty();
	}

	// ---------- dobles ----------

	static class ProveedorFalso implements ProveedorExterno {
		final Proveedor proveedor;
		final Map<String, Identidad> personas = new HashMap<>();

		ProveedorFalso(Proveedor proveedor) { this.proveedor = proveedor; }

		public Proveedor proveedor() { return proveedor; }

		public String urlAutorizacion(String estado, String reto, String urlVuelta) {
			return "https://proveedor.test/auth?state=" + estado + "&code_challenge=" + reto + "&redirect_uri=" + urlVuelta;
		}

		public Identidad canjear(String codigo, String verificador, String urlVuelta) {
			Identidad i = personas.get(codigo);
			if (i == null) {
				throw new IllegalStateException("invalid_grant");
			}
			return i;
		}
	}

	static class EnlacesEnMemoria implements EnlacesExternosRepositorio {
		record Fila(long idUsuario, Identidad identidad, Instant creada, Instant ultimoUso) {
		}

		final Map<String, Fila> filas = new HashMap<>();
		final Map<String, Estado> estados = new HashMap<>();

		static String llave(Proveedor p, String sujeto) { return p.name() + "|" + sujeto; }

		public Optional<Long> duenoDe(Proveedor p, String sujeto) {
			return Optional.ofNullable(filas.get(llave(p, sujeto))).map(Fila::idUsuario);
		}
		public boolean tieneEnlace(long id, Proveedor p) {
			return filas.values().stream().anyMatch(f -> f.idUsuario() == id && f.identidad().proveedor() == p);
		}
		public void enlazar(long id, Identidad i, Instant ahora) {
			filas.put(llave(i.proveedor(), i.sujeto()), new Fila(id, i, ahora, null));
		}
		public void registrarUso(Proveedor p, String sujeto, Instant ahora) {
			filas.computeIfPresent(llave(p, sujeto), (k, f) -> new Fila(f.idUsuario(), f.identidad(), f.creada(), ahora));
		}
		public List<Enlace> enlaces(long id) {
			return filas.values().stream().filter(f -> f.idUsuario() == id)
					.map(f -> new Enlace(f.identidad().proveedor(), f.identidad().correo(), f.creada(), f.ultimoUso())).toList();
		}
		public boolean quitar(long id, Proveedor p) {
			return filas.values().removeIf(f -> f.idUsuario() == id && f.identidad().proveedor() == p);
		}
		public void guardarEstado(Estado e) { estados.put(e.hashEstado(), e); }
		public Optional<Estado> tomarEstado(String hash) { return Optional.ofNullable(estados.remove(hash)); }
		public void limpiarVencidos(Instant ahora) { estados.values().removeIf(e -> e.vence().isBefore(ahora)); }
	}

}
