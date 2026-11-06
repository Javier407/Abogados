package co.abogados.gabinete.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.abogados.gabinete.dominio.excepciones.ReglaNegocioException;
import co.abogados.gabinete.dominio.modelo.Smmlv;
import co.abogados.gabinete.dominio.modelo.TarifaVista;
import co.abogados.gabinete.dominio.puertos.TarifaRepositorio;

/** Tarifas de referencia en salarios mínimos y su valor en pesos. */
@Service
@Transactional(readOnly = true)
public class TarifaServicio {

	private final TarifaRepositorio tarifas;
	private final Clock reloj;

	public TarifaServicio(TarifaRepositorio tarifas, Clock reloj) {
		this.tarifas = tarifas;
		this.reloj = reloj;
	}

	/** anio null = el año en curso. Si su salario mínimo no está cargado, se usa el último disponible. */
	public List<TarifaVista> listar(Integer anio) {
		Smmlv s = tarifas.smmlv(anio == null ? LocalDate.now(reloj).getYear() : anio)
				.orElseThrow(() -> new ReglaNegocioException("No hay salario mínimo cargado en gabinete.smmlv"));
		return tarifas.listar(s.anio(), s.valor());
	}

}
