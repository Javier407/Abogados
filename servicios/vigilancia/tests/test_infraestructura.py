"""Adaptadores con dobles: conexiones falsas que registran el SQL, portal simulado y RabbitMQ falso."""
import asyncio
import json
from contextlib import asynccontextmanager
from datetime import date, datetime, timezone
from types import SimpleNamespace

import aio_pika
import httpx
import pytest
from psycopg.types.json import Jsonb

from app import infraestructura as infra
from app.dominio import Actuacion, ErrorFuente, Fuente, RadicadoVigilado
from app.infraestructura import (ClienteProcesos, FuentePortalHttp, FuenteSimulada, Mensajeria, PublicadorOutbox,
                                 RelayOutbox, RepositorioPostgres)
from tests.test_vigilancia import BUSQUEDA, RAD, portal


# ------------------------------------------------------------------ dobles de PostgreSQL

class CursorFalso:
    def __init__(self, con, filas):
        self.con = con
        self.filas = list(filas)

    async def fetchall(self):
        return list(self.filas)

    async def fetchone(self):
        return self.filas[0] if self.filas else None

    async def executemany(self, sql, params):
        self.con.sql.append((" ".join(sql.split()), list(params)))

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_):
        return False


class ConexionFalsa:
    """Registra cada sentencia; las que devuelven filas (SELECT o RETURNING) toman, en orden, las preparadas."""

    def __init__(self, *respuestas, falla_en: int | None = None):
        self.respuestas = list(respuestas)
        self.sql: list[tuple[str, object]] = []
        self.transacciones = 0
        self.falla_en = falla_en

    async def execute(self, sql, params=None):
        if self.falla_en is not None and len(self.sql) == self.falla_en:
            self.falla_en = None
            raise RuntimeError("PostgreSQL caído")
        sql = " ".join(sql.split())
        self.sql.append((sql, params))
        devuelve = sql.startswith("SELECT") or "RETURNING" in sql
        return CursorFalso(self, self.respuestas.pop(0) if devuelve and self.respuestas else [])

    def cursor(self):
        return CursorFalso(self, [])

    @asynccontextmanager
    async def transaction(self):
        self.transacciones += 1
        yield


class PoolFalso:
    def __init__(self, con: ConexionFalsa):
        self.con = con

    @asynccontextmanager
    async def connection(self):
        yield self.con


def repo(*respuestas):
    con = ConexionFalsa(*respuestas)
    return RepositorioPostgres(PoolFalso(con)), con


FILA = (RAD + "  ", 34, "SIMULADA", 24, True, None, 2, True, "Juzgado 032", date(2026, 10, 14), "HTTP 503")


# ------------------------------------------------------------------ repositorio

async def test_radicados_convierte_las_filas_y_ordena_por_fallos():
    r, con = repo([FILA])
    [rv] = await r.radicados()
    assert rv == RadicadoVigilado(RAD, 34, Fuente.SIMULADA, 24, True, None, 2, True, "Juzgado 032",
                                  date(2026, 10, 14), "HTTP 503")
    assert "ORDER BY fallos_consecutivos DESC, radicado" in con.sql[0][0]


async def test_radicado_por_numero_o_none():
    r, con = repo([FILA], [])
    assert (await r.radicado(RAD)).radicado == RAD
    assert await r.radicado("11001310303220269999900") is None
    assert con.sql[1][1] == ("11001310303220269999900",)
    assert "WHERE radicado = %s" in con.sql[0][0]


async def test_registrar_dice_si_era_nuevo():
    r, con = repo([(RAD,)], [])
    assert await r.registrar(RAD, 34, Fuente.MANUAL)
    assert not await r.registrar(RAD, 34, Fuente.MANUAL)
    sql, params = con.sql[0]
    assert "ON CONFLICT (radicado) DO NOTHING" in sql and params == (RAD, 34, "MANUAL")


async def test_configurar_envia_la_fuente_y_reinicia_la_linea_base_si_cambia():
    r, con = repo()
    await r.configurar(RAD, Fuente.PORTAL_HTTP, 24, False)
    sql, params = con.sql[0]
    assert sql.startswith("UPDATE vigilancia.radicados_vigilados")
    assert "linea_base_hecha = CASE WHEN fuente = %s THEN linea_base_hecha ELSE false END" in sql
    assert params == ("PORTAL_HTTP", "PORTAL_HTTP", "PORTAL_HTTP", 24, False, RAD)


async def test_referencias_conocidas_es_un_conjunto():
    r, _ = repo([("PORTAL:1",), ("PORTAL:2",), ("PORTAL:1",)])
    assert await r.referencias_conocidas(RAD) == {"PORTAL:1", "PORTAL:2"}


async def test_guardar_conocidas_recorta_textos_y_no_hace_nada_sin_actuaciones():
    r, con = repo()
    await r.guardar_conocidas(RAD, [], avisadas=True)
    assert con.sql == []

    larga = Actuacion("PORTAL:9", date(2026, 10, 1), "A" * 300, None)
    await r.guardar_conocidas(RAD, [larga, Actuacion("PORTAL:8", date(2026, 9, 1), "Auto", "x" * 2000)], True)
    sql, filas = con.sql[0]
    assert "ON CONFLICT DO NOTHING" in sql
    assert filas[0] == (RAD, "PORTAL:9", date(2026, 10, 1), "A" * 200, "", True)
    assert len(filas[1][4]) == 1000


async def test_registrar_consulta_guarda_el_detalle_y_actualiza_el_radicado():
    r, con = repo()
    await r.registrar_consulta(RAD, False, 0, {"error": "HTTP 503"})
    (ins, p1), (upd, p2) = con.sql
    assert ins.startswith("INSERT INTO vigilancia.consultas")
    assert p1[:3] == (RAD, False, 0) and isinstance(p1[3], Jsonb) and p1[3].obj == {"error": "HTTP 503"}
    assert "fallos_consecutivos + 1" in upd
    assert p2 == (False, False, "HTTP 503", False, None, None, RAD)

    await r.registrar_consulta(RAD, True, 2, {"nuevas": 2}, "Juzgado 032", date(2026, 10, 14))
    assert con.sql[-1][1] == (True, True, None, True, "Juzgado 032", date(2026, 10, 14), RAD)


async def test_consultas_devuelve_el_historial():
    cuando = datetime(2026, 10, 15, 15, 0, tzinfo=timezone.utc)
    r, con = repo([(cuando, True, 1, {"nuevas": 1}), (cuando, False, 0, None)])
    h = await r.consultas(RAD, 5)
    assert h == [{"fecha": cuando.isoformat(), "exitosa": True, "nuevas": 1, "detalle": {"nuevas": 1}},
                 {"fecha": cuando.isoformat(), "exitosa": False, "nuevas": 0, "detalle": {}}]
    assert con.sql[0][1] == (RAD, 5)


async def test_simuladas_se_agregan_y_se_leen_como_actuaciones():
    r, con = repo([(7,)], [(7, date(2026, 10, 15), "Auto admite demanda", "")])
    assert await r.agregar_simulada(RAD, date(2026, 10, 15), "Auto admite demanda", "") == 7
    assert con.sql[0][1] == (RAD, date(2026, 10, 15), "Auto admite demanda", "")

    resultado = await FuenteSimulada(r).consultar(RAD)
    assert resultado.encontrado and resultado.despacho == "Fuente simulada"
    assert resultado.actuaciones == [Actuacion("SIM:7", date(2026, 10, 15), "Auto admite demanda", "",
                                               date(2026, 10, 15))]


# ------------------------------------------------------------------ portal

def test_portal_sin_cliente_crea_uno_que_pide_json():
    p = FuentePortalHttp("https://portal.test/api/v2/", timeout_seg=3)
    assert p.url == "https://portal.test/api/v2"
    assert p.cliente.headers["Accept"] == "application/json"
    assert "GestionJuridica" in p.cliente.headers["User-Agent"]
    assert p.cliente.timeout.read == 3


async def test_portal_que_no_responde_json_es_error_de_fuente():
    with pytest.raises(ErrorFuente, match="no es JSON"):
        await portal(lambda req: httpx.Response(200, text="<html>mantenimiento</html>")).consultar(RAD)


async def test_portal_sin_procesos_es_no_encontrado():
    assert not (await portal(lambda req: httpx.Response(200, json={"procesos": None})).consultar(RAD)).encontrado
    otro = {"procesos": [{"idProceso": 1, "llaveProceso": "11001310303220269999900", "esPrivado": False}]}
    assert not (await portal(lambda req: httpx.Response(200, json=otro)).consultar(RAD)).encontrado


async def test_portal_omite_actuaciones_mal_formadas_y_despacho_vacio():
    busqueda = {"procesos": [{"idProceso": 5, "llaveProceso": f" {RAD} ", "despacho": "   ", "esPrivado": False}]}
    actuaciones = {"actuaciones": [
        {"idRegActuacion": 1, "fechaActuacion": "2026-10-14T00:00:00", "actuacion": "Sentencia", "anotacion": None},
        {"idRegActuacion": 2, "actuacion": "sin fecha"},
        {"idRegActuacion": 3, "fechaActuacion": "no-es-fecha", "actuacion": "x"},
        {"fechaActuacion": "2026-10-14T00:00:00", "actuacion": "sin id"}]}

    def manejador(req):
        return httpx.Response(200, json=busqueda if req.url.path.endswith("NumeroRadicacion") else actuaciones)

    r = await portal(manejador).consultar(RAD)
    assert r.encontrado and r.despacho is None
    assert [a.referencia for a in r.actuaciones] == ["PORTAL:1"]
    assert r.actuaciones[0].fecha_registro is None and r.actuaciones[0].tipo == "SENTENCIA"


async def test_portal_falla_en_la_segunda_llamada():
    def manejador(req):
        if req.url.path.endswith("NumeroRadicacion"):
            return httpx.Response(200, json=BUSQUEDA)
        return httpx.Response(429)

    with pytest.raises(ErrorFuente, match="HTTP 429"):
        await portal(manejador).consultar(RAD)


# ------------------------------------------------------------------ outbox

async def test_publicar_es_guardar_el_evento_en_el_outbox():
    con = ConexionFalsa()
    rv = RadicadoVigilado(RAD, 34, Fuente.PORTAL_HTTP)
    a = Actuacion("PORTAL:9", date(2026, 10, 14), "Sentencia de primera instancia", "Niega pretensiones")

    await PublicadorOutbox(PoolFalso(con)).actuacion_detectada(rv, a, Fuente.PORTAL_HTTP)

    sql, (clave, cuerpo) = con.sql[0]
    assert sql == "INSERT INTO vigilancia.outbox (clave, cuerpo) VALUES (%s, %s)"
    assert clave == "actuacion.detectada" and isinstance(cuerpo, Jsonb)
    assert {k: v for k, v in cuerpo.obj.items() if k != "ocurrido"} == {
        "radicado": RAD, "id_proceso": 34, "referencia": "PORTAL:9", "fecha": "2026-10-14", "tipo": "SENTENCIA",
        "actuacion": "Sentencia de primera instancia", "anotacion": "Niega pretensiones", "fuente": "PORTAL_HTTP"}
    assert datetime.fromisoformat(cuerpo.obj["ocurrido"]).tzinfo is not None


class MensajeriaFalsa:
    def __init__(self, fallan=()):
        self.fallan = set(fallan)
        self.publicados = []

    async def publicar(self, clave, cuerpo, id_mensaje):
        if id_mensaje in self.fallan:
            raise ConnectionError("sin confirmación del broker")
        self.publicados.append((clave, cuerpo, id_mensaje))


PENDIENTES = [(1, "actuacion.detectada", '{"n": 1}'), (2, "actuacion.detectada", '{"n": 2}'),
              (3, "actuacion.detectada", '{"n": 3}')]


async def test_relay_envia_en_orden_y_marca_enviados():
    con = ConexionFalsa(PENDIENTES)
    mq = MensajeriaFalsa()
    assert await RelayOutbox(PoolFalso(con), mq).enviar_pendientes() == 3
    assert [p[2] for p in mq.publicados] == ["vigilancia-1", "vigilancia-2", "vigilancia-3"]
    assert con.transacciones == 1 and "FOR UPDATE SKIP LOCKED" in con.sql[0][0]
    assert [p for _, p in con.sql[1:]] == [(1,), (2,), (3,)]
    assert all("enviado_en = now()" in s for s, _ in con.sql[1:])


async def test_relay_se_detiene_en_el_primer_fallo_y_lo_reintenta_despues(caplog):
    con = ConexionFalsa(PENDIENTES, PENDIENTES[1:], PENDIENTES[1:], PENDIENTES[1:])
    mq = MensajeriaFalsa(fallan={"vigilancia-2"})
    relay = RelayOutbox(PoolFalso(con), mq)

    assert await relay.enviar_pendientes() == 1
    error = con.sql[2]
    assert "intentos = intentos + 1, ultimo_error = %s" in error[0]
    assert error[1] == ("sin confirmación del broker", 2)
    assert len(con.sql) == 3   # el 3 no se intenta: el orden se respeta
    assert "queda pendiente" in caplog.text

    caplog.clear()
    assert await relay.enviar_pendientes() == 0   # sigue caído tras un envío a medias: no repite el aviso
    assert await relay.enviar_pendientes() == 0
    assert "queda pendiente" not in caplog.text

    mq.fallan.clear()
    assert await relay.enviar_pendientes() == 2
    assert not relay._caido


def asyncio_falso(monkeypatch, modulo, vueltas_hasta_cancelar: int):
    pausas = []

    async def dormir(seg):
        pausas.append(seg)
        if len(pausas) >= vueltas_hasta_cancelar:
            raise asyncio.CancelledError

    monkeypatch.setattr(modulo, "asyncio", SimpleNamespace(sleep=dormir, Event=asyncio.Event,
                                                           CancelledError=asyncio.CancelledError))
    return pausas


async def test_relay_ejecuta_cada_dos_segundos_y_purga_lo_viejo(monkeypatch):
    pausas = asyncio_falso(monkeypatch, infra, 1800)
    con = ConexionFalsa()
    with pytest.raises(asyncio.CancelledError):
        await RelayOutbox(PoolFalso(con), MensajeriaFalsa()).ejecutar()
    assert pausas == [2.0] * 1800
    borrados = [s for s, _ in con.sql if s.startswith("DELETE")]
    assert borrados == ["DELETE FROM vigilancia.outbox WHERE enviado_en < now() - interval '7 days'"]


async def test_relay_sigue_vivo_si_falla_la_base(monkeypatch, caplog):
    pausas = asyncio_falso(monkeypatch, infra, 2)
    con = ConexionFalsa(falla_en=0)
    with pytest.raises(asyncio.CancelledError):
        await RelayOutbox(PoolFalso(con), MensajeriaFalsa(), cada_seg=0.5).ejecutar()
    assert pausas == [0.5, 0.5] and "Error enviando el outbox" in caplog.text
    assert len(con.sql) == 1   # la segunda vuelta sí consultó


# ------------------------------------------------------------------ servicio procesos

def http_falso(monkeypatch, manejador):
    original = httpx.AsyncClient

    def cliente(**kw):
        return original(transport=httpx.MockTransport(manejador), **kw)

    monkeypatch.setattr(infra, "httpx", SimpleNamespace(AsyncClient=cliente))


class TokenFijo:
    async def cabeceras(self):
        return {"Authorization": "Bearer t-servicio"}


async def test_cliente_procesos_devuelve_solo_los_vigilados_con_el_token_del_servicio(monkeypatch):
    pedidas = []

    def manejador(req):
        pedidas.append((str(req.url), req.headers.get("authorization")))
        return httpx.Response(200, json=[{"radicado": RAD, "idProceso": 34, "vigilado": True},
                                         {"radicado": "11001310303220260000200", "idProceso": 35, "vigilado": False},
                                         {"radicado": "11001310303220260000300", "idProceso": 36}])

    http_falso(monkeypatch, manejador)
    assert await ClienteProcesos("http://procesos.test/", TokenFijo()).vigilados() == [(RAD, 34)]
    assert await ClienteProcesos("http://procesos.test").vigilados() == [(RAD, 34)]
    assert pedidas == [("http://procesos.test/procesos", "Bearer t-servicio"), ("http://procesos.test/procesos", None)]


async def test_cliente_procesos_con_error_http_lanza(monkeypatch):
    http_falso(monkeypatch, lambda req: httpx.Response(500))
    with pytest.raises(httpx.HTTPStatusError):
        await ClienteProcesos("http://procesos.test").vigilados()


# ------------------------------------------------------------------ RabbitMQ

class ExchangeFalso:
    def __init__(self):
        self.mensajes = []

    async def publish(self, mensaje, routing_key, timeout):
        self.mensajes.append((mensaje, routing_key, timeout))


class ColaFalsa:
    def __init__(self):
        self.enlaces = []
        self.al_recibir = None

    async def bind(self, exchange, routing_key):
        self.enlaces.append((exchange, routing_key))

    async def consume(self, cb):
        self.al_recibir = cb


class CanalFalso:
    def __init__(self):
        self.exchange = ExchangeFalso()
        self.cola = ColaFalsa()
        self.declarados = []

    async def declare_exchange(self, nombre, tipo, durable):
        self.declarados.append(("exchange", nombre, tipo, durable))
        return self.exchange

    async def declare_queue(self, nombre, durable):
        self.declarados.append(("cola", nombre, durable))
        return self.cola


class CallbacksQueCierran(set):
    """La conexión se cae en cuanto se registra quién escucha el cierre."""

    def add(self, cb):
        super().add(cb)
        cb(None)


class ConexionMqFalsa:
    def __init__(self):
        self.canal = CanalFalso()
        self.close_callbacks = CallbacksQueCierran()
        self.cerrada = False

    async def channel(self):
        return self.canal

    async def close(self):
        self.cerrada = True


class MensajeFalso:
    def __init__(self, cuerpo: bytes):
        self.body = cuerpo
        self.resultado = None

    async def ack(self):
        self.resultado = "ack"

    async def nack(self, requeue):
        self.resultado = ("nack", requeue)


async def test_publicar_sin_conexion_falla_y_con_conexion_es_persistente():
    mq = Mensajeria("amqp://mq.test/", "gabinete.eventos")
    with pytest.raises(RuntimeError, match="no está conectado"):
        await mq.publicar("actuacion.detectada", "{}", "vigilancia-1")

    mq.exchange = ExchangeFalso()
    await mq.publicar("actuacion.detectada", '{"n": 1}', "vigilancia-1")
    [(m, clave, timeout)] = mq.exchange.mensajes
    assert clave == "actuacion.detectada" and timeout == 5
    assert m.body == b'{"n": 1}' and m.message_id == "vigilancia-1" and m.content_type == "application/json"
    assert m.delivery_mode == aio_pika.DeliveryMode.PERSISTENT


async def test_conectar_declara_la_cola_y_procesa_proceso_creado(monkeypatch):
    conexion = ConexionMqFalsa()
    urls = []

    async def conectar(url):
        urls.append(url)
        return conexion

    monkeypatch.setattr(infra.aio_pika, "connect", conectar)
    pausas = asyncio_falso(monkeypatch, infra, 99)
    recibidos = []

    async def proceso_creado(evento):
        recibidos.append(evento)
        if evento.get("radicado") == "explota":
            raise RuntimeError("base caída")
        if evento.get("radicado") == "incompleto":
            raise KeyError("id_proceso")
        return evento.get("radicado") == RAD

    mq = Mensajeria("amqp://mq.test/", "gabinete.eventos")
    await mq._conectar(proceso_creado)

    canal = conexion.canal
    assert urls == ["amqp://mq.test/"] and mq.exchange is canal.exchange
    assert canal.declarados == [("exchange", "gabinete.eventos", aio_pika.ExchangeType.TOPIC, True),
                                ("cola", "vigilancia.procesos", True)]
    assert canal.cola.enlaces == [(canal.exchange, "proceso.creado")]

    casos = {"nuevo": json.dumps({"radicado": RAD, "id_proceso": 34}).encode(),
             "repetido": json.dumps({"radicado": "otro", "id_proceso": 1}).encode(),
             "basura": b"no es json",
             "incompleto": json.dumps({"radicado": "incompleto"}).encode(),
             "explota": json.dumps({"radicado": "explota"}).encode()}
    msgs = {k: MensajeFalso(v) for k, v in casos.items()}
    for m in msgs.values():
        await canal.cola.al_recibir(m)

    assert {k: m.resultado for k, m in msgs.items()} == {
        "nuevo": "ack", "repetido": "ack", "basura": "ack", "incompleto": "ack", "explota": ("nack", True)}
    assert pausas == [5] and len(recibidos) == 4

    await mq.cerrar()
    assert conexion.cerrada


async def test_iniciar_reintenta_si_rabbitmq_no_esta_y_reconecta_si_se_cae(monkeypatch, caplog):
    intentos = []

    async def conectar(url):
        intentos.append(url)
        if len(intentos) == 1:
            raise ConnectionError("rechazada")
        return ConexionMqFalsa()

    monkeypatch.setattr(infra.aio_pika, "connect", conectar)
    pausas = asyncio_falso(monkeypatch, infra, 2)
    mq = Mensajeria("amqp://mq.test/", "gabinete.eventos")

    with pytest.raises(asyncio.CancelledError):
        await mq.iniciar(lambda e: None)

    assert len(intentos) == 2 and pausas == [5, 5]
    assert "RabbitMQ no disponible" in caplog.text and "Se perdió la conexión" in caplog.text
    assert mq.exchange is None


async def test_iniciar_no_atrapa_la_cancelacion(monkeypatch):
    async def conectar(url):
        raise asyncio.CancelledError

    monkeypatch.setattr(infra.aio_pika, "connect", conectar)
    pausas = asyncio_falso(monkeypatch, infra, 99)
    with pytest.raises(asyncio.CancelledError):
        await Mensajeria("amqp://mq.test/", "x").iniciar(lambda e: None)
    assert pausas == []


async def test_cerrar_sin_conexion_no_hace_nada():
    await Mensajeria("amqp://mq.test/", "x").cerrar()
