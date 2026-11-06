"""Reglas de la vigilancia: qué radicados consultar, qué actuaciones son nuevas y cómo se clasifican.

No depende de FastAPI, de la base de datos, de RabbitMQ ni del portal: se prueba con datos en memoria.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from enum import Enum
from typing import Protocol

FALLOS_PARA_ALERTA = 3


class Fuente(str, Enum):
    MANUAL = "MANUAL"            # el dependiente registra las actuaciones; no se consulta nada
    SIMULADA = "SIMULADA"        # actuaciones escritas a mano para demostraciones y pruebas
    PORTAL_HTTP = "PORTAL_HTTP"  # Consulta de Procesos de la Rama Judicial (solo radicados reales)

    @property
    def automatica(self) -> bool:
        return self in (Fuente.SIMULADA, Fuente.PORTAL_HTTP)


class ErrorFuente(Exception):
    """La fuente no respondió o respondió algo inesperado; cuenta como fallo de la consulta."""


@dataclass(frozen=True)
class Actuacion:
    """Una actuación del expediente tal como la publica la fuente."""
    referencia: str          # identificador estable en la fuente, p. ej. PORTAL:1721903412
    fecha: date
    actuacion: str           # p. ej. "Auto admite demanda", "Fijación estado"
    anotacion: str = ""
    fecha_registro: date | None = None

    @property
    def tipo(self) -> str:
        """AUTO o SENTENCIA para el expediente; el resto de actuaciones se registran como AUTO de trámite."""
        texto = f"{self.actuacion} {self.anotacion}".upper()
        return "SENTENCIA" if "SENTENCIA" in texto else "AUTO"


@dataclass(frozen=True)
class ResultadoConsulta:
    encontrado: bool
    actuaciones: list[Actuacion] = field(default_factory=list)
    despacho: str | None = None


@dataclass
class RadicadoVigilado:
    radicado: str
    id_proceso: int
    fuente: Fuente
    frecuencia_horas: int = 24
    activo: bool = True
    ultima_consulta: datetime | None = None
    fallos_consecutivos: int = 0
    linea_base_hecha: bool = False
    despacho: str | None = None
    ultima_actuacion: date | None = None
    ultimo_error: str | None = None

    def toca_consultar(self, ahora: datetime) -> bool:
        if not self.activo or not self.fuente.automatica:
            return False
        if self.ultima_consulta is None:
            return True
        return ahora - self.ultima_consulta >= timedelta(hours=self.frecuencia_horas)

    @property
    def en_alerta(self) -> bool:
        return self.fallos_consecutivos >= FALLOS_PARA_ALERTA


class FuenteRamaJudicial(Protocol):
    """ADR 0005: cualquier forma de obtener las actuaciones de un radicado."""
    async def consultar(self, radicado: str) -> ResultadoConsulta: ...


def nuevas(consultadas: list[Actuacion], conocidas: set[str]) -> list[Actuacion]:
    """Las que no se habían visto, de la más antigua a la más reciente (orden en que ocurrieron)."""
    vistas: set[str] = set()
    resultado = []
    for a in consultadas:
        if a.referencia not in conocidas and a.referencia not in vistas:
            vistas.add(a.referencia)
            resultado.append(a)
    return sorted(resultado, key=lambda a: (a.fecha, a.referencia))


def validar_radicado(radicado: str) -> str:
    r = (radicado or "").replace("-", "").replace(" ", "").replace(".", "")
    if len(r) != 23 or not r.isdigit():
        raise ValueError("El radicado debe tener 23 dígitos")
    return r
