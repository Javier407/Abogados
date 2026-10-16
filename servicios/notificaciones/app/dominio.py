"""Reglas de los avisos: qué evento se acepta, por qué canales se avisa y qué dice el aviso.

No depende de FastAPI, de la base de datos ni de RabbitMQ: se prueba con datos en memoria.
"""
from __future__ import annotations

import html
from dataclasses import dataclass, field
from datetime import date, datetime
from enum import Enum

EVENTOS_TERMINO = ("termino.por_vencer", "termino.vencido")
LARGO_MENSAJE = 500


class Canal(str, Enum):
    APP = "APP"
    EMAIL = "EMAIL"
    TELEGRAM = "TELEGRAM"   # F3


class EventoInvalido(ValueError):
    """El mensaje no cumple el contrato (docs/contratos/eventos.md); se descarta sin reintentar."""


@dataclass(frozen=True)
class AvisoTermino:
    """Eventos termino.por_vencer y termino.vencido que publica el servicio procesos."""
    evento: str
    id_termino: int
    id_proceso: int
    radicado: str
    descripcion: str
    vence: date
    dias_habiles_restantes: int
    id_abogado: int

    @property
    def vencido(self) -> bool:
        return self.evento == "termino.vencido" or self.dias_habiles_restantes < 0

    @staticmethod
    def desde_mensaje(evento: str, cuerpo: dict) -> "AvisoTermino":
        if evento not in EVENTOS_TERMINO:
            raise EventoInvalido(f"Evento no soportado: {evento}")
        try:
            return AvisoTermino(
                evento=evento,
                id_termino=int(cuerpo["id_termino"]),
                id_proceso=int(cuerpo["id_proceso"]),
                radicado=str(cuerpo["radicado"]),
                descripcion=str(cuerpo["descripcion"]),
                vence=date.fromisoformat(cuerpo["vence"]),
                dias_habiles_restantes=int(cuerpo["dias_habiles_restantes"]),
                id_abogado=int(cuerpo["id_abogado"]),
            )
        except (KeyError, TypeError, ValueError) as e:
            raise EventoInvalido(f"Mensaje {evento} incompleto o mal formado: {e}") from e


@dataclass(frozen=True)
class Preferencia:
    canal: Canal
    destino: str | None
    activo: bool = True


@dataclass(frozen=True)
class NotificacionNueva:
    id_persona: int
    canal: Canal
    evento: str
    titulo: str
    mensaje: str
    clave: str
    destino: str | None = None
    referencia: dict = field(default_factory=dict)


def radicado_legible(radicado: str) -> str:
    """11001310303220240045800 -> 11001-31-03-032-2024-00458-00"""
    r = radicado.strip()
    if len(r) != 23 or not r.isdigit():
        return r
    return "-".join((r[0:5], r[5:7], r[7:9], r[9:12], r[12:16], r[16:21], r[21:23]))


def _dias(n: int) -> str:
    return f"{n} día hábil" if n == 1 else f"{n} días hábiles"


def redactar(aviso: AvisoTermino) -> tuple[str, str]:
    """Título y mensaje del aviso, en español y con la fecha en formato colombiano."""
    vence = aviso.vence.strftime("%d/%m/%Y")
    proceso = radicado_legible(aviso.radicado)
    if aviso.vencido:
        titulo = "Término vencido"
        cola = f"venció el {vence}"
    elif aviso.dias_habiles_restantes == 0:
        titulo = "El término vence hoy"
        cola = f"vence hoy {vence}"
    else:
        titulo = "Término por vencer"
        n = aviso.dias_habiles_restantes
        cola = f"vence el {vence} ({'falta' if n == 1 else 'faltan'} {_dias(n)})"
    mensaje = f"{aviso.descripcion} — proceso {proceso} — {cola}"
    return titulo, mensaje[:LARGO_MENSAJE]


def avisos_para(aviso: AvisoTermino, preferencias: list[Preferencia], correo_respaldo: str | None,
                hoy: date) -> list[NotificacionNueva]:
    """
    Por qué canales avisar al abogado:
    - Dentro de la app siempre: es el registro de lo que se le avisó.
    - Por correo si tiene esa preferencia activa; si nunca configuró el correo, al de su ficha en gabinete
      (correo_respaldo). Si lo desactivó, no se le escribe.
    - Telegram llega en F3.
    La clave incluye el día: el mismo término avisa una vez por día y canal aunque el evento llegue repetido.
    """
    titulo, mensaje = redactar(aviso)
    referencia = {"id_proceso": aviso.id_proceso, "id_termino": aviso.id_termino}

    def nueva(canal: Canal, destino: str | None = None) -> NotificacionNueva:
        clave = f"{aviso.evento}:{aviso.id_termino}:{aviso.id_abogado}:{canal.value}:{hoy.isoformat()}"
        return NotificacionNueva(aviso.id_abogado, canal, aviso.evento, titulo, mensaje, clave, destino, referencia)

    resultado = [nueva(Canal.APP)]
    pref_correo = next((p for p in preferencias if p.canal == Canal.EMAIL), None)
    if pref_correo is not None:
        if pref_correo.activo and pref_correo.destino:
            resultado.append(nueva(Canal.EMAIL, pref_correo.destino))
    elif correo_respaldo:
        resultado.append(nueva(Canal.EMAIL, correo_respaldo))
    return resultado


def correo_de(n: NotificacionNueva, url_app: str) -> tuple[str, str, str]:
    """Asunto, texto plano y HTML del correo."""
    asunto = f"[Gestión Jurídica] {n.titulo}"
    enlace = f"{url_app.rstrip('/')}/terminos" if url_app else ""
    texto = f"{n.titulo}\n\n{n.mensaje}\n"
    if enlace:
        texto += f"\nVer la agenda de términos: {enlace}\n"
    texto += "\nAviso automático de Gestión Jurídica. Los términos se cuentan en días hábiles judiciales."
    color = "#c0392b" if "vencid" in n.titulo.lower() or "hoy" in n.titulo.lower() else "#1e3a5f"
    boton = (f'<p><a href="{html.escape(enlace)}" style="background:#1e3a5f;color:#fff;padding:10px 16px;'
             f'border-radius:4px;text-decoration:none">Ver la agenda de términos</a></p>') if enlace else ""
    cuerpo_html = f"""<div style="font-family:Segoe UI,Arial,sans-serif;max-width:560px;color:#333">
  <h2 style="color:{color};margin:0 0 12px">{html.escape(n.titulo)}</h2>
  <p style="font-size:15px;line-height:1.5">{html.escape(n.mensaje)}</p>
  {boton}
  <p style="font-size:12px;color:#6b7a8d">Aviso automático de Gestión Jurídica. Los términos se cuentan en días hábiles judiciales.</p>
</div>"""
    return asunto, texto, cuerpo_html
