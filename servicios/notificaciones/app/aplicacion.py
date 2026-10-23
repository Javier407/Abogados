"""Casos de uso: procesar un evento, consultar o marcar los avisos de la app y vincular Telegram."""
from __future__ import annotations

import logging
import secrets
from datetime import date, datetime, timedelta
from typing import Callable, Protocol
from zoneinfo import ZoneInfo

from .dominio import (EVENTO_ACTUACION, EVENTO_AUDIENCIA, EVENTO_RESUMEN, EVENTO_SEGURIDAD, EVENTOS_TERMINO, AvisoActuacion,
                      AvisoSeguridad, contenido_seguridad,
                      AvisoAudiencia, AvisoResumen, AvisoTermino, Canal, Contenido, CuentaCliente, EventoInvalido,
                      NotificacionNueva, Preferencia, canales, contenido_actuacion, contenido_audiencia,
                      contenido_audiencia_cliente, contenido_resumen_cliente, contenido_termino, correo_de,
                      texto_telegram)

log = logging.getLogger(__name__)
BOGOTA = ZoneInfo("America/Bogota")


# ------------------------------------------------------------------ puertos

class Repositorio(Protocol):
    async def preferencias(self, id_persona: int) -> list[Preferencia]: ...
    async def guardar(self, n: NotificacionNueva) -> int | None: ...          # None si la clave ya existía
    async def marcar(self, id_notificacion: int, estado: str) -> None: ...
    async def listar(self, id_persona: int, solo_no_leidas: bool, limite: int) -> list[dict]: ...
    async def contar_no_leidas(self, id_persona: int) -> int: ...
    async def marcar_leida(self, id_notificacion: int, id_persona: int) -> bool: ...
    async def marcar_todas_leidas(self, id_persona: int) -> int: ...
    async def crear_vinculo(self, codigo: str, id_persona: int) -> None: ...
    async def usar_vinculo(self, codigo: str) -> int | None: ...              # id_persona si el código es válido
    async def guardar_telegram(self, id_persona: int, chat_id: str) -> None: ...
    async def quitar_telegram(self, id_persona: int) -> bool: ...


class EnviadorCorreo(Protocol):
    async def enviar(self, destino: str, asunto: str, texto: str, html: str) -> None: ...


class EnviadorTelegram(Protocol):
    @property
    def activo(self) -> bool: ...
    async def enviar(self, chat_id: str, texto: str) -> None: ...


class Directorio(Protocol):
    async def correo_abogado(self, id_persona: int) -> str | None: ...


class Procesos(Protocol):
    async def responsable(self, id_proceso: int) -> int | None: ...
    async def cliente(self, id_proceso: int) -> int | None: ...


class CuentasCliente(Protocol):
    """Cuentas del portal de un cliente (servicio identidad)."""
    async def de_cliente(self, id_cliente: int) -> list[CuentaCliente]: ...


class AvisosEnVivo(Protocol):
    async def publicar(self, id_persona: int, dato: dict) -> None: ...


class Publicador(Protocol):
    async def notificacion_enviada(self, id_notificacion: int, n: NotificacionNueva) -> None: ...


class TelegramApagado:
    """Canal de Telegram sin token: no envía nada."""
    activo = False

    async def enviar(self, chat_id: str, texto: str) -> None:
        raise RuntimeError("Telegram no está configurado")


# ------------------------------------------------------------------ servicio

class ServicioNotificaciones:
    def __init__(self, repo: Repositorio, correo: EnviadorCorreo, directorio: Directorio,
                 en_vivo: AvisosEnVivo, publicador: Publicador, url_app: str = "",
                 hoy: Callable[[], date] = lambda: datetime.now(BOGOTA).date(),
                 telegram: EnviadorTelegram | None = None, procesos: Procesos | None = None,
                 cuentas: CuentasCliente | None = None):
        self.repo = repo
        self.correo = correo
        self.directorio = directorio
        self.en_vivo = en_vivo
        self.publicador = publicador
        self.url_app = url_app
        self.hoy = hoy
        self.telegram = telegram or TelegramApagado()
        self.procesos = procesos
        self.cuentas = cuentas

    async def procesar(self, evento: str, cuerpo: dict) -> int:
        """Guarda y envía los avisos de un evento. Devuelve cuántos avisos nuevos se crearon."""
        if evento in EVENTOS_TERMINO:
            aviso = AvisoTermino.desde_mensaje(evento, cuerpo)
            contenido = contenido_termino(aviso, self.hoy())
        elif evento == EVENTO_ACTUACION:
            actuacion = AvisoActuacion.desde_mensaje(cuerpo)
            responsable = await self._responsable(actuacion.id_proceso)
            if responsable is None:
                log.warning("El proceso %s no tiene responsable; la actuación %s no se avisa",
                            actuacion.id_proceso, actuacion.referencia)
                return 0
            contenido = contenido_actuacion(actuacion, responsable)
        elif evento == EVENTO_AUDIENCIA:
            audiencia = AvisoAudiencia.desde_mensaje(cuerpo)
            creadas = await self._avisar(contenido_audiencia(audiencia, self.hoy()))
            return creadas + await self._avisar_cliente_audiencia(audiencia)
        elif evento == EVENTO_RESUMEN:
            resumen = AvisoResumen.desde_mensaje(cuerpo)
            creadas = 0
            for cuenta in await self._cuentas(resumen.id_cliente):
                creadas += await self._avisar(contenido_resumen_cliente(resumen, cuenta), correo_cliente=cuenta.email)
            return creadas
        elif evento == EVENTO_SEGURIDAD:
            a = AvisoSeguridad.desde_mensaje(cuerpo)
            # El correo de la cuenta (identidad), por si la persona no configuró uno: es un aviso que debe llegar.
            return await self._avisar(contenido_seguridad(a), correo_cliente=a.email)
        else:
            raise EventoInvalido(f"Evento no soportado: {evento}")
        return await self._avisar(contenido)

    async def _cuentas(self, id_cliente: int) -> list[CuentaCliente]:
        """Cuentas del portal del cliente. Si identidad no responde, el evento se reintenta (no se pierde)."""
        if self.cuentas is None:
            return []
        return await self.cuentas.de_cliente(id_cliente)

    async def _avisar_cliente_audiencia(self, a: AvisoAudiencia) -> int:
        """El cliente también se entera de la audiencia (en palabras simples), si tiene cuenta en el portal."""
        if self.cuentas is None or self.procesos is None:
            return 0
        id_cliente = await self.procesos.cliente(a.id_proceso)
        if id_cliente is None:
            return 0
        creadas = 0
        for cuenta in await self._cuentas(id_cliente):
            creadas += await self._avisar(contenido_audiencia_cliente(a, cuenta, self.hoy()), correo_cliente=cuenta.email)
        return creadas

    async def _responsable(self, id_proceso: int) -> int | None:
        if self.procesos is None:
            raise RuntimeError("Falta el cliente del servicio procesos")
        return await self.procesos.responsable(id_proceso)   # si procesos no responde, el evento se reintenta

    async def _avisar(self, c: Contenido, correo_cliente: str | None = None) -> int:
        """correo_cliente: para los clientes, el correo de su cuenta (no tienen ficha de abogado en gabinete)."""
        preferencias = await self.repo.preferencias(c.id_persona)
        respaldo = None
        if not any(p.canal == Canal.EMAIL for p in preferencias):
            respaldo = correo_cliente if correo_cliente is not None else await self._correo_de_ficha(c.id_persona)

        creadas = 0
        for n in canales(c, preferencias, respaldo, self.telegram.activo):
            id_notificacion = await self.repo.guardar(n)
            if id_notificacion is None:
                log.info("Aviso repetido, se omite: %s", n.clave)
                continue
            creadas += 1
            if n.canal == Canal.APP:
                await self.en_vivo.publicar(n.id_persona, {
                    "idNotificacion": id_notificacion, "titulo": n.titulo, "mensaje": n.mensaje,
                    "evento": n.evento, "referencia": n.referencia,
                })
                await self.publicador.notificacion_enviada(id_notificacion, n)
            elif n.canal == Canal.EMAIL:
                asunto, texto, cuerpo_html = correo_de(n, self.url_app)
                await self._entregar(id_notificacion, n, self.correo.enviar(n.destino, asunto, texto, cuerpo_html))
            elif n.canal == Canal.TELEGRAM:
                await self._entregar(id_notificacion, n, self.telegram.enviar(n.destino, texto_telegram(n, self.url_app)))
        return creadas

    async def _entregar(self, id_notificacion: int, n: NotificacionNueva, envio) -> None:
        """Correo o Telegram: si falla queda FALLIDA (el aviso de la app ya está guardado)."""
        try:
            await envio
        except Exception:   # noqa: BLE001
            log.exception("No se pudo enviar el aviso %s por %s", id_notificacion, n.canal.value)
            await self.repo.marcar(id_notificacion, "FALLIDA")
            return
        await self.repo.marcar(id_notificacion, "ENVIADA")
        await self.publicador.notificacion_enviada(id_notificacion, n)

    async def _correo_de_ficha(self, id_persona: int) -> str | None:
        try:
            return await self.directorio.correo_abogado(id_persona)
        except Exception:   # noqa: BLE001 - sin gabinete se avisa igual dentro de la app
            log.warning("No se pudo consultar el correo del abogado %s en gabinete", id_persona)
            return None

    # -------------------------------------------------------------- avisos de la app

    async def listar(self, id_persona: int, solo_no_leidas: bool = False, limite: int = 50) -> list[dict]:
        return await self.repo.listar(id_persona, solo_no_leidas, max(1, min(limite, 200)))

    async def no_leidas(self, id_persona: int) -> int:
        return await self.repo.contar_no_leidas(id_persona)

    async def marcar_leida(self, id_notificacion: int, id_persona: int) -> bool:
        return await self.repo.marcar_leida(id_notificacion, id_persona)

    async def marcar_todas_leidas(self, id_persona: int) -> int:
        return await self.repo.marcar_todas_leidas(id_persona)

    # -------------------------------------------------------------- Telegram

    async def estado_telegram(self, id_persona: int) -> dict:
        vinculado = any(p.canal == Canal.TELEGRAM and p.activo and p.destino
                        for p in await self.repo.preferencias(id_persona))
        return {"configurado": self.telegram.activo, "vinculado": vinculado,
                "bot": getattr(self.telegram, "usuario", None)}

    async def generar_vinculo(self, id_persona: int) -> dict:
        """Código de un solo uso: la persona lo envía al bot con /start y queda vinculada."""
        if not self.telegram.activo:
            raise ValueError("El bot de Telegram no está configurado (falta TELEGRAM_TOKEN en el servidor)")
        codigo = secrets.token_urlsafe(9)
        await self.repo.crear_vinculo(codigo, id_persona)
        bot = getattr(self.telegram, "usuario", None)
        return {"codigo": codigo, "enlace": f"https://t.me/{bot}?start={codigo}" if bot else None, "bot": bot}

    async def vincular_telegram(self, codigo: str, chat_id: str) -> int | None:
        """Lo llama el bot al recibir /start <código>. Devuelve la persona vinculada o None si el código no sirve."""
        id_persona = await self.repo.usar_vinculo(codigo)
        if id_persona is not None:
            await self.repo.guardar_telegram(id_persona, chat_id)
            log.info("Telegram vinculado para la persona %s", id_persona)
        return id_persona

    async def desvincular_telegram(self, id_persona: int) -> bool:
        return await self.repo.quitar_telegram(id_persona)


# ------------------------------------------------------------------ estado del servidor (F12)

class RepositorioServidor(Protocol):
    """Lo que escribe el vigía (infra/monitoreo/vigia.sh) en el esquema operacion."""
    async def ultima_revision(self) -> dict | None: ...
    async def serie(self, desde: datetime, por_hora: bool) -> list[dict]: ...
    async def incidentes(self, desde: datetime) -> list[dict]: ...
    async def errores(self, desde: datetime) -> dict: ...   # F15: { porServicio: [...], recientes: [...] }


VIGIA_DORMIDO = timedelta(minutes=15)   # el vigía revisa cada 5 minutos: tres vueltas sin revisión = algo pasa


class ConsultaServidor:
    """
    Estado del servidor para la pantalla del administrador: la última revisión, la serie de disco y memoria
    (por hora si se piden más de 2 días), la disponibilidad de la app y los incidentes del periodo.
    """

    def __init__(self, repo: RepositorioServidor, ahora: Callable[[], datetime] = lambda: datetime.now(BOGOTA)):
        self.repo = repo
        self.ahora = ahora

    async def estado(self, dias: int) -> dict:
        ahora = self.ahora()
        desde = ahora - timedelta(days=dias)
        ultima = await self.repo.ultima_revision()
        serie = await self.repo.serie(desde, por_hora=dias > 2)
        total = sum(p["revisiones"] for p in serie)
        sin_app = sum(p["sinApp"] for p in serie)
        incidentes = []
        for i in await self.repo.incidentes(desde):
            fin = i["fin"]
            incidentes.append({"clave": i["clave"], "mensaje": i["mensaje"], "inicio": i["inicio"].isoformat(),
                               "fin": fin.isoformat() if fin else None, "abierto": fin is None,
                               "minutos": round(((fin or ahora) - i["inicio"]).total_seconds() / 60)})
        return {
            "dias": dias,
            "ultima": None if ultima is None else {**ultima, "momento": ultima["momento"].isoformat()},
            "vigiaActivo": ultima is not None and ahora - ultima["momento"] <= VIGIA_DORMIDO,
            "revisiones": total,
            # % de revisiones en que la app respondió por HTTPS (None si no hay revisiones en el periodo)
            "disponibilidad": round(100 * (total - sin_app) / total, 2) if total else None,
            "serie": [{k: (v.isoformat() if k == "momento" else v) for k, v in p.items()} for p in serie],
            "incidentes": incidentes,
            "errores": await self._errores(desde),
        }

    async def _errores(self, desde: datetime) -> dict:
        """Errores de los registros de los servicios (F15). Si la tabla aún no existe, sin errores."""
        try:
            e = await self.repo.errores(desde)
        except Exception:   # noqa: BLE001 - p. ej. sin la migración 012: la pantalla funciona igual
            log.warning("No se pudieron leer los errores de los servicios")
            return {"porServicio": [], "recientes": []}
        return {"porServicio": e["porServicio"],
                "recientes": [{**r, "momento": r["momento"].isoformat()} for r in e["recientes"]]}
