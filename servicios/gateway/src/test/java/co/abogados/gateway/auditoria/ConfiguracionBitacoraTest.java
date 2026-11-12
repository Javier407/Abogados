package co.abogados.gateway.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Dónde queda la bitácora según la configuración: en PostgreSQL en el servidor, en memoria sin base. */
class ConfiguracionBitacoraTest {

	@Test
	void sinDireccionDeLaBaseQuedaEnMemoria() {
		Bitacora bitacora = new ConfiguracionBitacora().bitacora("  ", "svc_gateway", "");

		assertThat(bitacora).isInstanceOf(BitacoraEnMemoria.class);
	}

	@Test
	void conDireccionVaAPostgresYElGatewayArrancaAunqueLaBaseNoResponda() {
		// Puerto 1 de la propia máquina: nadie escucha. Crear la bitácora no debe fallar ni quedarse esperando.
		Bitacora bitacora = new ConfiguracionBitacora().bitacora("jdbc:postgresql://127.0.0.1:1/gabinete", "svc_gateway", "x");
		try {
			assertThat(bitacora).isInstanceOf(BitacoraPostgres.class);
			assertThat(bitacora.estado().almacen()).isEqualTo("postgres");
			assertThat(bitacora.estado().pendientes()).isZero();
		} finally {
			((BitacoraPostgres) bitacora).close();
		}
	}

}
