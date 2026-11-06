package co.abogados.procesos.infraestructura.eventos;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.abogados.procesos.dominio.modelo.AvisoAudiencia;
import co.abogados.procesos.dominio.modelo.AvisoTermino;
import co.abogados.procesos.dominio.modelo.Portal.ResumenAvisado;
import co.abogados.procesos.dominio.modelo.ProcesoCreado;
import co.abogados.procesos.dominio.puertos.PublicadorAgenda;
import co.abogados.procesos.dominio.puertos.PublicadorEventos;
import co.abogados.procesos.dominio.puertos.PublicadorPortal;

/**
 * Publica los eventos del servicio escribiéndolos en el outbox, dentro de la transacción del caso de uso: si la
 * operación se deshace, el evento también. RelayOutbox los envía después al exchange gabinete.eventos.
 * Formato de cada mensaje: docs/contratos/eventos.md.
 */
@Component
public class PublicadorEventosOutbox implements PublicadorEventos, PublicadorAgenda, PublicadorPortal {

	private final Outbox outbox;
	private final ObjectMapper json;

	public PublicadorEventosOutbox(Outbox outbox, ObjectMapper json) {
		this.outbox = outbox;
		this.json = json;
	}

	@Override
	public void procesoCreado(ProcesoCreado e) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("id_proceso", e.idProceso());
		cuerpo.put("radicado", e.radicado());
		cuerpo.put("id_abogado_responsable", e.idAbogadoResponsable());
		cuerpo.put("ocurrido", e.ocurrido().toString());
		agregar("proceso.creado", cuerpo);
	}

	@Override
	public void avisoTermino(AvisoTermino a) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("id_termino", a.idTermino());
		cuerpo.put("id_proceso", a.idProceso());
		cuerpo.put("radicado", a.radicado());
		cuerpo.put("descripcion", a.descripcion());
		cuerpo.put("vence", a.vence().toString());
		cuerpo.put("dias_habiles_restantes", a.diasHabilesRestantes());
		cuerpo.put("id_abogado", a.idAbogado());
		cuerpo.put("ocurrido", a.ocurrido().toString());
		agregar(a.tipo().clave(), cuerpo);
	}

	@Override
	public void avisoAudiencia(AvisoAudiencia a) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("id_actuacion", a.idActuacion());
		cuerpo.put("id_proceso", a.idProceso());
		cuerpo.put("radicado", a.radicado());
		cuerpo.put("descripcion", a.descripcion());
		cuerpo.put("fecha", a.fecha().toString());
		cuerpo.put("hora", a.hora() == null ? null : a.hora().toString());
		cuerpo.put("lugar", a.lugar());
		cuerpo.put("id_abogado", a.idAbogado());
		cuerpo.put("ocurrido", a.ocurrido().toString());
		agregar("audiencia.proxima", cuerpo);
	}

	@Override
	public void resumenPublicado(ResumenAvisado r) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("id_resumen", r.idResumen());
		cuerpo.put("id_proceso", r.idProceso());
		cuerpo.put("radicado", r.radicado());
		cuerpo.put("id_cliente", r.idCliente());
		cuerpo.put("ocurrido", r.ocurrido().toString());
		agregar("portal.resumen_publicado", cuerpo);
	}

	private void agregar(String clave, Map<String, Object> cuerpo) {
		try {
			outbox.agregar(clave, json.writeValueAsString(cuerpo));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("No se pudo serializar el evento " + clave, e);
		}
	}

}
