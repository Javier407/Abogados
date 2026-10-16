"""Casos de uso de la vigilancia: registrar radicados, consultarlos y publicar las actuaciones nuevas."""
from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone
from typing import Callable, Protocol

from .dominio import (Actuacion, ErrorFuente, Fuente, FuenteRamaJudicial, RadicadoVigilado, ResultadoConsulta,
                      nuevas)

log = logging.getLogger(__name__)


class Repositorio(Protocol):
    async def radicados(self) -> list[RadicadoVigilado]: ...
    async def radicado(self, radicado: str) -> RadicadoVigilado | None: ...
    async def registrar(self, radicado: str, id_proceso: int, fuente: Fuente) -> bool: ...   # False si ya existía
    async def configurar(self, radicado: str, fuente: Fuente, frecuencia_horas: int, activo: bool) -> None: ...
    async def referencias_conocidas(self, radicado: str) -> set[str]: ...
    async def guardar_conocidas(self, radicado: str, actuaciones: list[Actuacion], avisadas: bool) -> None: ...
    async def registrar_consulta(self, radicado: str, exitosa: bool, nuevas: int, detalle: dict,
                                 despacho: str | None = None, ultima_actuacion=None) -> None: ...
    """Una consulta exitosa deja hecha la línea base y pone en cero los fallos."""
    async def consultas(self, radicado: str, limite: int) -> list[dict]: ...
    async def agregar_simulada(self, radicado: str, fecha, actuacion: str, anotacion: str) -> int: ...


class Publicador(Protocol):
    async def actuacion_detectada(self, radicado: RadicadoVigilado, a: Actuacion, fuente: Fuente) -> None: ...


class Procesos(Protocol):
    async def vigilados(self) -> list[tuple[str, int]]: ...   # (radicado, id_proceso) de procesos con vigilado=true


class ServicioVigilancia:
    def __init__(self, repo: Repositorio, fuentes: dict[Fuente, FuenteRamaJudicial], publicador: Publicador,
                 procesos: Procesos | None = None, pausa_portal_seg: float = 10.0,
                 ahora: Callable[[], datetime] = lambda: datetime.now(timezone.utc)):
        self.repo = repo
        self.fuentes = fuentes
        self.publicador = publicador
        self.procesos = procesos
        self.pausa_portal_seg = pausa_portal_seg
        self.ahora = ahora
        self._en_curso = asyncio.Lock()

    # -------------------------------------------------------------- radicados

    async def proceso_creado(self, evento: dict) -> bool:
        """Evento proceso.creado: se vigila con fuente MANUAL hasta que alguien elija otra (opt-in)."""
        return await self.repo.registrar(str(evento["radicado"]), int(evento["id_proceso"]), Fuente.MANUAL)

    async def sincronizar(self) -> int:
        """Registra los procesos vigilados que aún no están (p. ej. creados antes de existir este servicio)."""
        if self.procesos is None:
            return 0
        agregados = 0
        for radicado, id_proceso in await self.procesos.vigilados():
            if await self.repo.registrar(radicado, id_proceso, Fuente.MANUAL):
                agregados += 1
        return agregados

    async def configurar(self, radicado: str, fuente: Fuente, frecuencia_horas: int, activo: bool) -> RadicadoVigilado:
        if await self.repo.radicado(radicado) is None:
            raise LookupError(f"El radicado {radicado} no está en vigilancia")
        if fuente not in self.fuentes and fuente != Fuente.MANUAL:
            raise ValueError(f"La fuente {fuente.value} no está disponible en este servidor")
        if not 1 <= frecuencia_horas <= 168:
            raise ValueError("La frecuencia debe estar entre 1 y 168 horas")
        if fuente == Fuente.PORTAL_HTTP and frecuencia_horas < 12:
            raise ValueError("El portal de la Rama Judicial se consulta como máximo cada 12 horas por radicado")
        await self.repo.configurar(radicado, fuente, frecuencia_horas, activo)
        return await self.repo.radicado(radicado)

    async def agregar_simulada(self, radicado: str, fecha, actuacion: str, anotacion: str) -> int:
        rv = await self.repo.radicado(radicado)
        if rv is None:
            raise LookupError(f"El radicado {radicado} no está en vigilancia")
        if rv.fuente != Fuente.SIMULADA:
            raise ValueError("Solo se agregan actuaciones simuladas a radicados con fuente SIMULADA")
        if not actuacion.strip():
            raise ValueError("La actuación es obligatoria")
        return await self.repo.agregar_simulada(radicado, fecha, actuacion.strip()[:200], (anotacion or "").strip()[:1000])

    # -------------------------------------------------------------- consultas

    async def consultar(self, radicado: str) -> dict:
        """Consulta un radicado ya y publica actuacion.detectada por cada actuación nueva."""
        rv = await self.repo.radicado(radicado)
        if rv is None:
            raise LookupError(f"El radicado {radicado} no está en vigilancia")
        fuente = self.fuentes.get(rv.fuente)
        if fuente is None:
            raise ValueError(f"El radicado tiene fuente {rv.fuente.value}: no se consulta automáticamente")

        try:
            resultado: ResultadoConsulta = await fuente.consultar(rv.radicado)
        except ErrorFuente as e:
            await self.repo.registrar_consulta(rv.radicado, False, 0, {"error": str(e)[:300]}, None)
            log.warning("Consulta fallida de %s (%s): %s", rv.radicado, rv.fuente.value, e)
            return {"radicado": rv.radicado, "exitosa": False, "error": str(e)}

        if not resultado.encontrado:
            detalle = {"error": "El radicado no aparece en la fuente (¿privado o mal escrito?)"}
            await self.repo.registrar_consulta(rv.radicado, False, 0, detalle, None)
            return {"radicado": rv.radicado, "exitosa": False, "error": detalle["error"]}

        conocidas = await self.repo.referencias_conocidas(rv.radicado)
        detectadas = nuevas(resultado.actuaciones, conocidas)
        # Primera consulta exitosa con esta fuente: es la línea base. Se guarda el historial sin avisar,
        # para no llenar de avisos al abogado con actuaciones de hace meses.
        linea_base = not rv.linea_base_hecha
        if not linea_base:
            # Primero se publica y después se marca como conocida: si la mensajería falla, la actuación
            # se vuelve a detectar en la próxima consulta (los consumidores ignoran las repetidas).
            try:
                for a in detectadas:
                    await self.publicador.actuacion_detectada(rv, a, rv.fuente)
            except Exception as e:   # noqa: BLE001
                await self.repo.registrar_consulta(rv.radicado, False, 0,
                                                   {"error": f"No se pudo publicar el aviso: {e}"[:300]})
                log.error("No se pudo publicar actuacion.detectada de %s: %s", rv.radicado, e)
                return {"radicado": rv.radicado, "exitosa": False, "error": "No se pudo publicar el aviso"}
        await self.repo.guardar_conocidas(rv.radicado, detectadas, avisadas=not linea_base)

        ultima = max((a.fecha for a in resultado.actuaciones), default=None)
        publicadas = 0 if linea_base else len(detectadas)
        detalle = {"actuaciones": len(resultado.actuaciones), "nuevas": publicadas, "lineaBase": linea_base}
        if publicadas:
            detalle["ultima"] = detectadas[-1].actuacion
        await self.repo.registrar_consulta(rv.radicado, True, publicadas, detalle, resultado.despacho, ultima)
        log.info("Consulta de %s: %s actuaciones, %s nuevas%s", rv.radicado, len(resultado.actuaciones),
                 publicadas, " (línea base)" if linea_base else "")
        return {"radicado": rv.radicado, "exitosa": True, "actuaciones": len(resultado.actuaciones),
                "nuevas": publicadas, "lineaBase": linea_base}

    async def ciclo(self) -> int:
        """Consulta los radicados a los que les toca. Uno a la vez y con pausa entre consultas al portal."""
        if self._en_curso.locked():
            return 0
        async with self._en_curso:
            hechas = 0
            for rv in await self.repo.radicados():
                if not rv.toca_consultar(self.ahora()) or rv.fuente not in self.fuentes:
                    continue
                try:
                    await self.consultar(rv.radicado)
                except Exception:   # noqa: BLE001 - un radicado con problemas no detiene a los demás
                    log.exception("Error inesperado consultando %s", rv.radicado)
                hechas += 1
                if rv.fuente == Fuente.PORTAL_HTTP:
                    await asyncio.sleep(self.pausa_portal_seg)
            return hechas

    async def radicados(self) -> list[RadicadoVigilado]:
        return await self.repo.radicados()

    async def historial(self, radicado: str, limite: int = 20) -> list[dict]:
        return await self.repo.consultas(radicado, limite)
