package co.abogados.identidad;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import co.abogados.identidad.dominio.Refresco;
import co.abogados.identidad.dominio.puertos.SesionesRepositorio;

/** Doble en memoria de identidad.sesiones y identidad.tokens_revocados. */
class SesionesEnMemoria implements SesionesRepositorio {

	record Fila(Refresco refresco, String hash, UUID jti, Instant jtiExpira, String motivo) {
	}

	final Map<UUID, Fila> filas = new HashMap<>();
	final Map<String, Instant> revocados = new HashMap<>();

	public void guardar(Refresco r, String hash, UUID jti, Instant jtiExpira, String ip) {
		filas.put(r.id(), new Fila(r, hash, jti, jtiExpira, null));
	}

	public Optional<Refresco> porHash(String hash) {
		return filas.values().stream().filter(f -> f.hash().equals(hash)).map(Fila::refresco).findFirst();
	}

	public boolean marcarUsado(UUID id, Instant ahora) {
		Fila f = filas.get(id);
		if (f.refresco().usado() || f.refresco().revocado()) {
			return false;
		}
		Refresco r = f.refresco();
		filas.put(id, new Fila(new Refresco(r.id(), r.familia(), r.idUsuario(), r.expira(), r.familiaExpira(), ahora,
				null), f.hash(), f.jti(), f.jtiExpira(), null));
		return true;
	}

	public int revocarFamilia(UUID familia, String motivo, Instant ahora) {
		return revocar(filas.values().stream().filter(f -> f.refresco().familia().equals(familia)).toList(), motivo, ahora);
	}

	public int revocarUsuario(long idUsuario, UUID salvo, String motivo, Instant ahora) {
		return revocar(filas.values().stream()
				.filter(f -> f.refresco().idUsuario() == idUsuario && !f.refresco().familia().equals(salvo)).toList(),
				motivo, ahora);
	}

	private int revocar(List<Fila> elegidas, String motivo, Instant ahora) {
		int n = 0;
		for (Fila f : new ArrayList<>(elegidas)) {
			if (f.refresco().revocado()) {
				continue;
			}
			Refresco r = f.refresco();
			filas.put(r.id(), new Fila(new Refresco(r.id(), r.familia(), r.idUsuario(), r.expira(), r.familiaExpira(),
					r.usadoEn(), ahora), f.hash(), f.jti(), f.jtiExpira(), motivo));
			if (f.jtiExpira().isAfter(ahora) && revocados.putIfAbsent(f.jti().toString(), f.jtiExpira()) == null) {
				n++;
			}
		}
		return n;
	}

	public List<String> revocados(Instant ahora) {
		return revocados.entrySet().stream().filter(e -> e.getValue().isAfter(ahora)).map(Map.Entry::getKey).toList();
	}

	public void limpiar(Instant ahora) {
		revocados.values().removeIf(v -> !v.isAfter(ahora));
	}

}
