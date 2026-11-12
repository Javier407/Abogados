package co.abogados.gateway.auditoria;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bitácora en memoria, con la misma cadena de hashes, para las pruebas y para correr el gateway en el PC sin base
 * de datos. No sobrevive a un reinicio: en el servidor siempre se usa {@link BitacoraPostgres}.
 */
public final class BitacoraEnMemoria implements Bitacora {

	private final List<Registro> registros = new ArrayList<>();
	private final int maximo;
	private long perdidos;

	public BitacoraEnMemoria() {
		this(10_000);
	}

	public BitacoraEnMemoria(int maximo) {
		this.maximo = maximo;
	}

	@Override
	public synchronized void anotar(Acceso acceso) {
		if (registros.size() >= maximo) {
			perdidos++;
			return;
		}
		Registro ultimo = registros.isEmpty() ? null : registros.get(registros.size() - 1);
		registros.add(ultimo == null ? Cadena.encadenar(0, Cadena.GENESIS, acceso)
				: Cadena.encadenar(ultimo.id(), ultimo.hash(), acceso));
	}

	/** Solo para las pruebas: reemplaza un registro como lo haría alguien con acceso a la base. */
	synchronized void alterar(int posicion, Registro otro) {
		registros.set(posicion, otro);
	}

	synchronized List<Registro> todos() {
		return List.copyOf(registros);
	}

	@Override
	public synchronized List<Registro> buscar(Filtro f) {
		List<Registro> salida = new ArrayList<>();
		for (int i = registros.size() - 1; i >= 0 && salida.size() < f.limite(); i--) {
			if (f.incluye(registros.get(i))) {
				salida.add(registros.get(i));
			}
		}
		return salida;
	}

	@Override
	public synchronized Optional<Registro> registro(long id) {
		return registros.stream().filter(r -> r.id() == id).findFirst();
	}

	@Override
	public synchronized Cadena.Verificacion verificar(long selloId, String selloHash) {
		Cadena.Verificador v = selloId > 0 ? new Cadena.Verificador(selloId, selloHash) : new Cadena.Verificador();
		for (Registro r : registros) {
			if (r.id() >= v.leerDesde() && !v.revisar(r)) {
				break;
			}
		}
		return v.resultado();
	}

	@Override
	public synchronized Estado estado() {
		return new Estado("memoria", 0, perdidos);
	}

}
