package co.abogados.procesos.infraestructura.eventos;

import java.util.Map;

import co.abogados.procesos.dominio.modelo.ActuacionDetectada;

/** Expone a las pruebas de otros paquetes la lectura del mensaje (ConsumidorActuaciones.leer es de paquete). */
public final class ConsumidorActuacionesPrueba {

	private ConsumidorActuacionesPrueba() {
	}

	public static ActuacionDetectada leer(Map<String, Object> mensaje) {
		return ConsumidorActuaciones.leer(mensaje);
	}

}
