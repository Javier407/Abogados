"""Adaptadores: Ollama (modelo local) y los servicios procesos y gabinete."""
from __future__ import annotations

import json
import logging
from typing import AsyncIterator

import httpx

log = logging.getLogger(__name__)


class ModeloOllama:
    """Modelo local servido por Ollama (sin GPU: unas decenas de palabras por segundo como mucho)."""

    def __init__(self, url: str, nombre: str, cliente: httpx.AsyncClient | None = None,
                 max_tokens: int = 1500, temperatura: float = 0.3):
        self.url = url.rstrip("/")
        self.nombre = nombre
        self.max_tokens = max_tokens
        self.temperatura = temperatura
        # La redacción en CPU puede tardar minutos: sin límite para leer, pero sí para conectar.
        self.cliente = cliente or httpx.AsyncClient(timeout=httpx.Timeout(connect=5, read=None, write=30, pool=10))

    async def disponible(self) -> tuple[bool, str]:
        try:
            r = await self.cliente.get(f"{self.url}/api/tags", timeout=5)
            r.raise_for_status()
        except httpx.HTTPError as e:
            return False, f"Ollama no responde: {type(e).__name__}"
        modelos = {m.get("name") for m in r.json().get("models", [])}
        if self.nombre not in modelos and f"{self.nombre}:latest" not in modelos:
            return False, f"el modelo {self.nombre} no está descargado"
        return True, "listo"

    async def redactar(self, conversacion: list[dict]) -> AsyncIterator[str]:
        cuerpo = {"model": self.nombre, "messages": conversacion, "stream": True,
                  "options": {"temperature": self.temperatura, "num_predict": self.max_tokens, "num_ctx": 4096}}
        async with self.cliente.stream("POST", f"{self.url}/api/chat", json=cuerpo) as r:
            r.raise_for_status()
            async for linea in r.aiter_lines():
                if not linea.strip():
                    continue
                dato = json.loads(linea)
                if dato.get("error"):
                    raise RuntimeError(f"Ollama: {dato['error']}")
                texto = (dato.get("message") or {}).get("content") or ""
                if texto:
                    yield texto
                if dato.get("done"):
                    break


class ClienteProcesos:
    def __init__(self, url: str):
        self.url = url.rstrip("/")

    async def _get(self, ruta: str, params: dict | None = None):
        async with httpx.AsyncClient(timeout=10) as http:
            r = await http.get(f"{self.url}{ruta}", params=params)
            if r.status_code == 404:
                return None
            r.raise_for_status()
            return r.json()

    async def proceso(self, id_proceso: int) -> dict | None:
        return await self._get(f"/procesos/{id_proceso}")

    async def providencias(self, id_proceso: int) -> list[dict]:
        return await self._get(f"/procesos/{id_proceso}/providencias") or []

    async def terminos(self, id_proceso: int) -> list[dict]:
        return await self._get("/terminos", {"idProceso": id_proceso}) or []


class ClienteGabinete:
    def __init__(self, url: str):
        self.url = url.rstrip("/")

    async def _get(self, ruta: str):
        async with httpx.AsyncClient(timeout=10) as http:
            r = await http.get(f"{self.url}{ruta}")
            if r.status_code == 404:
                return None
            r.raise_for_status()
            return r.json()

    async def cliente(self, id_cliente: int) -> dict | None:
        return await self._get(f"/clientes/{id_cliente}")

    async def abogado(self, id_persona: int) -> dict | None:
        return await self._get(f"/abogados/{id_persona}")
