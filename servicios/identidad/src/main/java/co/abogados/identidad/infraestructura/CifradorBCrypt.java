package co.abogados.identidad.infraestructura;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import co.abogados.identidad.dominio.puertos.Cifrador;

/** BCrypt con costo 10: compatible con los hash $2a$ que genera pgcrypto en la base de pruebas. */
@Component
public class CifradorBCrypt implements Cifrador {

	private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

	@Override
	public String cifrar(String clave) {
		return bcrypt.encode(clave);
	}

	@Override
	public boolean coincide(String clave, String hash) {
		return hash != null && bcrypt.matches(clave, hash);
	}

}
