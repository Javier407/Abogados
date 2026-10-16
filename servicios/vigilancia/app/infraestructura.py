"""Adaptadores: PostgreSQL, fuentes (portal de la Rama Judicial y simulada), RabbitMQ y servicio procesos."""
from __future__ import annotations

import asyncio
import json
import logging
from datetime import date, datetime, timezone

import aio_pika
import httpx
from psycopg.types.json import Jsonb
from psycopg_pool import AsyncConnectionPool

from .dominio import Actuacion, ErrorFuente, Fuente, RadicadoVigilado, ResultadoConsulta

log = logging.getLogger(__name__)


# ------------------------------------------------------------------ PostgreSQL

class RepositorioPostgres:
    COLUMNAS = """radicado, id_proceso, fuente, frecuencia_horas, activo, ultima_consulta, fallos_consecutivos,
                  linea_base_hecha, despacho_portal, ultima_actuacion, ultimo_error"""

    def __init__(self, pool: AsyncConnectionPool):
        self.pool = pool

    @staticmethod
    def _radicado(f) -> RadicadoVigilado:
        return RadicadoVigilado(radicado=f[0].strip(), id_proceso=f[1], fuente=Fuente(f[2]), frecuencia_horas=f[3],
                                activo=f[4], ultima_consulta=f[5], fallos_consecutivos=f[6], linea_base_hecha=f[7],
                                despacho=f[8], ultima_actuacion=f[9], ultimo_error=f[10])

    async def radicados(self) -> list[RadicadoVigilado]:
        async with self.pool.connection() as con:
            cur = await con.execute(f"SELECT {self.COLUMNAS} FROM vigilancia.radicados_vigilados "
                                    "ORDER BY fallos_consecutivos DESC, radicado")
            return [self._radicado(f) for f in await cur.fetchall()]

    async def radicado(self, radicado: str) -> RadicadoVigilado | None:
        async with self.pool.connection() as con:
            cur = await con.execute(f"SELECT {self.COLUMNAS} FROM vigilancia.radicados_vigilados WHERE radicado = %s",
                                    (radicado,))
            f = await cur.fetchone()
            return self._radicado(f) if f else None

    async def registrar(self, radicado: str, id_proceso: int, fuente: Fuente) -> bool:
        async with self.pool.connection() as con:
            cur = await con.execute(
                """INSERT INTO vigilancia.radicados_vigilados (radicado, id_proceso, fuente)
                   VALUES (%s, %s, %s) ON CONFLICT (radicado) DO NOTHING RETURNING radicado""",
                (radicado, id_proceso, fuente.value))
            return await cur.fetchone() is not None

    async def configurar(self, radicado: str, fuente: Fuente, frecuencia_horas: int, activo: bool) -> None:
        async with self.pool.connection() as con:
            await con.execute(
                """UPDATE vigilancia.radicados_vigilados
                   SET linea_base_hecha = CASE WHEN fuente = %s THEN linea_base_hecha ELSE false END,
                       fallos_consecutivos = CASE WHEN fuente = %s THEN fallos_consecutivos ELSE 0 END,
                       fuente = %s, frecuencia_horas = %s, activo = %s
                   WHERE radicado = %s""",
                (fuente.value, fuente.value, fuente.value, frecuencia_horas, activo, radicado))

    async def referencias_conocidas(self, radicado: str) -> set[str]:
        async with self.pool.connection() as con:
            cur = await con.execute("SELECT referencia FROM vigilancia.actuaciones_conocidas WHERE radicado = %s",
                                    (radicado,))
            return {f[0] for f in await cur.fetchall()}

    async def guardar_conocidas(self, radicado: str, actuaciones: list[Actuacion], avisadas: bool) -> None:
        if not actuaciones:
            return
        async with self.pool.connection() as con:
            async with con.cursor() as cur:
                await cur.executemany(
                    """INSERT INTO vigilancia.actuaciones_conocidas
                           (radicado, referencia, fecha, actuacion, anotacion, avisada)
                       VALUES (%s, %s, %s, %s, %s, %s) ON CONFLICT DO NOTHING""",
                    [(radicado, a.referencia, a.fecha, a.actuacion[:200], (a.anotacion or "")[:1000], avisadas)
                     for a in actuaciones])

    async def registrar_consulta(self, radicado: str, exitosa: bool, nuevas: int, detalle: dict,
                                 despacho: str | None = None, ultima_actuacion: date | None = None) -> None:
        async with self.pool.connection() as con:
            await con.execute(
                """INSERT INTO vigilancia.consultas (radicado, exitosa, actuaciones_nuevas, detalle)
                   VALUES (%s, %s, %s, %s)""", (radicado, exitosa, nuevas, Jsonb(detalle)))
            await con.execute(
                """UPDATE vigilancia.radicados_vigilados
                   SET ultima_consulta = now(),
                       fallos_consecutivos = CASE WHEN %s THEN 0 ELSE fallos_consecutivos + 1 END,
                       ultimo_error = CASE WHEN %s THEN NULL ELSE %s END,
                       linea_base_hecha = linea_base_hecha OR %s,
                       despacho_portal = coalesce(%s, despacho_portal),
                       ultima_actuacion = greatest(ultima_actuacion, %s::date)
                   WHERE radicado = %s""",
                (exitosa, exitosa, detalle.get("error"), exitosa, despacho, ultima_actuacion, radicado))

    async def consultas(self, radicado: str, limite: int) -> list[dict]:
        async with self.pool.connection() as con:
            cur = await con.execute(
                """SELECT fecha, exitosa, actuaciones_nuevas, detalle FROM vigilancia.consultas
                   WHERE radicado = %s ORDER BY fecha DESC LIMIT %s""", (radicado, limite))
            return [{"fecha": f.isoformat(), "exitosa": e, "nuevas": n, "detalle": d or {}}
                    for f, e, n, d in await cur.fetchall()]

    async def agregar_simulada(self, radicado: str, fecha, actuacion: str, anotacion: str) -> int:
        async with self.pool.connection() as con:
            cur = await con.execute(
                """INSERT INTO vigilancia.actuaciones_simuladas (radicado, fecha, actuacion, anotacion)
                   VALUES (%s, %s, %s, %s) RETURNING id""", (radicado, fecha, actuacion, anotacion))
            return (await cur.fetchone())[0]

    async def simuladas(self, radicado: str) -> list[Actuacion]:
        async with self.pool.connection() as con:
            cur = await con.execute(
                """SELECT id, fecha, actuacion, coalesce(anotacion, '') FROM vigilancia.actuaciones_simuladas
                   WHERE radicado = %s ORDER BY fecha DESC, id DESC""", (radicado,))
            return [Actuacion(f"SIM:{i}", fecha, act, anot, fecha) for i, fecha, act, anot in await cur.fetchall()]


# ------------------------------------------------------------------ fuentes

class FuenteSimulada:
    """Devuelve las actuaciones escritas en la pantalla de vigilancia. Para demostraciones y pruebas."""

    def __init__(self, repo: RepositorioPostgres):
        self.repo = repo

    async def consultar(self, radicado: str) -> ResultadoConsulta:
        return ResultadoConsulta(True, await self.repo.simuladas(radicado), "Fuente simulada")


class FuentePortalHttp:
    """
    Consulta de Procesos Nacional Unificada de la Rama Judicial (endpoints internos del portal, sin
    documentación oficial; ver ADR 0005). Dos llamadas por radicado: buscar el proceso y leer la primera
    página de actuaciones (las 40 más recientes). El servicio espacia las consultas entre radicados.
    """

    def __init__(self, url_base: str, timeout_seg: float = 45.0, cliente: httpx.AsyncClient | None = None):
        self.url = url_base.rstrip("/")
        self.cliente = cliente or httpx.AsyncClient(
            timeout=timeout_seg,
            headers={"Accept": "application/json",
                     "User-Agent": "GestionJuridica/0.1 (vigilancia de procesos propios de la firma)"})

    async def _get(self, ruta: str, params: dict | None = None) -> dict:
        try:
            r = await self.cliente.get(f"{self.url}{ruta}", params=params)
        except httpx.HTTPError as e:
            raise ErrorFuente(f"El portal no respondió: {type(e).__name__}") from e
        if r.status_code != 200:
            raise ErrorFuente(f"El portal respondió HTTP {r.status_code}")
        try:
            return r.json()
        except ValueError as e:
            raise ErrorFuente("El portal respondió algo que no es JSON") from e

    async def consultar(self, radicado: str) -> ResultadoConsulta:
        datos = await self._get("/Procesos/Consulta/NumeroRadicacion",
                                {"numero": radicado, "SoloActivos": "false", "pagina": 1})
        candidatos = [p for p in datos.get("procesos") or []
                      if str(p.get("llaveProceso", "")).strip() == radicado and not p.get("esPrivado")]
        if not candidatos:
            return ResultadoConsulta(False)
        proceso = max(candidatos, key=lambda p: p.get("fechaUltimaActuacion") or "")
        datos = await self._get(f"/Proceso/Actuaciones/{proceso['idProceso']}", {"pagina": 1})
        actuaciones = []
        for a in datos.get("actuaciones") or []:
            try:
                actuaciones.append(Actuacion(
                    referencia=f"PORTAL:{a['idRegActuacion']}",
                    fecha=date.fromisoformat(a["fechaActuacion"][:10]),
                    actuacion=" ".join(str(a.get("actuacion") or "").split()),
                    anotacion=" ".join(str(a.get("anotacion") or "").split()),
                    fecha_registro=date.fromisoformat(a["fechaRegistro"][:10]) if a.get("fechaRegistro") else None))
            except (KeyError, TypeError, ValueError):
                log.warning("Actuación del portal con formato inesperado; se omite")
        return ResultadoConsulta(True, actuaciones, " ".join(str(proceso.get("despacho") or "").split()) or None)


# ------------------------------------------------------------------ servicio procesos

class ClienteProcesos:
    def __init__(self, url: str):
        self.url = url.rstrip("/")

    async def vigilados(self) -> list[tuple[str, int]]:
        async with httpx.AsyncClient(timeout=10) as http:
            r = await http.get(f"{self.url}/procesos")
            r.raise_for_status()
            return [(p["radicado"], p["idProceso"]) for p in r.json() if p.get("vigilado")]


# ------------------------------------------------------------------ RabbitMQ

class Mensajeria:
    """Consume proceso.creado (cola vigilancia.procesos) y publica actuacion.detectada."""

    COLA = "vigilancia.procesos"

    def __init__(self, url: str, exchange: str):
        self.url = url
        self.nombre_exchange = exchange
        self.conexion = None
        self.exchange = None

    async def iniciar(self, proceso_creado) -> None:
        while True:
            try:
                self.conexion = await aio_pika.connect_robust(self.url)
                break
            except Exception as e:   # noqa: BLE001
                log.warning("RabbitMQ no disponible (%s); reintento en 5 s", e)
                await asyncio.sleep(5)
        canal = await self.conexion.channel()
        self.exchange = await canal.declare_exchange(self.nombre_exchange, aio_pika.ExchangeType.TOPIC, durable=True)
        cola = await canal.declare_queue(self.COLA, durable=True)
        await cola.bind(self.exchange, routing_key="proceso.creado")

        async def al_recibir(msg: aio_pika.abc.AbstractIncomingMessage) -> None:
            try:
                cuerpo = json.loads(msg.body)
                if await proceso_creado(cuerpo):
                    log.info("Radicado %s agregado a la vigilancia", cuerpo.get("radicado"))
                await msg.ack()
            except (KeyError, ValueError, TypeError) as e:
                log.error("proceso.creado mal formado, se descarta: %s", e)
                await msg.ack()
            except Exception:   # noqa: BLE001
                log.exception("Error registrando el radicado; se reintenta en 5 s")
                await asyncio.sleep(5)
                await msg.nack(requeue=True)

        await cola.consume(al_recibir)
        log.info("Escuchando proceso.creado en la cola %s", self.COLA)

    async def actuacion_detectada(self, rv: RadicadoVigilado, a: Actuacion, fuente: Fuente) -> None:
        if self.exchange is None:
            raise RuntimeError("RabbitMQ no está conectado")
        cuerpo = {"radicado": rv.radicado, "id_proceso": rv.id_proceso, "referencia": a.referencia,
                  "fecha": a.fecha.isoformat(), "tipo": a.tipo, "actuacion": a.actuacion, "anotacion": a.anotacion,
                  "fuente": fuente.value, "ocurrido": datetime.now(timezone.utc).isoformat()}
        await self.exchange.publish(
            aio_pika.Message(json.dumps(cuerpo, ensure_ascii=False).encode(), content_type="application/json",
                             delivery_mode=aio_pika.DeliveryMode.PERSISTENT),
            routing_key="actuacion.detectada")

    async def cerrar(self) -> None:
        if self.conexion is not None:
            await self.conexion.close()
