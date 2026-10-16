"""Adaptadores: PostgreSQL, SMTP, servicio gabinete, RabbitMQ y avisos en vivo (SSE)."""
from __future__ import annotations

import asyncio
import json
import logging
from collections import defaultdict
from contextlib import asynccontextmanager
from email.message import EmailMessage
from typing import AsyncIterator

import aio_pika
import aiosmtplib
import httpx
from psycopg.types.json import Jsonb
from psycopg_pool import AsyncConnectionPool

from .config import Config
from .dominio import Canal, EventoInvalido, NotificacionNueva, Preferencia

log = logging.getLogger(__name__)


# ------------------------------------------------------------------ PostgreSQL

class RepositorioPostgres:
    def __init__(self, pool: AsyncConnectionPool):
        self.pool = pool

    async def preferencias(self, id_persona: int) -> list[Preferencia]:
        async with self.pool.connection() as con:
            cur = await con.execute(
                "SELECT canal, destino, activo FROM notificaciones.preferencias WHERE id_persona = %s",
                (id_persona,))
            return [Preferencia(Canal(c), d, a) for c, d, a in await cur.fetchall()]

    async def guardar(self, n: NotificacionNueva) -> int | None:
        async with self.pool.connection() as con:
            cur = await con.execute(
                """INSERT INTO notificaciones.notificaciones
                       (id_persona, canal, evento, titulo, mensaje, referencia, estado, clave)
                   VALUES (%s, %s, %s, %s, %s, %s, 'PENDIENTE', %s)
                   ON CONFLICT (clave) DO NOTHING
                   RETURNING id_notificacion""",
                (n.id_persona, n.canal.value, n.evento, n.titulo, n.mensaje, Jsonb(n.referencia), n.clave))
            fila = await cur.fetchone()
            return fila[0] if fila else None

    async def marcar(self, id_notificacion: int, estado: str) -> None:
        async with self.pool.connection() as con:
            await con.execute(
                """UPDATE notificaciones.notificaciones
                   SET estado = %s, enviada = CASE WHEN %s = 'ENVIADA' THEN now() ELSE enviada END
                   WHERE id_notificacion = %s""",
                (estado, estado, id_notificacion))

    async def listar(self, id_persona: int, solo_no_leidas: bool, limite: int) -> list[dict]:
        async with self.pool.connection() as con:
            cur = await con.execute(
                """SELECT id_notificacion, evento, titulo, mensaje, referencia, estado, creada
                   FROM notificaciones.notificaciones
                   WHERE id_persona = %s AND canal = 'APP' AND (NOT %s OR estado = 'PENDIENTE')
                   ORDER BY creada DESC, id_notificacion DESC
                   LIMIT %s""",
                (id_persona, solo_no_leidas, limite))
            return [
                {"idNotificacion": i, "evento": e, "titulo": t, "mensaje": m, "referencia": r or {},
                 "leida": est == "LEIDA", "creada": c.isoformat()}
                for i, e, t, m, r, est, c in await cur.fetchall()
            ]

    async def contar_no_leidas(self, id_persona: int) -> int:
        async with self.pool.connection() as con:
            cur = await con.execute(
                """SELECT count(*) FROM notificaciones.notificaciones
                   WHERE id_persona = %s AND canal = 'APP' AND estado = 'PENDIENTE'""", (id_persona,))
            return (await cur.fetchone())[0]

    async def marcar_leida(self, id_notificacion: int, id_persona: int) -> bool:
        async with self.pool.connection() as con:
            cur = await con.execute(
                """UPDATE notificaciones.notificaciones SET estado = 'LEIDA'
                   WHERE id_notificacion = %s AND id_persona = %s AND canal = 'APP'
                   RETURNING id_notificacion""", (id_notificacion, id_persona))
            return await cur.fetchone() is not None

    async def marcar_todas_leidas(self, id_persona: int) -> int:
        async with self.pool.connection() as con:
            cur = await con.execute(
                """UPDATE notificaciones.notificaciones SET estado = 'LEIDA'
                   WHERE id_persona = %s AND canal = 'APP' AND estado = 'PENDIENTE'""", (id_persona,))
            return cur.rowcount


# ------------------------------------------------------------------ correo (SMTP)

class CorreoSmtp:
    """En pruebas el SMTP es Mailpit, que guarda los correos sin entregarlos a nadie."""

    def __init__(self, cfg: Config):
        self.cfg = cfg

    async def enviar(self, destino: str, asunto: str, texto: str, html: str) -> None:
        m = EmailMessage()
        m["From"] = self.cfg.correo_remitente
        m["To"] = destino
        m["Subject"] = asunto
        m.set_content(texto)
        m.add_alternative(html, subtype="html")
        await aiosmtplib.send(
            m, hostname=self.cfg.smtp_host, port=self.cfg.smtp_puerto,
            username=self.cfg.smtp_usuario or None, password=self.cfg.smtp_clave or None,
            start_tls=self.cfg.smtp_starttls, timeout=15)


# ------------------------------------------------------------------ servicio gabinete

class DirectorioGabinete:
    def __init__(self, url: str):
        self.url = url.rstrip("/")

    async def correo_abogado(self, id_persona: int) -> str | None:
        async with httpx.AsyncClient(timeout=5) as http:
            r = await http.get(f"{self.url}/abogados/{id_persona}")
            if r.status_code == 404:
                return None
            r.raise_for_status()
            return r.json().get("email")


# ------------------------------------------------------------------ avisos en vivo (SSE)

class AvisosEnVivoMemoria:
    """Reparte los avisos nuevos a las pestañas abiertas de cada persona (una sola instancia del servicio)."""

    def __init__(self) -> None:
        self._colas: dict[int, set[asyncio.Queue]] = defaultdict(set)

    async def publicar(self, id_persona: int, dato: dict) -> None:
        for cola in list(self._colas.get(id_persona, ())):
            cola.put_nowait(dato)

    @asynccontextmanager
    async def suscribir(self, id_persona: int) -> AsyncIterator[asyncio.Queue]:
        cola: asyncio.Queue = asyncio.Queue(maxsize=100)
        self._colas[id_persona].add(cola)
        try:
            yield cola
        finally:
            self._colas[id_persona].discard(cola)
            if not self._colas[id_persona]:
                del self._colas[id_persona]


# ------------------------------------------------------------------ RabbitMQ

class Mensajeria:
    """
    Consume termino.* de la cola durable notificaciones.avisos y publica notificacion.enviada.
    Si RabbitMQ no está listo al arrancar, reintenta en segundo plano sin tumbar la API.
    """

    COLA = "notificaciones.avisos"
    CLAVES = ("termino.por_vencer", "termino.vencido")

    def __init__(self, cfg: Config):
        self.cfg = cfg
        self.conexion: aio_pika.abc.AbstractRobustConnection | None = None
        self.exchange: aio_pika.abc.AbstractExchange | None = None

    async def iniciar(self, procesar) -> None:
        while True:
            try:
                self.conexion = await aio_pika.connect_robust(self.cfg.rabbitmq_url)
                break
            except Exception as e:   # noqa: BLE001
                log.warning("RabbitMQ no disponible (%s); reintento en 5 s", e)
                await asyncio.sleep(5)
        canal = await self.conexion.channel()
        await canal.set_qos(prefetch_count=10)
        self.exchange = await canal.declare_exchange(self.cfg.exchange, aio_pika.ExchangeType.TOPIC, durable=True)
        cola = await canal.declare_queue(self.COLA, durable=True)
        for clave in self.CLAVES:
            await cola.bind(self.exchange, routing_key=clave)

        async def al_recibir(msg: aio_pika.abc.AbstractIncomingMessage) -> None:
            try:
                cuerpo = json.loads(msg.body)
                creadas = await procesar(msg.routing_key, cuerpo)
                log.info("Evento %s procesado: %s aviso(s) nuevo(s)", msg.routing_key, creadas)
                await msg.ack()
            except (EventoInvalido, json.JSONDecodeError) as e:
                log.error("Evento descartado: %s", e)
                await msg.ack()
            except Exception:   # noqa: BLE001 - p. ej. base de datos caída: se reintenta más tarde
                log.exception("Error procesando %s; se reintenta en 5 s", msg.routing_key)
                await asyncio.sleep(5)
                await msg.nack(requeue=True)

        await cola.consume(al_recibir)
        log.info("Escuchando %s en la cola %s", ", ".join(self.CLAVES), self.COLA)

    async def notificacion_enviada(self, id_notificacion: int, n: NotificacionNueva) -> None:
        if self.exchange is None:
            return
        cuerpo = {"id_notificacion": id_notificacion, "canal": n.canal.value, "destinatario": n.id_persona,
                  "evento": n.evento}
        try:
            await self.exchange.publish(
                aio_pika.Message(json.dumps(cuerpo).encode(), content_type="application/json"),
                routing_key="notificacion.enviada")
        except Exception as e:   # noqa: BLE001 - la auditoría no debe frenar el aviso
            log.warning("No se pudo publicar notificacion.enviada: %s", e)

    async def cerrar(self) -> None:
        if self.conexion is not None:
            await self.conexion.close()
