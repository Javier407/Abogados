package co.abogados.gabinete.infraestructura.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import co.abogados.gabinete.dominio.modelo.Abogado;
import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Cliente;
import co.abogados.gabinete.dominio.modelo.Sede;
import co.abogados.gabinete.dominio.modelo.TipoDocumento;
import co.abogados.gabinete.dominio.puertos.AbogadoRepositorio;
import co.abogados.gabinete.dominio.puertos.CatalogoGabinete;
import co.abogados.gabinete.dominio.puertos.ClienteRepositorio;

/** Implementan los puertos del dominio con JPA y traducen entre entidades y records. */
final class Adaptadores {

	private Adaptadores() {
	}

	@Component
	static class ClienteRepositorioJpa implements ClienteRepositorio {

		private final ClienteJpa jpa;

		ClienteRepositorioJpa(ClienteJpa jpa) {
			this.jpa = jpa;
		}

		@Override
		public List<Cliente> listar(String texto) {
			List<ClienteEntidad> filas = texto == null || texto.isBlank()
					? jpa.findAllByOrderByNombreAsc()
					: jpa.buscarTexto(texto.strip());
			return filas.stream().map(ClienteRepositorioJpa::aDominio).toList();
		}

		@Override
		public Optional<Cliente> buscar(int idCliente) {
			return jpa.findById(idCliente).map(ClienteRepositorioJpa::aDominio);
		}

		@Override
		public Optional<Integer> idPorDocumento(TipoDocumento tipo, String numero) {
			return jpa.findByTipoDocumentoAndNumeroDocumento(tipo, numero).map(e -> e.idCliente);
		}

		@Override
		public Cliente guardar(Cliente c) {
			ClienteEntidad e = c.idCliente() == null ? new ClienteEntidad()
					: jpa.findById(c.idCliente()).orElseGet(ClienteEntidad::new);
			e.tipoPersona = c.tipoPersona();
			e.tipoDocumento = c.tipoDocumento();
			e.numeroDocumento = c.numeroDocumento();
			e.nombre = c.nombre();
			e.email = c.email();
			e.telefono = c.telefono();
			e.direccion = c.direccion();
			e.ciudad = c.ciudad();
			e.idSede = c.idSede();
			e.creado = c.creado();
			return aDominio(jpa.saveAndFlush(e));
		}

		@Override
		public int contarHonorarios(int idCliente) {
			return jpa.contarHonorarios(idCliente);
		}

		@Override
		public void eliminar(int idCliente) {
			jpa.deleteById(idCliente);
		}

		static Cliente aDominio(ClienteEntidad e) {
			return new Cliente(e.idCliente, e.tipoPersona, e.tipoDocumento, e.numeroDocumento, e.nombre, e.email,
					e.telefono, e.direccion, e.ciudad, e.idSede, e.creado);
		}
	}

	@Component
	static class AbogadoRepositorioJpa implements AbogadoRepositorio {

		private final PersonalJpa jpa;
		private final CargoJpa cargos;
		private final SedeJpa sedes;
		private final AreaJpa areas;

		AbogadoRepositorioJpa(PersonalJpa jpa, CargoJpa cargos, SedeJpa sedes, AreaJpa areas) {
			this.jpa = jpa;
			this.cargos = cargos;
			this.sedes = sedes;
			this.areas = areas;
		}

		@Override
		public List<Abogado> listar(boolean soloActivos) {
			return jpa.abogados(soloActivos).stream().map(AbogadoRepositorioJpa::aDominio).toList();
		}

		@Override
		public Optional<Abogado> buscar(long idPersona) {
			return jpa.abogado(idPersona).map(AbogadoRepositorioJpa::aDominio);
		}

		@Override
		public boolean existePersona(long idPersona) {
			return jpa.existsById(idPersona);
		}

		@Override
		public Optional<Long> idPorEmail(String email) {
			return jpa.findByEmailIgnoreCase(email).map(e -> e.idPersona);
		}

		@Override
		public Optional<Long> idPorTarjeta(String tarjeta) {
			return jpa.findByTarjetaProfesional(tarjeta).map(e -> e.idPersona);
		}

		@Override
		public Abogado guardar(Abogado a) {
			// Se parte de la fila existente para no perder columnas que el dominio no maneja (id_usuario).
			PersonalEntidad e = jpa.findById(a.idPersona()).orElseGet(PersonalEntidad::new);
			e.idPersona = a.idPersona();
			e.nombres = a.nombres();
			e.apellidos = a.apellidos();
			e.email = a.email();
			e.telefono = a.telefono();
			e.cargo = cargos.findById(a.idCargo()).orElseThrow();
			e.sede = sedes.findById(a.idSede()).orElseThrow();
			e.area = a.idArea() == null ? null : areas.findById(a.idArea()).orElseThrow();
			e.tarjetaProfesional = a.tarjetaProfesional();
			e.tarifaHora = a.tarifaHora();
			e.fechaIngreso = a.fechaIngreso();
			e.activo = a.activo();
			jpa.saveAndFlush(e);
			return buscar(a.idPersona()).orElseThrow();
		}

		static Abogado aDominio(PersonalEntidad e) {
			return new Abogado(e.idPersona, e.nombres, e.apellidos, e.email, e.telefono, e.cargo.idCargo,
					e.cargo.nombre, e.sede.idSede, e.sede.nombre, e.area == null ? null : e.area.idArea,
					e.area == null ? null : e.area.nombre, e.tarjetaProfesional, e.tarifaHora, e.fechaIngreso,
					e.activo);
		}
	}

	@Component
	static class CatalogoGabineteJpa implements CatalogoGabinete {

		private final SedeJpa sedes;
		private final CargoJpa cargos;
		private final AreaJpa areas;

		CatalogoGabineteJpa(SedeJpa sedes, CargoJpa cargos, AreaJpa areas) {
			this.sedes = sedes;
			this.cargos = cargos;
			this.areas = areas;
		}

		@Override
		public List<Sede> sedes() {
			return sedes.findAll(Sort.by("idSede")).stream().map(CatalogoGabineteJpa::aSede).toList();
		}

		@Override
		public List<Cargo> cargos() {
			return cargos.findAll(Sort.by("idCargo")).stream().map(CatalogoGabineteJpa::aCargo).toList();
		}

		@Override
		public List<AreaPractica> areas() {
			return areas.findAll(Sort.by("nombre")).stream().map(CatalogoGabineteJpa::aArea).toList();
		}

		@Override
		public Optional<Sede> sede(short idSede) {
			return sedes.findById(idSede).map(CatalogoGabineteJpa::aSede);
		}

		@Override
		public Optional<Cargo> cargo(short idCargo) {
			return cargos.findById(idCargo).map(CatalogoGabineteJpa::aCargo);
		}

		@Override
		public Optional<AreaPractica> area(short idArea) {
			return areas.findById(idArea).map(CatalogoGabineteJpa::aArea);
		}

		static Sede aSede(SedeEntidad e) {
			return new Sede(e.idSede, e.nombre, e.ciudad, e.direccion, e.telefono, e.esPrincipal);
		}

		static Cargo aCargo(CargoEntidad e) {
			return new Cargo(e.idCargo, e.codigo, e.nombre, e.esAbogado);
		}

		static AreaPractica aArea(AreaEntidad e) {
			return new AreaPractica(e.idArea, e.codigoRama, e.nombre, e.idDirector);
		}
	}

}
