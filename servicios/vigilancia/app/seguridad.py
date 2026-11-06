"""Seguridad entre servicios (F9): verificar el token de cada petición y pedir el token de este servicio.

- El gateway ya verificó el token; este servicio lo verifica otra vez con la llave pública de identidad (JWKS),
  para no confiar en la red interna. La identidad sale del token: x-usuario-id, x-usuario-roles y x-cliente-id
  se reescriben con lo que dice el token (las que traiga la petición se descartan).
- Un servicio interno (rol SERVICIO) solo puede consultar (GET).
- Para llamar a otros servicios se usa un token propio (rol SERVICIO) que identidad entrega a cambio de la clave
  del servicio (infra/.env). Se reutiliza hasta un minuto antes de vencer.
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
import time
from dataclasses import dataclass

import httpx
import jwt

log = logging.getLogger(__name__)

CABECERAS_IDENTIDAD = (b"x-usuario-id", b"x-usuario-roles", b"x-cliente-id")


@dataclass(frozen=True)
class Identidad:
    sujeto: str
    roles: tuple[str, ...]
    id_cliente: int | None
    clave_temporal: bool

    @property
    def es_servicio(self) -> bool:
        return self.sujeto.startswith("servicio:") and "SERVICIO" in self.roles


class VerificadorTokens:
    """Firma RS256 con la llave pública de identidad, emisor y vencimiento obligatorios."""

    def __init__(self, jwks_url: str, emisor: str = "gestion-juridica", llaves: jwt.PyJWKClient | None = None):
        self.emisor = emisor
        self.llaves = llaves or jwt.PyJWKClient(jwks_url, cache_keys=True, lifespan=300, timeout=5)

    @staticmethod
    def desde_entorno() -> "VerificadorTokens":
        base = os.environ.get("IDENTIDAD_URL", "http://localhost:8005").rstrip("/")
        return VerificadorTokens(f"{base}/.well-known/jwks.json")

    def verificar(self, token: str | None) -> Identidad | None:
        if not token:
            return None
        try:
            llave = self.llaves.get_signing_key_from_jwt(token).key
            c = jwt.decode(token, llave, algorithms=["RS256"], issuer=self.emisor,
                           options={"require": ["exp", "iat", "sub", "jti", "iss"]})
        except Exception:   # noqa: BLE001 - token mal formado, firma ajena, vencido o JWKS caído: no se acepta
            return None
        cliente = c.get("idCliente")
        return Identidad(str(c["sub"]), tuple(sorted(c.get("roles") or [])),
                         int(cliente) if cliente is not None else None, bool(c.get("clave_temporal")))


class MiddlewareToken:
    """Middleware ASGI: sin token válido responde 401 (salvo las rutas públicas, como /salud)."""

    def __init__(self, app, verificador: VerificadorTokens, publicas: tuple[str, ...] = ("/salud",)):
        self.app = app
        self.verificador = verificador
        self.publicas = publicas

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope["path"] in self.publicas:
            await self.app(scope, receive, send)
            return
        cabeceras = [(k, v) for k, v in scope["headers"]]
        autorizacion = next((v.decode("latin-1") for k, v in cabeceras if k == b"authorization"), "")
        token = autorizacion[7:].strip() if autorizacion[:7].lower() == "bearer " else None
        # La verificación puede descargar el JWKS (llamada bloqueante): fuera del bucle de eventos.
        identidad = await asyncio.to_thread(self.verificador.verificar, token) if token else None
        if identidad is None:
            await _responder(send, 401, "Inicie sesión para continuar")
            return
        if identidad.es_servicio and scope["method"] not in ("GET", "HEAD"):
            await _responder(send, 403, "Un servicio interno solo puede consultar")
            return
        if identidad.clave_temporal:
            await _responder(send, 403, "Cambie su contraseña temporal antes de continuar")
            return
        propias = [(k, v) for k, v in cabeceras if k not in CABECERAS_IDENTIDAD]
        if not identidad.es_servicio:
            propias.append((b"x-usuario-id", identidad.sujeto.encode()))
        propias.append((b"x-usuario-roles", ",".join(identidad.roles).encode()))
        if identidad.id_cliente is not None:
            propias.append((b"x-cliente-id", str(identidad.id_cliente).encode()))
        await self.app(dict(scope, headers=propias), receive, send)


async def _responder(send, estado: int, mensaje: str) -> None:
    cuerpo = json.dumps({"error": mensaje}, ensure_ascii=False).encode()
    cabeceras = [(b"content-type", b"application/json"), (b"content-length", str(len(cuerpo)).encode())]
    if estado == 401:
        cabeceras.append((b"www-authenticate", b"Bearer"))
    await send({"type": "http.response.start", "status": estado, "headers": cabeceras})
    await send({"type": "http.response.body", "body": cuerpo})


class TokenServicio:
    """Token de este servicio para llamar a otros. Sin clave (desarrollo, pruebas) las llamadas salen sin token."""

    def __init__(self, identidad_url: str, nombre: str, clave: str):
        self.url = identidad_url.rstrip("/")
        self.nombre = nombre
        self._clave = clave
        self._token: str | None = None
        self._vence = 0.0
        self._candado = asyncio.Lock()

    @staticmethod
    def desde_entorno(nombre: str) -> "TokenServicio":
        return TokenServicio(os.environ.get("IDENTIDAD_URL", "http://localhost:8005"), nombre,
                             os.environ.get("SERVICIO_CLAVE", "").strip())

    def __repr__(self) -> str:   # nunca muestra la clave
        return f"TokenServicio({self.nombre})"

    async def cabeceras(self) -> dict[str, str]:
        token = await self.vigente()
        return {"Authorization": f"Bearer {token}"} if token else {}

    async def vigente(self) -> str | None:
        if not self._clave:
            return None
        async with self._candado:
            if self._token and time.time() < self._vence - 60:
                return self._token
            try:
                async with httpx.AsyncClient(timeout=5) as http:
                    r = await http.post(f"{self.url}/interno/token", json={"servicio": self.nombre, "clave": self._clave})
                    r.raise_for_status()
                    self._token = r.json()["token"]
                    self._vence = time.time() + 15 * 60
                    try:
                        self._vence = jwt.decode(self._token, options={"verify_signature": False})["exp"]
                    except Exception:   # noqa: BLE001
                        pass
            except Exception as e:   # noqa: BLE001
                log.warning("No se pudo obtener el token del servicio %s: %s", self.nombre, e)
            return self._token


async def sin_token() -> dict[str, str]:
    """Para los clientes HTTP sin TokenServicio (pruebas)."""
    return {}
