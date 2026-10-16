"""Caso de uso del asistente: armar el caso desde los otros servicios y redactar el borrador por partes."""
from __future__ import annotations

import logging
import time
from datetime import date
from typing import AsyncIterator, Protocol

from .dominio import Actuacion, Caso, TipoBorrador, encabezado, firma, mensajes, plantilla, validar

log = logging.getLogger(__name__)


class ModeloLenguaje(Protocol):
    nombre: str
    async def disponible(self) -> tuple[bool, str]: ...
    def redactar(self, conversacion: list[dict]) -> AsyncIterator[str]: ...


class Procesos(Protocol):
    async def proceso(self, id_proceso: int) -> dict | None: ...
    async def providencias(self, id_proceso: int) -> list[dict]: ...
    async def terminos(self, id_proceso: int) -> list[dict]: ...


class Gabinete(Protocol):
    async def cliente(self, id_cliente: int) -> dict | None: ...
    async def abogado(self, id_persona: int) -> dict | None: ...


class ProcesoNoEncontrado(LookupError):
    pass


def _pesos(valor) -> str | None:
    if valor is None:
        return None
    return "$" + f"{float(valor):,.0f}".replace(",", ".")


class ServicioAsistente:
    def __init__(self, modelo: ModeloLenguaje, procesos: Procesos, gabinete: Gabinete):
        self.modelo = modelo
        self.procesos = procesos
        self.gabinete = gabinete

    async def estado(self) -> dict:
        listo, detalle = await self.modelo.disponible()
        return {"modelo": self.modelo.nombre, "disponible": listo, "detalle": detalle}

    async def caso(self, id_proceso: int) -> Caso:
        p = await self.procesos.proceso(id_proceso)
        if p is None:
            raise ProcesoNoEncontrado(f"No existe el proceso {id_proceso}")
        cliente = await self.gabinete.cliente(p["idCliente"]) or {}
        abogado = await self.gabinete.abogado(p["idResponsable"]) if p.get("idResponsable") else None
        abogado = abogado or {}
        providencias = await self.procesos.providencias(id_proceso)
        terminos = await self.procesos.terminos(id_proceso)
        return Caso(
            radicado_legible=p["radicadoLegible"], rama=p["rama"], tipo_proceso=p["tipoProceso"],
            despacho=p["despachoActual"], asunto=p["asunto"], etapa=p["etapa"], estado=p["estado"],
            rol_cliente=p["rolCliente"], cliente=cliente.get("nombre", "[COMPLETAR: cliente]"),
            abogado=(f"{abogado.get('nombres', '')} {abogado.get('apellidos', '')}".strip()
                     or "[COMPLETAR: abogado]"),
            tarjeta_profesional=abogado.get("tarjetaProfesional", "[COMPLETAR]"),
            cuantia=_pesos(p.get("cuantia")),
            actuaciones=[Actuacion(date.fromisoformat(x["fechaEstado"]), x["tipo"], x["clase"], x.get("decision") or "")
                         for x in providencias],
            terminos_pendientes=[f"{t['descripcion']} (vence {date.fromisoformat(t['fechaVencimiento']).strftime('%d/%m/%Y')})"
                                 for t in terminos if t.get("estado") == "PENDIENTE"])

    async def preparar(self, tipo: TipoBorrador, id_proceso: int) -> Caso:
        """Arma y valida el caso antes de empezar a responder (los errores salen como 404/422 normales)."""
        caso = await self.caso(id_proceso)
        validar(tipo, caso)
        return caso

    async def redactar(self, tipo: TipoBorrador, caso: Caso, instrucciones: str = "") -> AsyncIterator[dict]:
        """
        Eventos: inicio → texto (encabezado) → texto... (lo que redacta el modelo) → texto (firma) → fin.
        Si el modelo no está o falla a mitad, se avisa y se entrega la plantilla.
        """
        listo, detalle = await self.modelo.disponible()
        yield {"evento": "inicio", "modelo": self.modelo.nombre if listo else None, "tipo": tipo.value}
        if not listo:
            yield {"evento": "aviso", "mensaje": f"El modelo no está disponible ({detalle}); se entrega la plantilla."}
            yield {"evento": "texto", "texto": plantilla(tipo, caso)}
            yield {"evento": "fin", "conModelo": False}
            return

        yield {"evento": "texto", "texto": encabezado(tipo, caso)}
        inicio = time.monotonic()
        escrito = 0
        try:
            async for trozo in self.modelo.redactar(mensajes(tipo, caso, instrucciones)):
                escrito += len(trozo)
                yield {"evento": "texto", "texto": trozo}
        except Exception as e:   # noqa: BLE001 - el abogado igual recibe algo útil
            log.exception("El modelo falló redactando")
            yield {"evento": "aviso", "mensaje": f"El modelo se interrumpió ({type(e).__name__}); "
                                                 "complete el borrador o vuelva a intentarlo."}
            if escrito == 0:
                yield {"evento": "texto", "texto": plantilla(tipo, caso)[len(encabezado(tipo, caso)):]}
                yield {"evento": "fin", "conModelo": False}
                return
        yield {"evento": "texto", "texto": firma(tipo, caso)}
        segundos = round(time.monotonic() - inicio)
        log.info("Borrador %s redactado en %s s (%s caracteres)", tipo.value, segundos, escrito)
        yield {"evento": "fin", "conModelo": True, "segundos": segundos}
