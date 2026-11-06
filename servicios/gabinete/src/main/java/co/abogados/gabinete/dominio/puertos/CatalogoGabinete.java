package co.abogados.gabinete.dominio.puertos;

import java.util.List;
import java.util.Optional;

import co.abogados.gabinete.dominio.modelo.AreaPractica;
import co.abogados.gabinete.dominio.modelo.Cargo;
import co.abogados.gabinete.dominio.modelo.Sede;

public interface CatalogoGabinete {

	List<Sede> sedes();

	List<Cargo> cargos();

	List<AreaPractica> areas();

	Optional<Sede> sede(short idSede);

	Optional<Cargo> cargo(short idCargo);

	Optional<AreaPractica> area(short idArea);

}
