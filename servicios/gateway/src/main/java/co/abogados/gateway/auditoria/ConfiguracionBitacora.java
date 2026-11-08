package co.abogados.gateway.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Con DB_URL (en el servidor), la bitácora va a PostgreSQL como svc_gateway, que solo puede agregar y leer.
 * Sin DB_URL (pruebas, o el gateway corriendo en el PC), queda en memoria y se avisa en el log.
 */
@Configuration
public class ConfiguracionBitacora {

	private static final Logger log = LoggerFactory.getLogger(ConfiguracionBitacora.class);

	/** Spring la cierra al apagar (close): guarda lo pendiente y cierra las conexiones. */
	@Bean
	Bitacora bitacora(@Value("${auditoria.bd.url:}") String url, @Value("${auditoria.bd.usuario:svc_gateway}") String usuario,
			@Value("${auditoria.bd.clave:}") String clave) {
		if (url.isBlank()) {
			log.warn("AUDITORIA sin DB_URL: la bitácora queda en memoria y se pierde al reiniciar (solo para desarrollo)");
			return new BitacoraEnMemoria();
		}
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(url);
		config.setUsername(usuario);
		config.setPassword(clave);
		config.setPoolName("auditoria");
		config.setMaximumPoolSize(3);            // un escritor y las consultas del administrador
		config.setMinimumIdle(1);
		config.setInitializationFailTimeout(-1); // si la base aún no responde, el gateway arranca igual
		config.setConnectionTimeout(5_000);
		log.info("AUDITORIA bitácora encadenada en PostgreSQL como {}", usuario);
		return new BitacoraPostgres(new HikariDataSource(config));
	}

}
