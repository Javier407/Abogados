package co.abogados.procesos.infraestructura.persistencia;

import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mockito.Answers;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Doble de JdbcClient para las pruebas de los repositorios sin base de datos: guarda cada sentencia con sus
 * parámetros y responde las consultas con filas de prueba elegidas por un fragmento del SQL. Las filas pasan por el
 * RowMapper real, así se prueba también la lectura de columnas.
 */
public class JdbcFalso {

	public record Sentencia(String sql, Map<String, Object> params) {
	}

	public final List<Sentencia> ejecutadas = new ArrayList<>();
	final Map<String, List<Map<String, Object>>> respuestas = new LinkedHashMap<>();
	public final JdbcClient cliente = mock(JdbcClient.class, this::enCliente);

	/** Las consultas cuyo SQL contiene el fragmento devuelven estas filas (columna → valor, en orden). */
	@SafeVarargs
	public final JdbcFalso responder(String fragmento, Map<String, Object>... filas) {
		respuestas.put(fragmento, List.of(filas));
		return this;
	}

	/** Para consultas de una columna (query(Integer.class) y similares). */
	public JdbcFalso responderValor(String fragmento, Object valor) {
		Map<String, Object> fila = new LinkedHashMap<>();
		fila.put("valor", valor);
		respuestas.put(fragmento, List.of(fila));
		return this;
	}

	public Sentencia ultima() {
		return ejecutadas.get(ejecutadas.size() - 1);
	}

	public static Map<String, Object> fila(Object... columnasYValores) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i < columnasYValores.length; i += 2) {
			m.put((String) columnasYValores[i], columnasYValores[i + 1]);
		}
		return m;
	}

	// ------------------------------------------------------------------

	private Object enCliente(InvocationOnMock inv) throws Throwable {
		if (inv.getMethod().getName().equals("sql")) {
			Sentencia s = new Sentencia(inv.getArgument(0), new LinkedHashMap<>());
			ejecutadas.add(s);
			return mock(JdbcClient.StatementSpec.class, i -> enSentencia(s, i));
		}
		return Answers.RETURNS_DEFAULTS.answer(inv);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object enSentencia(Sentencia s, InvocationOnMock inv) throws Throwable {
		Object[] args = inv.getArguments();
		switch (inv.getMethod().getName()) {
			case "param":
				if (args[0] instanceof String nombre) {
					s.params().put(nombre, args[1]);
				} else {
					s.params().put(String.valueOf(s.params().size()), args[args.length > 1 ? 1 : 0]);
				}
				return inv.getMock();
			case "params":
				if (args.length == 1 && args[0] instanceof Map m) {
					s.params().putAll(m);
				}
				return inv.getMock();
			case "update":
				return 1;
			case "query":
				if (args.length == 1 && args[0] instanceof RowMapper mapper) {
					return resultado(filas(s.sql()), mapper);
				}
				if (args.length == 1 && args[0] instanceof Class) {
					return resultado(filas(s.sql()), (rs, n) -> rs.getObject(1));
				}
				return Answers.RETURNS_DEFAULTS.answer(inv);
			default:
				return inv.getMethod().getReturnType().isInstance(inv.getMock()) ? inv.getMock()
						: Answers.RETURNS_DEFAULTS.answer(inv);
		}
	}

	private List<Map<String, Object>> filas(String sql) {
		return respuestas.entrySet().stream().filter(e -> sql.contains(e.getKey())).map(Map.Entry::getValue)
				.findFirst().orElse(List.of());
	}

	private static Object resultado(List<Map<String, Object>> filas, RowMapper<?> mapper) throws SQLException {
		List<Object> lista = new ArrayList<>();
		for (int i = 0; i < filas.size(); i++) {
			lista.add(mapper.mapRow(resultSet(filas.get(i)), i));
		}
		return mock(JdbcClient.MappedQuerySpec.class, inv -> switch (inv.getMethod().getName()) {
			case "list" -> lista;
			case "stream" -> lista.stream();
			case "set" -> new java.util.LinkedHashSet<>(lista);
			case "optional" -> {
				if (lista.size() > 1) throw new IncorrectResultSizeDataAccessException(1, lista.size());
				yield java.util.Optional.ofNullable(lista.isEmpty() ? null : lista.get(0));
			}
			case "single" -> {
				if (lista.isEmpty()) throw new EmptyResultDataAccessException(1);
				yield lista.get(0);
			}
			default -> Answers.RETURNS_DEFAULTS.answer(inv);
		});
	}

	/** Un ResultSet de una sola fila que responde por nombre o por posición (desde 1). */
	static ResultSet resultSet(Map<String, Object> fila) {
		List<String> columnas = new ArrayList<>(fila.keySet());
		boolean[] nulo = { false };
		return mock(ResultSet.class, inv -> {
			String metodo = inv.getMethod().getName();
			if (metodo.equals("wasNull")) {
				return nulo[0];
			}
			Object[] args = inv.getArguments();
			if (!metodo.startsWith("get") || args.length == 0) {
				return Answers.RETURNS_DEFAULTS.answer(inv);
			}
			String columna = args[0] instanceof Integer n ? columnas.get(n - 1) : (String) args[0];
			Object v = fila.get(columna);
			nulo[0] = v == null;
			return switch (metodo) {
				case "getString" -> v == null ? null : v.toString();
				case "getInt" -> v == null ? 0 : ((Number) v).intValue();
				case "getLong" -> v == null ? 0L : ((Number) v).longValue();
				case "getShort" -> v == null ? (short) 0 : ((Number) v).shortValue();
				case "getBoolean" -> v != null && (Boolean) v;
				case "getBigDecimal" -> (BigDecimal) v;
				case "getDate" -> v instanceof LocalDate f ? Date.valueOf(f) : v;
				case "getTimestamp" -> v instanceof Instant t ? Timestamp.from(t) : v;
				case "getObject" -> v;
				default -> Answers.RETURNS_DEFAULTS.answer(inv);
			};
		});
	}

}
