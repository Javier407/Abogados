package co.abogados.procesos.infraestructura.persistencia;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.abogados.procesos.dominio.modelo.DiaInhabil;
import co.abogados.procesos.dominio.puertos.CalendarioRepositorio;

@Repository
public class CalendarioRepositorioJdbc implements CalendarioRepositorio {

	private final JdbcClient jdbc;

	public CalendarioRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<DiaInhabil> diasInhabiles() {
		return jdbc.sql("SELECT fecha, descripcion, tipo FROM procesos.festivos ORDER BY fecha")
				.query((rs, i) -> new DiaInhabil(rs.getObject("fecha", LocalDate.class), rs.getString("descripcion"),
						rs.getString("tipo")))
				.list();
	}

}
