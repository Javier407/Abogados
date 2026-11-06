package co.abogados.gabinete.infraestructura.persistencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import co.abogados.gabinete.dominio.modelo.EstadoHonorario;
import co.abogados.gabinete.dominio.modelo.FormaCobro;
import co.abogados.gabinete.dominio.modelo.Honorario;
import co.abogados.gabinete.dominio.modelo.HonorarioVista;
import co.abogados.gabinete.dominio.modelo.MedioPago;
import co.abogados.gabinete.dominio.modelo.Pago;
import co.abogados.gabinete.dominio.modelo.Perito;
import co.abogados.gabinete.dominio.modelo.Smmlv;
import co.abogados.gabinete.dominio.modelo.TarifaVista;
import co.abogados.gabinete.dominio.puertos.HonorarioRepositorio;
import co.abogados.gabinete.dominio.puertos.PeritoRepositorio;
import co.abogados.gabinete.dominio.puertos.TarifaRepositorio;

/** Adaptadores JPA de F4: peritos, tarifas y salario mínimo, honorarios y pagos. */
final class AdaptadoresHonorarios {

	private AdaptadoresHonorarios() {
	}

	@Component
	static class PeritoRepositorioJpa implements PeritoRepositorio {

		private final PeritoJpa jpa;

		PeritoRepositorioJpa(PeritoJpa jpa) {
			this.jpa = jpa;
		}

		@Override
		public List<Perito> listar(String texto) {
			List<PeritoEntidad> filas = texto == null || texto.isBlank() ? jpa.findAllByOrderByNombreAsc()
					: jpa.buscarTexto(texto.strip());
			return filas.stream().map(PeritoRepositorioJpa::aDominio).toList();
		}

		@Override
		public Optional<Perito> buscar(int idPerito) {
			return jpa.findById(idPerito).map(PeritoRepositorioJpa::aDominio);
		}

		@Override
		public Perito guardar(Perito p) {
			PeritoEntidad e = p.idPerito() == null ? new PeritoEntidad()
					: jpa.findById(p.idPerito()).orElseGet(PeritoEntidad::new);
			e.nombre = p.nombre();
			e.especialidad = p.especialidad();
			e.profesion = p.profesion();
			e.registro = p.registro();
			e.telefono = p.telefono();
			e.email = p.email();
			e.ciudad = p.ciudad();
			e.tarifaDictamen = p.tarifaDictamen();
			e.calificacion = p.calificacion();
			return aDominio(jpa.saveAndFlush(e));
		}

		static Perito aDominio(PeritoEntidad e) {
			return new Perito(e.idPerito, e.nombre, e.especialidad, e.profesion, e.registro, e.telefono, e.email,
					e.ciudad, e.tarifaDictamen, e.calificacion);
		}
	}

	@Component
	static class TarifaRepositorioJpa implements TarifaRepositorio {

		private final TarifaJpa tarifas;
		private final SmmlvJpa smmlv;

		TarifaRepositorioJpa(TarifaJpa tarifas, SmmlvJpa smmlv) {
			this.tarifas = tarifas;
			this.smmlv = smmlv;
		}

		@Override
		public List<TarifaVista> listar(int anio, BigDecimal valorSmmlv) {
			return tarifas.findAll(Sort.by("fuente", "codigoRama", "servicio")).stream()
					.map(t -> new TarifaVista(t.idTarifa, t.fuente, t.codigoRama, t.servicio, t.tipoServicio,
							t.formaCobro, t.valorSmmlv, t.valorSmmlvMax, t.porcentaje, t.nota, t.vigenteDesde,
							t.vigenteHasta, anio, pesos(t.valorSmmlv, valorSmmlv), pesos(t.valorSmmlvMax, valorSmmlv)))
					.toList();
		}

		@Override
		public boolean existe(short idTarifa) {
			return tarifas.existsById(idTarifa);
		}

		@Override
		public Optional<Smmlv> smmlv(int anio) {
			return smmlv.findById((short) anio).or(smmlv::findFirstByOrderByAnioDesc)
					.map(s -> new Smmlv(s.anio, s.valor));
		}

		private static BigDecimal pesos(BigDecimal salarios, BigDecimal smmlv) {
			return salarios == null ? null : salarios.multiply(smmlv).setScale(0, RoundingMode.HALF_UP);
		}
	}

	@Component
	static class HonorarioRepositorioJpa implements HonorarioRepositorio {

		private final HonorarioJpa honorarios;
		private final PagoJpa pagos;
		private final ClienteJpa clientes;

		HonorarioRepositorioJpa(HonorarioJpa honorarios, PagoJpa pagos, ClienteJpa clientes) {
			this.honorarios = honorarios;
			this.pagos = pagos;
			this.clientes = clientes;
		}

		@Override
		public List<HonorarioVista> listar(Integer idCliente, Integer idProceso, String estado) {
			List<HonorarioEntidad> filas = honorarios.filtrar(idCliente, idProceso, estado);
			return vistas(filas);
		}

		@Override
		public Optional<HonorarioVista> buscar(int idHonorario) {
			return honorarios.findById(idHonorario).map(h -> vistas(List.of(h)).get(0));
		}

		@Override
		public Optional<Honorario> acuerdo(int idHonorario) {
			return honorarios.findById(idHonorario).map(h -> new Honorario(h.idHonorario, h.idProceso,
					h.cliente.idCliente, h.idTarifa, FormaCobro.valueOf(h.formaCobro), h.valorFijo, h.porcentaje,
					h.valorEstimado, h.fechaPacto, EstadoHonorario.valueOf(h.estado)));
		}

		@Override
		public boolean existeParaProceso(int idProceso) {
			return honorarios.existsByIdProceso(idProceso);
		}

		@Override
		public int crear(Honorario h) {
			HonorarioEntidad e = new HonorarioEntidad();
			e.idProceso = h.idProceso();
			e.cliente = clientes.findById(h.idCliente()).orElseThrow();
			e.idTarifa = h.idTarifa();
			e.formaCobro = h.formaCobro().name();
			e.valorFijo = h.valorFijo();
			e.porcentaje = h.porcentaje();
			e.valorEstimado = h.valorEstimado();
			e.fechaPacto = h.fechaPacto();
			e.estado = h.estado().name();
			return honorarios.saveAndFlush(e).idHonorario;
		}

		@Override
		public void cambiarEstado(int idHonorario, String estado) {
			HonorarioEntidad e = honorarios.findById(idHonorario).orElseThrow();
			e.estado = estado;
			honorarios.saveAndFlush(e);
		}

		@Override
		public List<Pago> pagos(int idHonorario) {
			return pagos.findByIdHonorarioOrderByFechaDescIdPagoDesc(idHonorario).stream()
					.map(HonorarioRepositorioJpa::aPago)
					.toList();
		}

		@Override
		public BigDecimal totalPagado(int idHonorario) {
			return pagos.totalPagado(idHonorario);
		}

		@Override
		public int registrarPago(Pago p) {
			PagoEntidad e = new PagoEntidad();
			e.idHonorario = p.idHonorario();
			e.fecha = p.fecha();
			e.valor = p.valor();
			e.medio = p.medio().name();
			e.referencia = p.referencia();
			return pagos.saveAndFlush(e).idPago;
		}

		@Override
		public Optional<Pago> pago(int idPago) {
			return pagos.findById(idPago).map(HonorarioRepositorioJpa::aPago);
		}

		@Override
		public void anularPago(int idPago, Instant cuando, Long quien, String motivo) {
			PagoEntidad e = pagos.findById(idPago).orElseThrow();
			e.anuladoEn = cuando;
			e.anuladoPor = quien;
			e.motivoAnulacion = motivo;
			pagos.saveAndFlush(e);
		}

		@Override
		public Optional<BigDecimal> valorLiquidado(int idHonorario) {
			return honorarios.findById(idHonorario).map(h -> h.valorLiquidado);
		}

		@Override
		public void liquidar(int idHonorario, BigDecimal base, BigDecimal valor, LocalDate fecha) {
			HonorarioEntidad e = honorarios.findById(idHonorario).orElseThrow();
			e.valorBase = base;
			e.valorLiquidado = valor;
			e.fechaLiquidacion = fecha;
			honorarios.saveAndFlush(e);
		}

		static Pago aPago(PagoEntidad p) {
			return new Pago(p.idPago, p.idHonorario, p.fecha, p.valor, MedioPago.valueOf(p.medio), p.referencia,
					p.anuladoEn, p.anuladoPor, p.motivoAnulacion);
		}

		/** Agrega lo pagado con una sola consulta para todos los acuerdos de la lista. */
		private List<HonorarioVista> vistas(List<HonorarioEntidad> filas) {
			Map<Integer, Object[]> resumen = new HashMap<>();
			if (!filas.isEmpty()) {
				for (Object[] r : pagos.resumen(filas.stream().map(h -> h.idHonorario).toList())) {
					resumen.put((Integer) r[0], r);
				}
			}
			return filas.stream().map(h -> {
				Object[] r = resumen.get(h.idHonorario);
				BigDecimal pagado = r == null ? BigDecimal.ZERO : (BigDecimal) r[1];
				int cantidad = r == null ? 0 : ((Number) r[2]).intValue();
				// El total es lo liquidado si ya se liquidó; si no, el valor fijo en FIJO y MIXTO. En MENSUAL el valor
				// fijo es la cuota de cada mes (no un total) y en porcentaje sin liquidar no se sabe aún: sin saldo.
				BigDecimal total = h.valorLiquidado != null ? h.valorLiquidado
						: "FIJO".equals(h.formaCobro) || "MIXTO".equals(h.formaCobro) ? h.valorFijo : null;
				BigDecimal saldo = total == null ? null : total.subtract(pagado).max(BigDecimal.ZERO);
				return new HonorarioVista(h.idHonorario, h.idProceso, h.cliente.idCliente, h.cliente.nombre, h.idTarifa,
						h.formaCobro, h.valorFijo, h.porcentaje, h.valorEstimado, h.fechaPacto, h.estado, pagado, saldo,
						cantidad, h.valorBase, h.valorLiquidado, h.fechaLiquidacion);
			}).toList();
		}
	}

}
