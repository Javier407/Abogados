package co.abogados.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.abogados.identidad.aplicacion.AutenticacionServicio;
import co.abogados.identidad.aplicacion.MfaServicio;
import co.abogados.identidad.aplicacion.SesionesServicio;
import co.abogados.identidad.dominio.CuentaBloqueadaException;
import co.abogados.identidad.dominio.MfaRequeridoException;
import co.abogados.identidad.dominio.Totp;
import co.abogados.identidad.dominio.Usuario;
import co.abogados.identidad.dominio.puertos.AvisosSeguridad;
import co.abogados.identidad.dominio.puertos.AvisosSeguridad.Tipo;
import co.abogados.identidad.infraestructura.AvisosSeguridadRabbit;
import co.abogados.identidad.infraestructura.CifradorAesGcm;
import co.abogados.identidad.infraestructura.EmisorJwtRsa;

/** F15: la persona se entera de los cambios de seguridad de su cuenta. */
class AvisosSeguridadTest {

	static final long ABOGADA = 1019087654L;
	static final String CLAVE = "Prueba2026*";

	final List<String> avisos = new ArrayList<>();
	AutenticacionTest.UsuariosEnMemoria usuarios;
	AutenticacionTest.MutableReloj reloj;
	MfaTest.MfaEnMemoria repoMfa;
	CifradorAesGcm cifrador = new CifradorAesGcm("");
	MfaServicio mfa;
	AutenticacionServicio auth;

	@BeforeEach
	void preparar() {
		usuarios = new AutenticacionTest.UsuariosEnMemoria();
		usuarios.datos.put(ABOGADA, new Usuario(ABOGADA, "crojas", "Carolina Rojas", "c@firma.co",
				AutenticacionTest.HASH_PRUEBA, true, Set.of("ABOGADO"), 0, null));
		reloj = new AutenticacionTest.MutableReloj(AutenticacionTest.AHORA);
		repoMfa = new MfaTest.MfaEnMemoria();
		SesionesServicio s = new SesionesServicio(new SesionesEnMemoria(), usuarios, new EmisorJwtRsa("", Duration.ofMinutes(15)),
				reloj, Duration.ofHours(12), Duration.ofDays(7));
		mfa = new MfaServicio(repoMfa, usuarios, s, cifrador, AutenticacionTest.CIFRADOR, reloj, "");
		auth = new AutenticacionServicio(usuarios, AutenticacionTest.CIFRADOR, s, reloj, mfa);
		AvisosSeguridad registro = (tipo, u) -> avisos.add(tipo + ":" + u.usuario());
		mfa.setAvisos(registro);
		auth.setAvisos(registro);
	}

	String codigo(int desplazamiento) {
		return Totp.codigo(cifrador.descifrar(repoMfa.filas.get(ABOGADA).secretoCifrado()), Totp.paso(reloj.ahora) + desplazamiento);
	}

	@Test
	void cadaCambioDeSeguridadAvisaALaPersona() {
		auth.cambiarClave(ABOGADA, CLAVE, "OtraClave2026x");
		mfa.iniciar(ABOGADA);
		mfa.activar(ABOGADA, codigo(0));
		mfa.regenerarCodigos(ABOGADA, "OtraClave2026x");
		mfa.desactivar(ABOGADA, "OtraClave2026x", codigo(1));
		mfa.iniciar(ABOGADA);
		reloj.ahora = reloj.ahora.plusSeconds(90);
		mfa.activar(ABOGADA, codigo(0));
		mfa.reiniciar(1L, ABOGADA);

		assertThat(avisos).containsExactly("CLAVE_CAMBIADA:crojas", "MFA_ACTIVADA:crojas", "CODIGOS_RENOVADOS:crojas",
				"MFA_DESACTIVADA:crojas", "MFA_ACTIVADA:crojas", "MFA_REINICIADA:crojas");
	}

	@Test
	void elBloqueoPorContrasenaOPorCodigoTambienSeAvisa() {
		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> auth.iniciarSesion("crojas", "mala", "1.2.3.4")).isNotInstanceOf(CuentaBloqueadaException.class);
		}
		assertThatThrownBy(() -> auth.iniciarSesion("crojas", "mala", "1.2.3.4")).isInstanceOf(CuentaBloqueadaException.class);
		assertThat(avisos).containsExactly("CUENTA_BLOQUEADA:crojas");

		usuarios.guardarFallo(ABOGADA, 0, null);
		mfa.iniciar(ABOGADA);
		mfa.activar(ABOGADA, codigo(0));
		MfaRequeridoException e = MfaTest.catchMfa(() -> auth.iniciarSesion("crojas", CLAVE, "1.2.3.4"));
		for (int i = 0; i < 5; i++) {
			try {
				mfa.completar(e.desafio(), "000000", "1.2.3.4");
			} catch (RuntimeException ignorada) {
				// se espera
			}
		}
		assertThat(avisos).containsExactly("CUENTA_BLOQUEADA:crojas", "MFA_ACTIVADA:crojas", "CUENTA_BLOQUEADA:crojas");
	}

	// ------------------------------------------------------------------ el adaptador de RabbitMQ

	static final Usuario CAROLINA = new Usuario(ABOGADA, "crojas", "Carolina Rojas", "c@firma.co", "h", true,
			Set.of("ABOGADO"), 0, null);

	@Test
	@SuppressWarnings("unchecked")
	void publicaCuentaSeguridadEnJsonConElCorreoDeLaPersona() throws Exception {
		RabbitTemplate rabbit = mock(RabbitTemplate.class);
		Clock fijo = Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC);
		new AvisosSeguridadRabbit(rabbit, new ObjectMapper(), fijo, "gabinete.eventos").avisar(Tipo.MFA_ACTIVADA, CAROLINA);

		ArgumentCaptor<Message> m = ArgumentCaptor.forClass(Message.class);
		then(rabbit).should().send(eq("gabinete.eventos"), eq("cuenta.seguridad"), m.capture());
		Map<String, Object> cuerpo = new ObjectMapper().readValue(m.getValue().getBody(), Map.class);
		assertThat(cuerpo).containsEntry("tipo", "MFA_ACTIVADA").containsEntry("email", "c@firma.co")
				.containsEntry("usuario", "crojas").containsEntry("momento", "2026-10-22T15:00:00Z");
		assertThat(m.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
	}

	@Test
	void siRabbitNoRespondeElCambioSigueYSoloQuedaEnElLog() {
		RabbitTemplate rabbit = mock(RabbitTemplate.class);
		willThrow(new AmqpConnectException(new java.net.ConnectException("rechazada"))).given(rabbit)
				.send(any(String.class), any(String.class), any(Message.class));
		AvisosSeguridadRabbit avisosRabbit = new AvisosSeguridadRabbit(rabbit, new ObjectMapper(), Clock.systemUTC(), "x");
		avisosRabbit.avisar(Tipo.CLAVE_CAMBIADA, CAROLINA);    // no lanza
		then(rabbit).should().send(any(String.class), any(String.class), any(Message.class));
	}

	@Test
	void dentroDeUnaTransaccionEsperaAlCommit() {
		RabbitTemplate rabbit = mock(RabbitTemplate.class);
		AvisosSeguridadRabbit avisosRabbit = new AvisosSeguridadRabbit(rabbit, new ObjectMapper(), Clock.systemUTC(), "x");
		TransactionSynchronizationManager.initSynchronization();
		try {
			avisosRabbit.avisar(Tipo.CLAVE_RESTABLECIDA, CAROLINA);
			then(rabbit).should(never()).send(any(String.class), any(String.class), any(Message.class));
			TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
			then(rabbit).should().send(eq("x"), eq("cuenta.seguridad"), any(Message.class));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

}
