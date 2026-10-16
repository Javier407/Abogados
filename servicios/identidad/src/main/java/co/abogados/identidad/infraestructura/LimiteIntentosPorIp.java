package co.abogados.identidad.infraestructura;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limita los intentos de inicio de sesión por dirección IP (ventana deslizante en memoria).
 * Complementa el bloqueo por cuenta: frena a quien prueba muchas cuentas distintas desde la misma IP.
 * Una sola instancia del servicio: si algún día hay varias, esto debe ir a un almacén compartido.
 *
 * Ojo: en el servidor actual Tailscale enmascara (SNAT) el tráfico hacia Docker, así que todos los clientes
 * llegan con la misma IP y el límite funciona como freno GLOBAL de ráfagas. Por eso el máximo es alto
 * (login.maximo-por-ip, 100 por defecto): uno bajo dejaría a toda la firma sin poder entrar.
 */
@Component
public class LimiteIntentosPorIp {

	public static final int MAXIMO_POR_DEFECTO = 100;
	public static final Duration VENTANA = Duration.ofMinutes(10);

	private final Map<String, Deque<Instant>> intentos = new ConcurrentHashMap<>();
	private final Clock reloj;
	private final int maximo;

	public LimiteIntentosPorIp(Clock reloj,
			@Value("${login.maximo-por-ip:" + MAXIMO_POR_DEFECTO + "}") int maximo) {
		this.reloj = reloj;
		this.maximo = maximo;
	}

	public int maximo() {
		return maximo;
	}

	/** Registra el intento y dice si se permite (false = superó el límite en la ventana). */
	public boolean permitir(String ip) {
		String clave = ip == null || ip.isBlank() ? "desconocida" : ip;
		Instant ahora = Instant.now(reloj);
		Instant desde = ahora.minus(VENTANA);
		Deque<Instant> cola = intentos.computeIfAbsent(clave, k -> new ArrayDeque<>());
		synchronized (cola) {
			while (!cola.isEmpty() && cola.peekFirst().isBefore(desde)) {
				cola.pollFirst();
			}
			if (cola.size() >= maximo) {
				return false;
			}
			cola.addLast(ahora);
		}
		if (intentos.size() > 10_000) {
			// Evita que la tabla crezca sin fin: se descartan las IP sin intentos recientes.
			intentos.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().peekLast().isBefore(desde));
		}
		return true;
	}

}
