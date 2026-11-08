package co.abogados.gateway.auditoria;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guarda los accesos en lotes desde un hilo propio, para que ninguna petición espere a la base de datos.
 * - Un solo hilo escribe: el orden de llegada es el orden de la cadena.
 * - Si guardar falla (base caída), reintenta el mismo lote cada vez más espaciado (hasta 30 s) sin perderlo.
 * - Si la espera en memoria se llena, los accesos nuevos se cuentan como perdidos y se avisa en el log (siguen en la
 *   línea AUDITORIA del log del contenedor).
 * - Al apagar, intenta guardar lo pendiente durante unos segundos.
 */
final class EscritorEnLotes implements AutoCloseable {

	/** Donde se guardan los lotes. Debe guardar todo el lote o nada (una transacción). */
	@FunctionalInterface
	interface Almacen {
		void guardar(List<Acceso> lote) throws Exception;
	}

	private static final Logger log = LoggerFactory.getLogger(EscritorEnLotes.class);

	private final Almacen almacen;
	private final BlockingQueue<Acceso> cola;
	private final int tamanoLote;
	private final Duration esperaMinima;
	private final Duration esperaMaxima;
	private final AtomicLong perdidos = new AtomicLong();
	private final List<Acceso> lote = new ArrayList<>();
	private final Thread hilo;
	private volatile boolean activo = true;
	private volatile int enCurso;

	EscritorEnLotes(Almacen almacen, int capacidad, int tamanoLote, Duration esperaMinima, Duration esperaMaxima) {
		this.almacen = almacen;
		this.cola = new ArrayBlockingQueue<>(capacidad);
		this.tamanoLote = tamanoLote;
		this.esperaMinima = esperaMinima;
		this.esperaMaxima = esperaMaxima;
		this.hilo = new Thread(this::escribir, "auditoria-escritor");
		hilo.setDaemon(true);
		hilo.start();
	}

	void anotar(Acceso acceso) {
		if (!activo || !cola.offer(acceso)) {
			long n = perdidos.incrementAndGet();
			if (n == 1 || n % 1000 == 0) {
				log.warn("AUDITORIA_PERDIDA {} acceso(s) sin guardar en la bitácora: la espera está llena", n);
			}
		}
	}

	int pendientes() {
		return cola.size() + enCurso;
	}

	long perdidos() {
		return perdidos.get();
	}

	private void escribir() {
		Duration espera = esperaMinima;
		boolean fallando = false;
		while (activo || !cola.isEmpty() || !lote.isEmpty()) {
			try {
				if (lote.isEmpty()) {
					Acceso primero = cola.poll(500, TimeUnit.MILLISECONDS);
					if (primero == null) {
						continue;
					}
					lote.add(primero);
					cola.drainTo(lote, tamanoLote - 1);
					enCurso = lote.size();
				}
				almacen.guardar(List.copyOf(lote));
				lote.clear();
				enCurso = 0;
				if (fallando) {
					log.info("AUDITORIA la bitácora vuelve a guardarse en la base");
					fallando = false;
				}
				espera = esperaMinima;
			} catch (InterruptedException e) {
				if (!activo) {
					return;           // apagado: lo que quedó sin guardar sigue en el log AUDITORIA
				}
			} catch (Exception e) {
				if (!fallando) {
					log.warn("AUDITORIA no se pudo guardar en la base ({}); se reintenta sin perder lo pendiente",
							e.getMessage());
					fallando = true;
				}
				if (!activo) {
					return;           // apagando y la base no responde: no se insiste
				}
				dormir(espera);
				espera = espera.multipliedBy(2).compareTo(esperaMaxima) > 0 ? esperaMaxima : espera.multipliedBy(2);
			}
		}
	}

	private void dormir(Duration d) {
		try {
			Thread.sleep(d.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Deja de recibir y espera hasta {@code plazo} a que se guarde lo pendiente. */
	void cerrar(Duration plazo) {
		activo = false;
		try {
			hilo.join(plazo.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (hilo.isAlive()) {
			hilo.interrupt();
		}
		if (pendientes() > 0) {
			log.warn("AUDITORIA {} acceso(s) quedaron sin guardar al apagar (están en el log)", pendientes());
		}
	}

	@Override
	public void close() {
		cerrar(Duration.ofSeconds(5));
	}

}
