package co.abogados.identidad.dominio.puertos;

import co.abogados.identidad.dominio.Usuario;

/**
 * Avisa a la persona cuando cambia algo de la seguridad de su cuenta (F15): así se entera si no fue ella.
 * El aviso sale cuando se confirma el cambio y es de mejor esfuerzo: si RabbitMQ no responde, el cambio vale igual.
 */
public interface AvisosSeguridad {

	enum Tipo {
		CLAVE_CAMBIADA, CLAVE_RESTABLECIDA, CUENTA_BLOQUEADA, MFA_ACTIVADA, MFA_DESACTIVADA, MFA_REINICIADA,
		CODIGOS_RENOVADOS
	}

	void avisar(Tipo tipo, Usuario usuario);

	/** Para las pruebas y para cuando no hay mensajería. */
	AvisosSeguridad NINGUNO = (tipo, usuario) -> {
	};

}
