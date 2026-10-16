"""Casos de uso: procesar un evento y consultar o marcar los avisos de la app."""
from __future__ import annotations

import logging
from datetime import date, datetime
from typing import Callable, Protocol
from zoneinfo import ZoneInfo

from .dominio import AvisoTermino, Canal, NotificacionNueva, Preferencia, avisos_para, correo_de

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


class EnviadorCorreo(Protocol):
    async def enviar(self, destino: str, asunto: str, texto: str, html: str) -> None: ...


class Directorio(Protocol):
    async def correo_abogado(self, id_persona: int) -> str | None: ...


class AvisosEnVivo(Protocol):
    async def publicar(self, id_persona: int, dato: dict) -> None: ...


class Publicador(Protocol):
    async def notificacion_enviada(self, id_notificacion: int, n: NotificacionNueva) -> None: ...


# ------------------------------------------------------------------ servicio

class ServicioNotificaciones:
    def __init__(self, repo: Repositorio, correo: EnviadorCorreo, directorio: Directorio,
                 en_vivo: AvisosEnVivo, publicador: Publicador, url_app: str = "",
                 hoy: Callable[[], date] = lambda: datetime.now(BOGOTA).date()):
        self.repo = repo
        self.correo = correo
        self.directorio = directorio
        self.en_vivo = en_vivo
        self.publicador = publicador
        self.url_app = url_app
        self.hoy = hoy

    async def procesar(self, evento: str, cuerpo: dict) -> int:
        """Guarda y envía los avisos de un evento. Devuelve cuántos avisos nuevos se crearon."""
        aviso = AvisoTermino.desde_mensaje(evento, cuerpo)
        preferencias = await self.repo.preferencias(aviso.id_abogado)
        respaldo = None
        if not any(p.canal == Canal.EMAIL for p in preferencias):
            respaldo = await self._correo_de_ficha(aviso.id_abogado)

        creadas = 0
        for n in avisos_para(aviso, preferencias, respaldo, self.hoy()):
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
                await self._enviar_correo(id_notificacion, n)
        return creadas

    async def _enviar_correo(self, id_notificacion: int, n: NotificacionNueva) -> None:
        asunto, texto, cuerpo_html = correo_de(n, self.url_app)
        try:
            await self.correo.enviar(n.destino, asunto, texto, cuerpo_html)
        except Exception:   # noqa: BLE001 - cualquier fallo del SMTP queda registrado como FALLIDA
            log.exception("No se pudo enviar el correo %s a %s", id_notificacion, n.destino)
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
