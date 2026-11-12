"""Toga por WhatsApp, Instagram y otros canales que atiende Talki (docs/16-integracion-talki.md).

Talki recibe el mensaje y llama a Lexora por la red interna del servidor con una clave compartida. Lexora
identifica a la persona por (canal, id externo) y responde con el mismo motor y las mismas reglas que Telegram y
el portal (ConsultasBot): solo personal activo, solo lo suyo, solo lectura.

Vincular: la persona genera en el portal → Avisos un código de un uso (24 horas) y escribe «vincular <código>» en
el chat. Los intentos por chat están limitados para que nadie adivine códigos.
"""
from __future__ import annotations

import logging
import re
import secrets
import time
from collections import defaultdict, deque
from typing import Callable, Protocol

log = logging.getLogger(__name__)

CANALES = ("WHATSAPP", "INSTAGRAM")
NOMBRES = {"WHATSAPP": "WhatsApp", "INSTAGRAM": "Instagram"}
# Sin letras ni números que se confunden al copiarlos (0/O, 1/I/L).
ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
LARGO_CODIGO = 8
INTENTOS_POR_HORA = 5

SIN_VINCULO = ("Este chat no está vinculado a Lexora. Si trabaja en la firma, entre al portal → Avisos → "
               "Vincular {canal} y escriba aquí «vincular» seguido del código que le aparece.")


class RepositorioCanales(Protocol):
    async def crear_vinculo_canal(self, codigo: str, id_persona: int, canal: str) -> None: ...
    async def usar_vinculo_canal(self, codigo: str, canal: str) -> int | None: ...   # id_persona si sirve
    async def guardar_canal(self, id_persona: int, canal: str, id_externo: str) -> None: ...
    async def persona_de_canal(self, canal: str, id_externo: str) -> int | None: ...
    async def canales_de(self, id_persona: int) -> list[str]: ...
    async def quitar_canal(self, id_persona: int, canal: str) -> bool: ...
    async def registrar_consulta_bot(self, id_persona: int | None, chat_id: str, comando: str, resultado: str) -> None: ...


class Toga(Protocol):
    async def responder_externo(self, id_persona: int, chat_id: str, texto: str) -> str: ...


def chat_id(canal: str, id_externo: str) -> str:
    """Cómo queda el chat en el registro de consultas (columna de 40 caracteres)."""
    return f"{canal.lower()}:{id_externo}"[:40]


def normalizar_codigo(codigo: str) -> str:
    return re.sub(r"[^A-Z0-9]", "", (codigo or "").upper())


class CanalesExternos:
    def __init__(self, repo: RepositorioCanales, toga: Toga, reloj: Callable[[], float] = time.monotonic,
                 intentos_por_hora: int = INTENTOS_POR_HORA):
        self.repo = repo
        self.toga = toga
        self.reloj = reloj
        self.intentos_por_hora = intentos_por_hora
        self._intentos: dict[tuple[str, str], deque[float]] = defaultdict(deque)

    # -------------------------------------------------------------- portal

    async def estado(self, id_persona: int) -> list[dict]:
        vinculados = set(await self.repo.canales_de(id_persona))
        return [{"canal": c, "nombre": NOMBRES[c], "vinculado": c in vinculados} for c in CANALES]

    async def generar_vinculo(self, id_persona: int, canal: str) -> dict:
        codigo = "".join(secrets.choice(ALFABETO) for _ in range(LARGO_CODIGO))
        await self.repo.crear_vinculo_canal(codigo, id_persona, canal)
        return {"canal": canal, "nombre": NOMBRES[canal], "codigo": codigo, "mensaje": f"vincular {codigo}"}

    async def desvincular(self, id_persona: int, canal: str) -> bool:
        return await self.repo.quitar_canal(id_persona, canal)

    # -------------------------------------------------------------- Talki

    async def vincular(self, canal: str, id_externo: str, codigo: str) -> dict:
        chat = chat_id(canal, id_externo)
        if not self._dentro_del_limite(canal, id_externo):
            await self._registrar(None, chat, "LIMITE")
            return {"vinculado": False,
                    "mensaje": "Hubo demasiados intentos desde este chat. Espere una hora y genere un código nuevo."}
        id_persona = await self.repo.usar_vinculo_canal(normalizar_codigo(codigo), canal)
        if id_persona is None:
            await self._registrar(None, chat, "NO_ENCONTRADO")
            return {"vinculado": False,
                    "mensaje": "Ese código no sirve o ya venció. Genere otro en el portal → Avisos."}
        await self.repo.guardar_canal(id_persona, canal, id_externo)
        await self._registrar(id_persona, chat, "RESPONDIDA")
        log.info("%s vinculado para la persona %s", NOMBRES[canal], id_persona)
        return {"vinculado": True,
                "mensaje": "Listo: este chat quedó vinculado a Lexora. Pregúnteme por sus procesos, sus términos o "
                           "sus audiencias, o escríbame el radicado de un proceso suyo."}

    async def preguntar(self, canal: str, id_externo: str, texto: str) -> dict:
        chat = chat_id(canal, id_externo)
        id_persona = await self.repo.persona_de_canal(canal, id_externo)
        if id_persona is None:
            await self._registrar(None, chat, "SIN_VINCULO", "consulta")
            return {"vinculado": False, "respuesta": SIN_VINCULO.format(canal=NOMBRES[canal])}
        return {"vinculado": True, "respuesta": await self.toga.responder_externo(id_persona, chat, texto)}

    # -------------------------------------------------------------- apoyo

    def _dentro_del_limite(self, canal: str, id_externo: str) -> bool:
        ahora = self.reloj()
        ventana = self._intentos[(canal, id_externo)]
        while ventana and ahora - ventana[0] > 3600:
            ventana.popleft()
        if len(ventana) >= self.intentos_por_hora:
            return False
        ventana.append(ahora)
        return True

    async def _registrar(self, id_persona: int | None, chat: str, resultado: str, comando: str = "vincular") -> None:
        try:
            await self.repo.registrar_consulta_bot(id_persona, chat, comando, resultado)
        except Exception:   # noqa: BLE001 - el registro no debe impedir la respuesta
            log.exception("No se pudo registrar el intento de vínculo")
