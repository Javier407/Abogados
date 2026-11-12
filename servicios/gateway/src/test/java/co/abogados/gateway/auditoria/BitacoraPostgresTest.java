package co.abogados.gateway.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * La bitácora en PostgreSQL sin base de datos: se comprueba el SQL que arma, los valores que envía y cómo lee las
 * filas. Que la base encadena, rechaza cambios y calcula el mismo hash lo prueba db/pruebas/015-auditoria.sql.
 */
class BitacoraPostgresTest {

	DataSource datos;
	Connection conexion;
	PreparedStatement sentencia;
	BitacoraPostgres bitacora;

	@BeforeEach
	void preparar() throws SQLException {
		datos = mock(DataSource.class, withSettings().extraInterfaces(AutoCloseable.class));
		conexion = mock(Connection.class);
		sentencia = mock(PreparedStatement.class);
		when(datos.getConnection()).thenReturn(conexion);
		when(conexion.prepareStatement(anyString())).thenReturn(sentencia);
		bitacora = new BitacoraPostgres(datos, 100, 10, Duration.ofMillis(5), Duration.ofMillis(20));
	}

	@AfterEach
	void cerrar() {
		bitacora.close();
	}

	/** Un ResultSet que recorre los registros dados, con los nombres de columna de auditoria.registros. */
	static ResultSet filas(List<Registro> registros) throws SQLException {
		int[] i = { -1 };
		ResultSet rs = mock(ResultSet.class);
		when(rs.next()).thenAnswer(inv -> ++i[0] < registros.size());
		when(rs.getLong("id")).thenAnswer(inv -> registros.get(i[0]).id());
		when(rs.getObject("momento", OffsetDateTime.class))
				.thenAnswer(inv -> registros.get(i[0]).acceso().momento().atOffset(ZoneOffset.UTC));
		when(rs.getString("usuario")).thenAnswer(inv -> registros.get(i[0]).acceso().usuario());
		when(rs.getString("roles")).thenAnswer(inv -> registros.get(i[0]).acceso().roles());
		when(rs.getString("metodo")).thenAnswer(inv -> registros.get(i[0]).acceso().metodo());
		when(rs.getString("ruta")).thenAnswer(inv -> registros.get(i[0]).acceso().ruta());
		when(rs.getShort("estado")).thenAnswer(inv -> {
			Integer estado = registros.get(i[0]).acceso().estado();
			return estado == null ? (short) 0 : estado.shortValue();
		});
		when(rs.wasNull()).thenAnswer(inv -> registros.get(i[0]).acceso().estado() == null);
		when(rs.getInt("duracion_ms")).thenAnswer(inv -> registros.get(i[0]).acceso().duracionMs());
		when(rs.getString("ip")).thenAnswer(inv -> registros.get(i[0]).acceso().ip());
		when(rs.getString("hash_anterior")).thenAnswer(inv -> registros.get(i[0]).hashAnterior());
		when(rs.getString("hash")).thenAnswer(inv -> registros.get(i[0]).hash());
		return rs;
	}

	String sqlPreparado() throws SQLException {
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(conexion).prepareStatement(sql.capture());
		return sql.getValue();
	}

	@Test
	void guardaElLoteEnUnaSolaTransaccionSinEscribirNumeroNiHash() throws SQLException {
		bitacora.guardar(List.of(CadenaTest.PRIMERO, CadenaTest.SEGUNDO));

		// El número y los hashes los pone la base (disparador): el gateway no los envía.
		assertThat(sqlPreparado()).startsWith("INSERT INTO auditoria.registros")
				.contains("(momento, usuario, roles, metodo, ruta, estado, duracion_ms, ip)")
				.doesNotContain("hash");
		InOrder orden = inOrder(conexion, sentencia);
		orden.verify(conexion).setAutoCommit(false);
		orden.verify(sentencia).setObject(1, CadenaTest.PRIMERO.momento().atOffset(ZoneOffset.UTC));
		orden.verify(sentencia).setShort(6, (short) 200);
		orden.verify(sentencia).addBatch();
		orden.verify(sentencia).setNull(6, Types.SMALLINT);       // petición cortada: sin código de respuesta
		orden.verify(sentencia).addBatch();
		orden.verify(sentencia).executeBatch();
		orden.verify(conexion).commit();
		verify(sentencia).setString(2, "1020304050");
		verify(sentencia).setString(2, Acceso.ANONIMO);
		verify(sentencia).setString(8, "100.64.0.7");
		verify(sentencia).setString(8, null);
		verify(conexion, never()).rollback();
	}

	@Test
	void siLaBaseRechazaElLoteLoDeshaceYAvisaParaReintentar() throws SQLException {
		when(sentencia.executeBatch()).thenThrow(new SQLException("sin conexión"));

		assertThatThrownBy(() -> bitacora.guardar(List.of(CadenaTest.PRIMERO)))
				.isInstanceOf(SQLException.class).hasMessage("sin conexión");
		verify(conexion).rollback();
		verify(conexion, never()).commit();
	}

	@Test
	void anotarNoEsperaALaBaseYElEscritorGuardaDespues() throws SQLException {
		bitacora.anotar(CadenaTest.PRIMERO);

		verify(sentencia, timeout(3_000)).executeBatch();
		verify(conexion, timeout(3_000)).commit();
		assertThat(bitacora.estado().almacen()).isEqualTo("postgres");
		assertThat(bitacora.estado().perdidos()).isZero();
	}

	@Test
	void buscaConTodosLosFiltrosYLeeLasFilas() throws SQLException {
		List<Registro> cadena = CadenaTest.dos();
		ResultSet rs = filas(List.of(cadena.get(1), cadena.get(0)));
		when(sentencia.executeQuery()).thenReturn(rs);
		Instant desde = Instant.parse("2026-11-08T00:00:00Z");
		Instant hasta = Instant.parse("2026-11-09T00:00:00Z");

		List<Registro> encontrados = bitacora.buscar(
				new Bitacora.Filtro("1020304050", "get", "Procesos", true, desde, hasta, 50L, 20));

		assertThat(sqlPreparado()).isEqualTo("SELECT id, momento, usuario, roles, metodo, ruta, estado, duracion_ms, ip, "
				+ "hash_anterior, hash FROM auditoria.registros WHERE true AND usuario = ? AND metodo = ?"
				+ " AND strpos(lower(ruta), lower(?)) > 0 AND (estado IS NULL OR estado >= 400)"
				+ " AND momento >= ? AND momento < ? AND id < ? ORDER BY id DESC LIMIT ?");
		verify(sentencia).setObject(1, "1020304050");
		verify(sentencia).setObject(2, "GET");                  // el método se compara en mayúsculas
		verify(sentencia).setObject(3, "Procesos");
		verify(sentencia).setObject(4, desde.atOffset(ZoneOffset.UTC));
		verify(sentencia).setObject(5, hasta.atOffset(ZoneOffset.UTC));
		verify(sentencia).setObject(6, 50L);
		verify(sentencia).setObject(7, 20);
		assertThat(encontrados).containsExactly(cadena.get(1), cadena.get(0));
		assertThat(encontrados.get(0).acceso().estado()).isNull();   // NULL en la base sigue siendo «sin código»
		assertThat(encontrados.get(1).acceso().estado()).isEqualTo(200);
	}

	@Test
	void sinFiltrosSoloPideLosMasRecientes() throws SQLException {
		ResultSet vacio = filas(List.of());
		when(sentencia.executeQuery()).thenReturn(vacio);

		assertThat(bitacora.buscar(new Bitacora.Filtro(null, null, null, false, null, null, null, 5_000))).isEmpty();

		assertThat(sqlPreparado()).endsWith("FROM auditoria.registros WHERE true ORDER BY id DESC LIMIT ?");
		verify(sentencia).setObject(1, 200);                    // el límite nunca pasa de 200
		verify(sentencia, times(1)).setObject(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void buscaUnRegistroPorSuNumero() throws SQLException {
		Registro primero = CadenaTest.dos().get(0);
		ResultSet rs = filas(List.of(primero));
		ResultSet vacio = filas(List.of());
		when(sentencia.executeQuery()).thenReturn(rs, vacio);

		assertThat(bitacora.registro(1)).contains(primero);
		assertThat(bitacora.registro(99)).isEmpty();
		verify(sentencia).setLong(1, 1L);
		verify(sentencia).setLong(1, 99L);
	}

	@Test
	void revisaLaCadenaCompletaLeyendoEnOrdenYSinEscribir() throws SQLException {
		ResultSet rs = filas(CadenaTest.dos());
		when(sentencia.executeQuery()).thenReturn(rs);

		Cadena.Verificacion v = bitacora.verificar(0, null);

		assertThat(v.integra()).isTrue();
		assertThat(v.revisados()).isEqualTo(2);
		assertThat(v.ultimoHash()).isEqualTo(CadenaTest.HASH_2);
		assertThat(sqlPreparado()).endsWith("FROM auditoria.registros WHERE id >= ? ORDER BY id");
		verify(sentencia).setLong(1, 1L);
		verify(sentencia).setFetchSize(2_000);                  // con cursor: no carga toda la tabla
		verify(conexion).setReadOnly(true);
		verify(conexion).rollback();
		verify(conexion, never()).commit();
	}

	@Test
	void notaUnRegistroCambiadoDespuesDeGuardado() throws SQLException {
		List<Registro> cadena = CadenaTest.dos();
		Acceso otro = new Acceso(CadenaTest.SEGUNDO.momento(), "1020304050", "ADMIN", "POST", "/api/auth/login", 200, 3, null);
		Registro manipulado = new Registro(2, otro, cadena.get(1).hashAnterior(), cadena.get(1).hash());
		ResultSet rs = filas(List.of(cadena.get(0), manipulado));
		when(sentencia.executeQuery()).thenReturn(rs);

		Cadena.Verificacion v = bitacora.verificar(0, null);

		assertThat(v.integra()).isFalse();
		assertThat(v.roturaEn()).isEqualTo(2L);
		assertThat(v.motivo()).contains("cambió después de guardarlo");
		assertThat(v.ultimoId()).isEqualTo(1);                  // hasta ahí sí cuadra
	}

	@Test
	void desdeUnSelloSoloLeeLoNuevoYCompruebaQueElSelloSigueIgual() throws SQLException {
		List<Registro> cadena = CadenaTest.dos();
		ResultSet rs = filas(cadena);
		ResultSet cambiado = filas(cadena);
		when(sentencia.executeQuery()).thenReturn(rs, cambiado);

		Cadena.Verificacion bien = bitacora.verificar(1, CadenaTest.HASH_1);
		Cadena.Verificacion mal = bitacora.verificar(1, "f".repeat(64));

		assertThat(bien.integra()).isTrue();
		assertThat(bien.revisados()).isEqualTo(1);              // el registro del sello no se vuelve a contar
		assertThat(bien.ultimoId()).isEqualTo(2);
		assertThat(mal.integra()).isFalse();
		assertThat(mal.motivo()).contains("sello");
		verify(sentencia, times(2)).setLong(1, 1L);
	}

	@Test
	void siLaBaseNoRespondeAlLeerLoDiceConUnErrorClaro() throws SQLException {
		when(datos.getConnection()).thenThrow(new SQLException("la base no responde"));

		assertThatThrownBy(() -> bitacora.buscar(new Bitacora.Filtro(null, null, null, false, null, null, null, 10)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("No se pudo leer la bitácora");
		assertThatThrownBy(() -> bitacora.registro(1)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> bitacora.verificar(0, null)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void alCerrarGuardaLoPendienteYCierraLasConexiones() throws Exception {
		BitacoraPostgres otra = new BitacoraPostgres(datos, 100, 10, Duration.ofMillis(5), Duration.ofMillis(20));

		otra.close();

		verify((AutoCloseable) datos).close();
	}

}
