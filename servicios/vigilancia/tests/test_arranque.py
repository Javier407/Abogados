"""Arranque del servicio (lifespan con adaptadores falsos), tarea periódica y casos de uso que faltaban."""
import asyncio
from datetime import date
from types import SimpleNamespace

import psycopg_pool
import pytest
from fastapi.testclient import TestClient

from app import infraestructura as infra
from app import main
from app.aplicacion import ServicioVigilancia
from app.dominio import Actuacion, Fuente, RadicadoVigilado
from app.main import crear_app
from app.seguridad import VerificadorTokens
from tests.test_seguridad import LlavesFijas, token
from tests.test_vigilancia import AHORA, RAD, FuenteFija, Publicador, RepoMemoria, act

OTRO = "11001310303220260000200"


class ProcesosFijos:
    def __init__(self, *vigilados):
        self.lista = list(vigilados)

    async def vigilados(self):
        return list(self.lista)


# ------------------------------------------------------------------ casos de uso

async def test_sincronizar_registra_solo_los_que_faltan():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.MANUAL))
    assert await ServicioVigilancia(repo, {}, Publicador()).sincronizar() == 0
    s = ServicioVigilancia(repo, {}, Publicador(), ProcesosFijos((RAD, 1), (OTRO, 2)))
    assert await s.sincronizar() == 1
    assert repo.datos[OTRO].fuente == Fuente.MANUAL and repo.datos[OTRO].id_proceso == 2


async def test_configurar_rechaza_fuente_no_disponible_y_frecuencia_fuera_de_rango():
    s = ServicioVigilancia(RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.MANUAL)), {Fuente.SIMULADA: FuenteFija()},
                           Publicador())
    with pytest.raises(ValueError, match="no está disponible"):
        await s.configurar(RAD, Fuente.PORTAL_HTTP, 24, True)
    with pytest.raises(ValueError, match="entre 1 y 168"):
        await s.configurar(RAD, Fuente.SIMULADA, 200, True)
    assert (await s.configurar(RAD, Fuente.MANUAL, 24, False)).activo is False


async def test_agregar_simulada_valida_y_recorta():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA))
    s = ServicioVigilancia(repo, {Fuente.SIMULADA: FuenteFija()}, Publicador())
    with pytest.raises(LookupError):
        await s.agregar_simulada(OTRO, date(2026, 10, 15), "Auto", "")
    with pytest.raises(ValueError, match="obligatoria"):
        await s.agregar_simulada(RAD, date(2026, 10, 15), "   ", "")
    assert await s.agregar_simulada(RAD, date(2026, 10, 15), "  Auto admite demanda  ", None) == 1
    assert repo.simuladas == [(RAD, date(2026, 10, 15), "Auto admite demanda")]


class FuenteRota:
    async def consultar(self, radicado):
        raise RuntimeError("respuesta imposible")


async def test_el_ciclo_sigue_si_un_radicado_falla_y_pausa_entre_consultas_al_portal(caplog):
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA), RadicadoVigilado(OTRO, 2, Fuente.PORTAL_HTTP))
    s = ServicioVigilancia(repo, {Fuente.SIMULADA: FuenteRota(), Fuente.PORTAL_HTTP: FuenteFija(act("P:1", 1))},
                           Publicador(), pausa_portal_seg=0, ahora=lambda: AHORA)
    assert await s.ciclo() == 2
    assert "Error inesperado consultando" in caplog.text
    assert repo.datos[OTRO].linea_base_hecha


async def test_no_se_cruzan_dos_ciclos():
    s = ServicioVigilancia(RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA)), {Fuente.SIMULADA: FuenteFija()},
                           Publicador(), pausa_portal_seg=0, ahora=lambda: AHORA)
    async with s._en_curso:
        assert await s.ciclo() == 0


async def test_historial_lee_las_consultas_del_repositorio():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA))
    s = ServicioVigilancia(repo, {Fuente.SIMULADA: FuenteFija(Actuacion("S:1", date(2026, 10, 1), "Auto"))},
                           Publicador(), ahora=lambda: AHORA)
    await s.consultar(RAD)
    [(rad, exitosa, nuevas, detalle)] = await s.historial(RAD)
    assert rad == RAD and exitosa and nuevas == 0 and detalle["lineaBase"]


# ------------------------------------------------------------------ API

def test_salud_historial_y_sincronizar_por_http():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA))
    s = ServicioVigilancia(repo, {Fuente.SIMULADA: FuenteFija()}, Publicador(), ProcesosFijos((OTRO, 2)),
                           pausa_portal_seg=0)
    with TestClient(crear_app(servicio=s)) as c:
        assert c.get("/salud").json() == {"estado": "ok"}
        assert c.post(f"/vigilancia/radicados/{RAD}/consultar").json()["lineaBase"] is True
        assert len(c.get(f"/vigilancia/radicados/{RAD}/consultas").json()) == 1
        assert c.post("/vigilancia/sincronizar").json() == {"agregados": 1}
        assert c.get("/vigilancia/radicados/123/consultas").status_code == 422


# ------------------------------------------------------------------ tarea periódica

class ServicioGuionado:
    def __init__(self):
        self.sincronizaciones = [ConnectionError("procesos no está listo"), 2]
        self.ciclos = [1, RuntimeError("base caída"), 0]

    @staticmethod
    def _siguiente(lista):
        v = lista.pop(0)
        if isinstance(v, Exception):
            raise v
        return v

    async def sincronizar(self):
        return self._siguiente(self.sincronizaciones)

    async def ciclo(self):
        return self._siguiente(self.ciclos)


async def test_tarea_periodica_reintenta_sincronizar_y_sobrevive_a_un_ciclo_fallido(monkeypatch, caplog):
    pausas = []

    async def dormir(seg):
        pausas.append(seg)
        if len(pausas) == 4:
            raise asyncio.CancelledError

    monkeypatch.setattr(main, "asyncio", SimpleNamespace(sleep=dormir))
    srv = ServicioGuionado()
    with pytest.raises(asyncio.CancelledError):
        await main._tarea_periodica(srv, 10)
    assert pausas == [30, 600, 600, 600]
    assert srv.sincronizaciones == [] and srv.ciclos == []
    assert "reintento en 30 s" in caplog.text and "Error en el ciclo de vigilancia" in caplog.text


# ------------------------------------------------------------------ arranque con adaptadores falsos

async def para_siempre():
    await asyncio.Event().wait()


class Registro:
    pools, mensajerias, portales, procesos, relays, tareas = [], [], [], [], [], []


class PoolArranque:
    check_connection = "comprobar"

    def __init__(self, url, **kw):
        self.url, self.kw, self.estado = url, kw, "creado"
        Registro.pools.append(self)

    async def open(self):
        self.estado = "abierto"

    async def close(self):
        self.estado = "cerrado"


class MensajeriaArranque:
    def __init__(self, url, exchange):
        self.url, self.exchange, self.cerrada = url, exchange, False
        Registro.mensajerias.append(self)

    async def iniciar(self, proceso_creado):
        self.proceso_creado = proceso_creado
        await para_siempre()

    async def cerrar(self):
        self.cerrada = True


class PortalArranque:
    def __init__(self, url):
        self.url = url
        Registro.portales.append(self)


class ProcesosArranque:
    def __init__(self, url, token):
        self.url, self.token = url, token
        Registro.procesos.append(self)


class RelayArranque:
    def __init__(self, pool, mensajeria):
        Registro.relays.append((pool, mensajeria))

    async def ejecutar(self):
        await para_siempre()


async def tarea_periodica_falsa(srv, minutos):
    Registro.tareas.append(minutos)
    await para_siempre()


@pytest.fixture
def adaptadores_falsos(monkeypatch):
    for lista in ("pools", "mensajerias", "portales", "procesos", "relays", "tareas"):
        setattr(Registro, lista, [])
    monkeypatch.setattr(psycopg_pool, "AsyncConnectionPool", PoolArranque)
    monkeypatch.setattr(infra, "Mensajeria", MensajeriaArranque)
    monkeypatch.setattr(infra, "FuentePortalHttp", PortalArranque)
    monkeypatch.setattr(infra, "ClienteProcesos", ProcesosArranque)
    monkeypatch.setattr(infra, "RelayOutbox", RelayArranque)
    monkeypatch.setattr(main, "_tarea_periodica", tarea_periodica_falsa)
    for var in ("DB_URL", "PORTAL_URL", "RABBITMQ_URL", "EVENTOS_EXCHANGE", "PROCESOS_URL", "SERVICIO_CLAVE"):
        monkeypatch.delenv(var, raising=False)
    monkeypatch.setenv("PAUSA_PORTAL_SEG", "0")
    monkeypatch.setenv("CICLO_MINUTOS", "5")
    return monkeypatch


def app_real():
    return crear_app(verificador=VerificadorTokens("http://no-se-usa", llaves=LlavesFijas()))


def test_arranque_conecta_todo_y_lo_cierra_al_terminar(adaptadores_falsos):
    adaptadores_falsos.setenv("DB_URL", "postgresql://svc@db.test/gabinete")
    with TestClient(app_real()) as c:
        assert c.get("/vigilancia/fuentes").status_code == 401   # el token se exige sin dobles de servicio
        r = c.get("/vigilancia/fuentes", headers={"Authorization": f"Bearer {token()}"})
        assert r.json() == {"disponibles": ["MANUAL", "SIMULADA", "PORTAL_HTTP"]}
        srv = c.app.state.servicio
        assert srv.pausa_portal_seg == 0 and isinstance(srv.procesos, ProcesosArranque)

    [pool] = Registro.pools
    assert pool.url == "postgresql://svc@db.test/gabinete" and pool.estado == "cerrado"
    assert pool.kw == {"min_size": 1, "max_size": 4, "open": False, "check": "comprobar"}
    [mq] = Registro.mensajerias
    assert (mq.url, mq.exchange, mq.cerrada) == ("amqp://guest:guest@localhost/", "gabinete.eventos", True)
    assert Registro.portales[0].url == "https://consultaprocesos.ramajudicial.gov.co:448/api/v2"
    assert Registro.procesos[0].url == "http://localhost:8082" and repr(Registro.procesos[0].token) == \
        "TokenServicio(vigilancia)"
    assert Registro.relays == [(pool, mq)] and Registro.tareas == [5.0]


def test_arranque_sin_portal(adaptadores_falsos):
    adaptadores_falsos.setenv("PORTAL_HABILITADO", "false")
    with TestClient(app_real()) as c:
        r = c.get("/vigilancia/fuentes", headers={"Authorization": f"Bearer {token()}"})
        assert r.json() == {"disponibles": ["MANUAL", "SIMULADA"]}
    assert Registro.portales == []
