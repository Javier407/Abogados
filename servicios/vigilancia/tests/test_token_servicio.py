"""F9: token propio del servicio (pedido a identidad simulada) y configuración desde el entorno."""
import json
import time
from types import SimpleNamespace

import httpx
import jwt

from app import seguridad
from app.seguridad import TokenServicio, VerificadorTokens, sin_token
from tests.test_seguridad import LLAVE


def identidad_falsa(monkeypatch, *respuestas):
    """Cada llamada a /interno/token devuelve la siguiente respuesta preparada."""
    pedidas = []
    original = httpx.AsyncClient

    def manejador(req: httpx.Request):
        pedidas.append((str(req.url), req.read()))
        return respuestas[min(len(pedidas), len(respuestas)) - 1]

    monkeypatch.setattr(seguridad, "httpx", SimpleNamespace(
        AsyncClient=lambda **kw: original(transport=httpx.MockTransport(manejador), **kw)))
    return pedidas


def jwt_que_vence_en(segundos: int) -> str:
    return jwt.encode({"sub": "servicio:vigilancia", "exp": int(time.time()) + segundos}, LLAVE, algorithm="RS256")


def test_desde_entorno(monkeypatch):
    monkeypatch.setenv("IDENTIDAD_URL", "http://identidad.test:8005/")
    monkeypatch.setenv("SERVICIO_CLAVE", "  secreta  ")
    assert VerificadorTokens.desde_entorno().llaves.uri == "http://identidad.test:8005/.well-known/jwks.json"
    t = TokenServicio.desde_entorno("vigilancia")
    assert t.url == "http://identidad.test:8005" and t._clave == "secreta"
    assert "secreta" not in repr(t)


def test_sin_token_no_verifica():
    assert VerificadorTokens("http://no-se-usa", llaves=object()).verificar("") is None


async def test_sin_clave_las_llamadas_salen_sin_token(monkeypatch):
    pedidas = identidad_falsa(monkeypatch, httpx.Response(500))
    t = TokenServicio("http://identidad.test", "vigilancia", "")
    assert await t.cabeceras() == {} and await sin_token() == {}
    assert pedidas == []


async def test_pide_el_token_una_vez_y_lo_reutiliza_hasta_que_va_a_vencer(monkeypatch):
    primero, segundo = jwt_que_vence_en(3600), jwt_que_vence_en(3600)
    pedidas = identidad_falsa(monkeypatch, httpx.Response(200, json={"token": primero}),
                              httpx.Response(200, json={"token": segundo}))
    t = TokenServicio("http://identidad.test/", "vigilancia", "secreta")

    assert await t.cabeceras() == {"Authorization": f"Bearer {primero}"}
    assert await t.vigente() == primero
    assert len(pedidas) == 1
    url, cuerpo = pedidas[0]
    assert url == "http://identidad.test/interno/token"
    assert json.loads(cuerpo) == {"servicio": "vigilancia", "clave": "secreta"}

    t._vence = time.time() + 30   # a menos de un minuto de vencer: se renueva
    assert await t.vigente() == segundo and len(pedidas) == 2


async def test_token_opaco_vale_quince_minutos(monkeypatch):
    identidad_falsa(monkeypatch, httpx.Response(200, json={"token": "opaco"}))
    t = TokenServicio("http://identidad.test", "vigilancia", "secreta")
    antes = time.time()
    assert await t.vigente() == "opaco"
    assert antes + 15 * 60 - 1 <= t._vence <= time.time() + 15 * 60


async def test_identidad_caida_deja_las_llamadas_sin_token(monkeypatch, caplog):
    pedidas = identidad_falsa(monkeypatch, httpx.Response(503))
    t = TokenServicio("http://identidad.test", "vigilancia", "secreta")
    assert await t.cabeceras() == {}
    assert len(pedidas) == 1 and "No se pudo obtener el token del servicio vigilancia" in caplog.text
    assert "secreta" not in caplog.text
