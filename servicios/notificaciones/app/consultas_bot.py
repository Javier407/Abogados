"""Consultas a Toga, por Telegram o desde el portal: responde sobre los procesos de quien le escribe.

Reglas (docs/15-bot-telegram.md):
- Solo en chat privado y solo a un chat vinculado a una persona de la firma que siga activa. Un cliente, aunque
  tenga Telegram vinculado para avisos, no recibe datos por aquí.
- Solo lectura y solo lo suyo: los procesos en los que la persona está (a cargo, apoyo o dependiente), sus
  términos y sus audiencias. Un radicado que no es suyo responde igual que uno que no existe.
- Lista blanca de campos: radicado, asunto, tipo, etapa, términos (qué, cuándo vence) y audiencias. Nada de
  cuantías, partes, documentos ni errores internos.
- Los datos exactos los pone el sistema; no hay un modelo de lenguaje que redacte la respuesta.
- Límite de consultas por persona y minuto. Cada consulta queda registrada sin el texto que se escribió.
"""
from __future__ import annotations

import logging
import re
import time
import unicodedata
from collections import defaultdict, deque
from datetime import date, timedelta
from typing import Callable, Protocol

from .dominio import radicado_legible

log = logging.getLogger(__name__)

NOMBRE = "Toga"
LIMITE_POR_MINUTO = 10
MAXIMO_LISTA = 8
CERRADOS = ("TERMINADO", "ARCHIVADO")


class RepositorioBot(Protocol):
    async def persona_de_chat(self, chat_id: str) -> int | None: ...
    async def registrar_consulta_bot(self, id_persona: int | None, chat_id: str, comando: str, resultado: str) -> None: ...


class DatosBot(Protocol):
    """Lo que el bot puede leer de los otros servicios (con el token de servicio, que solo permite GET)."""
    async def persona(self, id_persona: int) -> dict | None: ...           # gabinete: {nombres, activo} o None
    async def procesos_de(self, id_persona: int) -> list[dict]: ...         # procesos: /procesos/de-abogado/{id}
    async def terminos_de(self, id_persona: int) -> list[dict]: ...         # procesos: /terminos?idResponsable=
    async def terminos_del_proceso(self, id_proceso: int) -> list[dict]: ...
    async def audiencias_de(self, id_persona: int, desde: date, hasta: date) -> list[dict]: ...


def _normalizar(texto: str) -> str:
    sin_tildes = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    return sin_tildes.lower().strip()


def _fecha(iso: str | None) -> str:
    if not iso:
        return "sin fecha"
    d = date.fromisoformat(iso[:10])
    return d.strftime("%d/%m/%Y")


def _faltan(dias: int | None) -> str:
    if dias is None:
        return ""
    if dias < 0:
        return f"venció hace {-dias} día{'s' if dias != -1 else ''} hábil{'es' if dias != -1 else ''}"
    if dias == 0:
        return "vence hoy"
    return f"falta{'n' if dias != 1 else ''} {dias} día{'s' if dias != 1 else ''} hábil{'es' if dias != 1 else ''}"


# Palabras que en los valores del sistema van sin tilde (como en el portal).
_TILDES = {"admision": "admisión", "notificacion": "notificación", "contestacion": "contestación",
           "investigacion": "investigación", "sentencia": "sentencia"}


def _etiqueta(valor: str | None) -> str:
    return " ".join(_TILDES.get(p, p) for p in (valor or "").lower().split("_") if p)


class ConsultasBot:
    def __init__(self, repo: RepositorioBot, datos: DatosBot, hoy: Callable[[], date] = date.today,
                 reloj: Callable[[], float] = time.monotonic, limite_por_minuto: int = LIMITE_POR_MINUTO):
        self.repo = repo
        self.datos = datos
        self.hoy = hoy
        self.reloj = reloj
        self.limite = limite_por_minuto
        self._recientes: dict[int, deque[float]] = defaultdict(deque)

    # -------------------------------------------------------------- entrada

    async def responder(self, chat_id: str, texto: str) -> str:
        """Responde un mensaje de un chat privado. Nunca lanza: si algo falla, lo dice sin detalles internos."""
        comando, _ = self.interpretar(texto)
        id_persona = await self.repo.persona_de_chat(chat_id)
        if id_persona is None:
            await self._registrar(None, chat_id, comando, "SIN_VINCULO")
            return ("Este chat no está vinculado a Gestión Jurídica. Si trabaja en la firma, entre al portal → "
                    "Avisos → Vincular Telegram y use el enlace que aparece.")
        return await self._atender(id_persona, chat_id, texto, "telegram")

    async def responder_portal(self, id_persona: int, texto: str) -> str:
        """Pregunta hecha desde el portal: la persona viene de la sesión (token), no de un chat."""
        return await self._atender(id_persona, "portal", texto, "portal")

    async def responder_externo(self, id_persona: int, chat_id: str, texto: str) -> str:
        """WhatsApp, Instagram… (vía Talki): la persona ya viene del vínculo del chat. Mismas reglas."""
        return await self._atender(id_persona, chat_id, texto, "externo")

    async def _atender(self, id_persona: int, chat_id: str, texto: str, canal: str) -> str:
        comando, argumento = self.interpretar(texto)
        try:
            persona = await self.datos.persona(id_persona)
        except Exception:   # noqa: BLE001 - si gabinete no responde, no se arriesga a responder datos
            log.exception("No se pudo comprobar a la persona %s para el bot", id_persona)
            await self._registrar(id_persona, chat_id, comando, "ERROR")
            return "No pude comprobar su cuenta en este momento. Intente de nuevo en unos minutos."
        if not persona or not persona.get("activo"):
            await self._registrar(id_persona, chat_id, comando, "NO_AUTORIZADO")
            return ("Las consultas por aquí son solo para el equipo de la firma. Sus avisos le seguirán llegando; "
                    "para ver su proceso, entre al portal.")
        if not self._dentro_del_limite(id_persona):
            await self._registrar(id_persona, chat_id, comando, "LIMITE")
            return "Recibí muchas consultas seguidas. Espere un minuto y vuelva a preguntar."
        try:
            respuesta, resultado = await self._despachar(comando, argumento, id_persona, persona, canal)
        except Exception:   # noqa: BLE001
            log.exception("Falló la consulta %s del bot", comando)
            respuesta, resultado = "No pude consultar eso ahora. Intente de nuevo en unos minutos.", "ERROR"
        await self._registrar(id_persona, chat_id, comando, resultado)
        return respuesta

    @staticmethod
    def interpretar(texto: str) -> tuple[str, str]:
        """Comando y argumento. Acepta los comandos y algunas frases comunes; un radicado suelto es /proceso."""
        t = (texto or "").strip()
        if t.startswith("/"):
            cabeza, _, resto = t.partition(" ")
            comando = cabeza[1:].split("@")[0].lower()
            alias = {"misprocesos": "mis_procesos", "procesos": "mis_procesos", "help": "ayuda", "start": "ayuda",
                     "plazos": "terminos"}
            return alias.get(comando, comando), resto.strip()
        n = _normalizar(t)
        digitos = re.sub(r"\D", "", t)
        if len(digitos) == 23:
            return "proceso", digitos
        if re.search(r"\b(registrar|registro|crear|cambiar|modificar|editar|borrar|eliminar|subir|radicar)\b", n):
            return "solo_lectura", ""
        if re.search(r"\b(bot|robot|quien eres|eres|humano|persona real)\b", n):
            return "quien", ""
        if re.search(r"\b(termino|terminos|vence|vencen|vencimiento|plazo|plazos)\b", n):
            return "terminos", ""
        if re.search(r"\b(audiencia|audiencias|diligencia)\b", n):
            return "audiencias", ""
        if re.search(r"\b(proceso|procesos|casos|caso|expedientes)\b", n):
            return "mis_procesos", ""
        return "ayuda", ""

    # -------------------------------------------------------------- comandos

    async def _despachar(self, comando: str, argumento: str, id_persona: int, persona: dict,
                         canal: str = "telegram") -> tuple[str, str]:
        if comando == "mis_procesos":
            return await self._mis_procesos(id_persona), "RESPONDIDA"
        if comando == "terminos":
            return await self._terminos(id_persona), "RESPONDIDA"
        if comando == "audiencias":
            return await self._audiencias(id_persona), "RESPONDIDA"
        if comando == "proceso":
            return await self._proceso(id_persona, argumento)
        if comando == "solo_lectura":
            return ("No: solo consulto. Para registrar, cambiar o borrar algo, use el portal. Puedo decirle sus "
                    "procesos, términos y audiencias (escriba /ayuda)."
                    if canal == "telegram" else
                    "No: solo consulto. Para registrar, cambiar o borrar algo, use el portal. Puedo decirle sus "
                    "procesos, términos y audiencias."), "RESPONDIDA"
        if comando == "quien":
            return (f"Soy {NOMBRE}, la asistente de la firma: consulto en Lexora sus procesos, términos y audiencias. "
                    "No soy una persona y no doy asesoría jurídica."
                    + (" Escriba /ayuda para ver qué puedo responder." if canal == "telegram" else "")), "RESPONDIDA"
        return self._ayuda(persona.get("nombres") or "", canal, await self._radicado_de_ejemplo(id_persona)), \
            "RESPONDIDA"

    async def _radicado_de_ejemplo(self, id_persona: int) -> str | None:
        """Un radicado de un proceso abierto de la persona, para que el ejemplo de la ayuda sí funcione."""
        try:
            for p in await self.datos.procesos_de(id_persona):
                if p.get("estado") not in CERRADOS and p.get("radicadoLegible"):
                    return re.sub(r"\D", "", p["radicadoLegible"])
        except Exception:   # noqa: BLE001 - la ayuda sale igual, sin ejemplo
            log.warning("No se pudo leer un radicado de ejemplo para la ayuda")
        return None

    def _ayuda(self, nombre: str, canal: str = "telegram", ejemplo: str | None = None) -> str:
        saludo = f"Hola, {nombre.split()[0]}. " if nombre else "Hola. "
        if canal == "portal":
            return (f"{saludo}Soy {NOMBRE}, la asistente de la firma. Pregúnteme por sus procesos abiertos, los "
                    "términos que le vencen o sus audiencias de las próximas dos semanas, o escríbame el radicado de "
                    "un proceso suyo para decirle cómo va. Solo consulto: para registrar o cambiar algo, use las "
                    "pantallas del portal.")
        if canal == "externo":
            return (f"{saludo}Soy {NOMBRE}, la asistente de la firma. Escríbame «mis procesos», «términos», "
                    "«audiencias» o el radicado de 23 dígitos de un proceso suyo. Solo consulto: para registrar o "
                    "cambiar algo, use el portal.")
        return (f"{saludo}Soy {NOMBRE}, la asistente de la firma. Le respondo sobre sus procesos:\n\n"
                "/mis_procesos — sus procesos abiertos y lo próximo que vence\n"
                "/terminos — sus términos pendientes, del más urgente al más lejano\n"
                "/audiencias — sus audiencias de las próximas dos semanas\n"
                + (f"/proceso {ejemplo} — cómo va ese proceso suyo (o cualquier otro radicado suyo)\n\n"
                   if ejemplo else "/proceso <radicado de 23 dígitos> — cómo va un proceso suyo\n\n") +
                "También entiendo frases como «¿qué términos me vencen?». Solo consulto: para registrar o cambiar "
                "algo, use el portal.")

    async def _mis_procesos(self, id_persona: int) -> str:
        abiertos = [p for p in await self.datos.procesos_de(id_persona) if p.get("estado") not in CERRADOS]
        if not abiertos:
            return "No tiene procesos abiertos."
        vencidos: dict[str, list[str]] = defaultdict(list)     # radicado -> fechas de sus términos vencidos
        try:
            for t in await self.datos.terminos_de(id_persona):
                if t.get("estado") == "PENDIENTE" and (t.get("diasHabilesRestantes") or 0) < 0:
                    vencidos[t.get("radicadoLegible") or ""].append(t.get("fechaVencimiento") or "")
        except Exception:   # noqa: BLE001 - sin el detalle, se usa la fecha del próximo vencimiento
            log.warning("No se pudieron contar los términos vencidos para /mis_procesos")
        lineas = [f"Tiene {len(abiertos)} proceso{'s' if len(abiertos) != 1 else ''} abierto"
                  f"{'s' if len(abiertos) != 1 else ''}:"]
        for p in abiertos[:MAXIMO_LISTA]:
            papel = "a cargo" if p.get("rol") == "RESPONSABLE" else "apoyo"
            linea = (f"\n• {p.get('radicadoLegible')} — {p.get('asunto')}\n"
                     f"  {p.get('tipoProceso')}, etapa {_etiqueta(p.get('etapa'))} ({papel})")
            if p.get("terminosPendientes"):
                linea += "\n  " + self._resumen_terminos(p, vencidos.get(p.get("radicadoLegible") or "", []))
            lineas.append(linea)
        if len(abiertos) > MAXIMO_LISTA:
            lineas.append(f"\n…y {len(abiertos) - MAXIMO_LISTA} más en el portal.")
        return "".join(lineas)

    def _resumen_terminos(self, p: dict, fechas_vencidos: list[str]) -> str:
        """«2 término(s), todos VENCIDOS; el más antiguo desde el 16/10/2026» o «…; el próximo vence el …»."""
        total = p["terminosPendientes"]
        proximo = p.get("proximoVencimiento")
        fechas = sorted(f for f in fechas_vencidos if f)
        if not fechas and proximo and date.fromisoformat(proximo[:10]) < self.hoy():
            fechas = [proximo]    # la fecha más cercana ya pasó, aunque ese término no esté a su nombre
        if not fechas:
            return f"{total} término(s); el próximo vence el {_fecha(proximo)}"
        vencidos = min(len(fechas), total)
        if vencidos >= total:
            cuantos = "VENCIDO desde el" if total == 1 else "todos VENCIDOS; el más antiguo desde el"
        else:
            cuantos = f"{vencidos} VENCIDO(S); el más antiguo desde el"
        return f"{total} término(s), {cuantos} {_fecha(fechas[0])}"

    async def _terminos(self, id_persona: int) -> str:
        pendientes = sorted((t for t in await self.datos.terminos_de(id_persona) if t.get("estado") == "PENDIENTE"),
                            key=lambda t: t.get("fechaVencimiento") or "")
        if not pendientes:
            return "No tiene términos pendientes. Todo al día."
        vencidos = sum(1 for t in pendientes if (t.get("diasHabilesRestantes") or 0) < 0)
        cabeza = f"Tiene {len(pendientes)} término(s) pendiente(s)"
        cabeza += f", {vencidos} vencido(s). Empiece por esos:" if vencidos else ":"
        lineas = [cabeza]
        for t in pendientes[:MAXIMO_LISTA]:
            lineas.append(f"\n• {_fecha(t.get('fechaVencimiento'))} — {t.get('descripcion')}\n"
                          f"  {t.get('radicadoLegible')} · {_faltan(t.get('diasHabilesRestantes'))}")
        if len(pendientes) > MAXIMO_LISTA:
            lineas.append(f"\n…y {len(pendientes) - MAXIMO_LISTA} más en el portal.")
        return "".join(lineas)

    async def _audiencias(self, id_persona: int) -> str:
        hoy = self.hoy()
        lista = await self.datos.audiencias_de(id_persona, hoy, hoy + timedelta(days=14))
        if not lista:
            return "No tiene audiencias en las próximas dos semanas."
        lineas = [f"Sus audiencias de las próximas dos semanas ({len(lista)}):"]
        for a in lista[:MAXIMO_LISTA]:
            hora = f" a las {str(a['hora'])[:5]}" if a.get("hora") else ""
            lugar = f" — {a['lugar']}" if a.get("lugar") else ""
            lineas.append(f"\n• {_fecha(a.get('fecha'))}{hora} — {a.get('descripcion')}\n"
                          f"  {a.get('radicadoLegible')}{lugar}")
        return "".join(lineas)

    async def _proceso(self, id_persona: int, argumento: str) -> tuple[str, str]:
        radicado = re.sub(r"\D", "", argumento or "")
        if len(radicado) != 23:
            return ("Escriba el radicado completo de 23 dígitos, con o sin guiones (como aparece en el "
                    "portal)."), "RESPONDIDA"
        legible = radicado_legible(radicado)
        # Solo los procesos de la persona: uno ajeno responde igual que uno inexistente.
        suyo = next((p for p in await self.datos.procesos_de(id_persona) if p.get("radicadoLegible") == legible), None)
        if suyo is None:
            return (f"No encuentro el proceso {legible} entre los suyos. Si es de otro abogado, consúltelo en el "
                    "portal."), "NO_ENCONTRADO"
        pendientes = sorted((t for t in await self.datos.terminos_del_proceso(suyo["idProceso"])
                             if t.get("estado") == "PENDIENTE"), key=lambda t: t.get("fechaVencimiento") or "")
        estado = suyo.get("estado")
        lineas = [f"{legible} — {suyo.get('asunto')}\n{suyo.get('tipoProceso')} ({suyo.get('rama')}), "
                  f"etapa {_etiqueta(suyo.get('etapa'))}"]
        if estado and estado != "ACTIVO":
            lineas.append(f", {_etiqueta(estado)}")
        lineas.append(".")
        if pendientes:
            lineas.append(f"\n\nTérminos pendientes ({len(pendientes)}):")
            for t in pendientes[:5]:
                lineas.append(f"\n• {_fecha(t.get('fechaVencimiento'))} — {t.get('descripcion')} "
                              f"({_faltan(t.get('diasHabilesRestantes'))})")
        else:
            lineas.append("\n\nNo tiene términos pendientes.")
        return "".join(lineas), "RESPONDIDA"

    # -------------------------------------------------------------- apoyo

    def _dentro_del_limite(self, id_persona: int) -> bool:
        ahora = self.reloj()
        ventana = self._recientes[id_persona]
        while ventana and ahora - ventana[0] > 60:
            ventana.popleft()
        if len(ventana) >= self.limite:
            return False
        ventana.append(ahora)
        return True

    async def _registrar(self, id_persona: int | None, chat_id: str, comando: str, resultado: str) -> None:
        try:
            await self.repo.registrar_consulta_bot(id_persona, chat_id, comando[:30], resultado)
        except Exception:   # noqa: BLE001 - el registro no debe impedir la respuesta
            log.exception("No se pudo registrar la consulta del bot")
