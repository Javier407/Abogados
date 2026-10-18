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
from .dominio import CuentaCliente, Canal, EventoInvalido, NotificacionNueva, Preferencia

log = logging.getLogger(__name__)
# httpx registra cada URL a nivel INFO y la URL de la API de Telegram lleva el token: se silencia aquí,
# donde vive el bot, para que el token nunca llegue a los logs.
logging.getLogger("httpx").setLevel(logging.WARNING)


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

    # ------------------------------------------------ vínculos de Telegram

    async def crear_vinculo(self, codigo: str, id_persona: int) -> None:
        async with self.pool.connection() as con:
            await con.execute("INSERT INTO notificaciones.vinculos_telegram (codigo, id_persona) VALUES (%s, %s)",
                              (codigo, id_persona))

    async def usar_vinculo(self, codigo: str) -> int | None:
        """Un código sirve una vez y durante 24 horas."""
        async with self.pool.connection() as con:
            cur = await con.execute(
                """UPDATE notificaciones.vinculos_telegram SET usado = now()
                   WHERE codigo = %s AND usado IS NULL AND creado > now() - interval '24 hours'
                   RETURNING id_persona""", (codigo,))
            fila = await cur.fetchone()
            return fila[0] if fila else None

    async def guardar_telegram(self, id_persona: int, chat_id: str) -> None:
        async with self.pool.connection() as con:
            await con.execute(
                """INSERT INTO notificaciones.preferencias (id_persona, canal, destino, activo)
                   VALUES (%s, 'TELEGRAM', %s, true)
                   ON CONFLICT (id_persona, canal) DO UPDATE SET destino = EXCLUDED.destino, activo = true""",
                (id_persona, chat_id))

    async def quitar_telegram(self, id_persona: int) -> bool:
        async with self.pool.connection() as con:
            cur = await con.execute(
                "DELETE FROM notificaciones.preferencias WHERE id_persona = %s AND canal = 'TELEGRAM'", (id_persona,))
            return cur.rowcount > 0


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
    def __init__(self, url: str, token=None):
        self.url = url.rstrip("/")
        self.token = token   # TokenServicio: estas llamadas llevan el token del servicio (F9)

    async def correo_abogado(self, id_persona: int) -> str | None:
        async with httpx.AsyncClient(timeout=5, headers=await self.token.cabeceras() if self.token else {}) as http:
            r = await http.get(f"{self.url}/abogados/{id_persona}")
            if r.status_code == 404:
                return None
            r.raise_for_status()
            return r.json().get("email")


# ------------------------------------------------------------------ servicio procesos

class ClienteProcesos:
    def __init__(self, url: str, token=None):
        self.url = url.rstrip("/")
        self.token = token   # TokenServicio: estas llamadas llevan el token del servicio (F9)

    async def cliente(self, id_proceso: int) -> int | None:
        async with httpx.AsyncClient(timeout=5, headers=await self.token.cabeceras() if self.token else {}) as http:
            r = await http.get(f"{self.url}/procesos/{id_proceso}")
            if r.status_code == 404:
                return None
            r.raise_for_status()
            return r.json().get("idCliente")

    async def responsable(self, id_proceso: int) -> int | None:
        async with httpx.AsyncClient(timeout=5, headers=await self.token.cabeceras() if self.token else {}) as http:
            r = await http.get(f"{self.url}/procesos/{id_proceso}")
            if r.status_code == 404:
                return None
            r.raise_for_status()
            return r.json().get("idResponsable")


# ------------------------------------------------------------------ servicio identidad

class ClienteIdentidad:
    """Cuentas del portal de un cliente (ruta interna de identidad, con el token del servicio)."""

    def __init__(self, url: str, token=None):
        self.url = url.rstrip("/")
        self.token = token

    async def de_cliente(self, id_cliente: int) -> list[CuentaCliente]:
        async with httpx.AsyncClient(timeout=5, headers=await self.token.cabeceras() if self.token else {}) as http:
            r = await http.get(f"{self.url}/interno/clientes/{id_cliente}/cuentas")
            r.raise_for_status()
            return [CuentaCliente(int(c["idUsuario"]), c.get("nombre") or "", c.get("email")) for c in r.json()]


# ------------------------------------------------------------------ Telegram

class TelegramBot:
    """
    Envía mensajes con la API de bots de Telegram y escucha /start <código> por long polling
    (getUpdates): el servidor solo hace llamadas salientes, no se expone ningún puerto a internet.
    """

    API = "https://api.telegram.org"

    def __init__(self, token: str, cliente: httpx.AsyncClient | None = None):
        self._token = token
        self.cliente = cliente or httpx.AsyncClient(timeout=40)
        self.usuario: str | None = None

    @property
    def activo(self) -> bool:
        return bool(self._token) and self.usuario is not None

    def _url(self, metodo: str) -> str:
        return f"{self.API}/bot{self._token}/{metodo}"

    async def _llamar(self, metodo: str, **datos) -> dict:
        r = await self.cliente.post(self._url(metodo), json=datos)
        cuerpo = r.json()
        if not cuerpo.get("ok"):
            # El token nunca se escribe en el log: solo el método y la descripción del error.
            raise RuntimeError(f"Telegram {metodo}: {cuerpo.get('description', r.status_code)}")
        return cuerpo["result"]

    async def iniciar(self) -> None:
        """Verifica el token (getMe). Si falla, el canal queda apagado y se reintenta más tarde."""
        try:
            yo = await self._llamar("getMe")
            self.usuario = yo.get("username")
            log.info("Bot de Telegram listo: @%s", self.usuario)
        except Exception as e:   # noqa: BLE001
            self.usuario = None
            log.error("No se pudo iniciar el bot de Telegram: %s", type(e).__name__)

    async def enviar(self, chat_id: str, texto: str) -> None:
        await self._llamar("sendMessage", chat_id=chat_id, text=texto[:4000], disable_web_page_preview=True)

    async def escuchar(self, vincular) -> None:
        """Bucle de getUpdates. vincular(codigo, chat_id) -> id_persona | None."""
        desplazamiento = 0
        while True:
            if not self.activo:
                await self.iniciar()
                if not self.activo:
                    await asyncio.sleep(60)
                    continue
            try:
                novedades = await self._llamar("getUpdates", offset=desplazamiento, timeout=25,
                                               allowed_updates=["message"])
            except Exception as e:   # noqa: BLE001
                log.warning("getUpdates falló (%s); reintento en 10 s", type(e).__name__)
                await asyncio.sleep(10)
                continue
            for u in novedades:
                desplazamiento = u["update_id"] + 1
                await self._atender(u.get("message") or {}, vincular)

    async def _atender(self, mensaje: dict, vincular) -> None:
        texto = (mensaje.get("text") or "").strip()
        chat = mensaje.get("chat") or {}
        if not texto.startswith("/start") or "id" not in chat:
            return
        partes = texto.split(maxsplit=1)
        chat_id = str(chat["id"])
        try:
            if len(partes) == 2 and await vincular(partes[1].strip(), chat_id) is not None:
                respuesta = ("Listo: este chat quedó vinculado a Gestión Jurídica. Aquí recibirá los avisos de "
                             "términos y de actuaciones nuevas en sus procesos.")
            else:
                respuesta = ("Para vincular este chat, abra Gestión Jurídica → Avisos → Vincular Telegram y use el "
                             "enlace que aparece (sirve una vez y por 24 horas).")
            await self.enviar(chat_id, respuesta)
        except Exception:   # noqa: BLE001
            log.exception("No se pudo atender el /start de un chat")


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
    Consume termino.*, actuacion.detectada y audiencia.proxima de la cola durable notificaciones.avisos y publica
    notificacion.enviada.
    Si RabbitMQ no está listo al arrancar, reintenta en segundo plano sin tumbar la API.
    """

    COLA = "notificaciones.avisos"
    CLAVES = ("termino.por_vencer", "termino.vencido", "actuacion.detectada", "audiencia.proxima",
              "portal.resumen_publicado")

    def __init__(self, cfg: Config):
        self.cfg = cfg
        self.conexion: aio_pika.abc.AbstractConnection | None = None
        self.exchange: aio_pika.abc.AbstractExchange | None = None

    async def iniciar(self, procesar) -> None:
        """
        Conecta, consume y, si la conexión se cae (p. ej. RabbitMQ se reinició), vuelve a conectar y a consumir.
        No se usa connect_robust: tras un reinicio de RabbitMQ su reconexión podía quedarse detenida y la cola se
        quedaba sin consumidor (F9). Los mensajes esperan en la cola durable mientras tanto.
        """
        while True:
            try:
                cerrada = asyncio.Event()
                await self._conectar(procesar)
                self.conexion.close_callbacks.add(lambda *_: cerrada.set())
                await cerrada.wait()
                log.warning("Se perdió la conexión con RabbitMQ; reconectando en 5 s")
            except asyncio.CancelledError:
                raise
            except Exception as e:   # noqa: BLE001
                log.warning("RabbitMQ no disponible (%s); reintento en 5 s", e)
            self.exchange = None
            await asyncio.sleep(5)

    async def _conectar(self, procesar) -> None:
        self.conexion = await aio_pika.connect(self.cfg.rabbitmq_url)
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
