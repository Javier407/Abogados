"""Reglas de los avisos: qué evento se acepta, por qué canales se avisa y qué dice el aviso.

No depende de FastAPI, de la base de datos ni de RabbitMQ: se prueba con datos en memoria.
"""
from __future__ import annotations

import html
from dataclasses import dataclass, field
from datetime import date, datetime
from enum import Enum

EVENTOS_TERMINO = ("termino.por_vencer", "termino.vencido")
EVENTO_ACTUACION = "actuacion.detectada"
EVENTO_AUDIENCIA = "audiencia.proxima"
EVENTO_RESUMEN = "portal.resumen_publicado"
EVENTO_SEGURIDAD = "cuenta.seguridad"
DIAS_SEMANA = ("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
LARGO_MENSAJE = 1000


class Canal(str, Enum):
    APP = "APP"
    EMAIL = "EMAIL"
    TELEGRAM = "TELEGRAM"


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


@dataclass(frozen=True)
class AvisoActuacion:
    """Evento actuacion.detectada que publica el servicio vigilancia."""
    radicado: str
    id_proceso: int
    referencia: str
    fecha: date
    tipo: str
    actuacion: str
    anotacion: str
    fuente: str

    @staticmethod
    def desde_mensaje(cuerpo: dict) -> "AvisoActuacion":
        try:
            return AvisoActuacion(
                radicado=str(cuerpo["radicado"]),
                id_proceso=int(cuerpo["id_proceso"]),
                referencia=str(cuerpo["referencia"]),
                fecha=date.fromisoformat(cuerpo["fecha"]),
                tipo=str(cuerpo.get("tipo") or "AUTO"),
                actuacion=str(cuerpo["actuacion"]),
                anotacion=str(cuerpo.get("anotacion") or ""),
                fuente=str(cuerpo.get("fuente") or ""),
            )
        except (KeyError, TypeError, ValueError) as e:
            raise EventoInvalido(f"Mensaje actuacion.detectada incompleto o mal formado: {e}") from e


@dataclass(frozen=True)
class AvisoAudiencia:
    """Evento audiencia.proxima que publica procesos el día hábil anterior a la audiencia."""
    id_actuacion: int
    id_proceso: int
    radicado: str
    descripcion: str
    fecha: date
    hora: str | None          # "09:00" o None si no se registró
    lugar: str | None
    id_abogado: int

    @staticmethod
    def desde_mensaje(cuerpo: dict) -> "AvisoAudiencia":
        try:
            hora = cuerpo.get("hora")
            return AvisoAudiencia(
                id_actuacion=int(cuerpo["id_actuacion"]),
                id_proceso=int(cuerpo["id_proceso"]),
                radicado=str(cuerpo["radicado"]),
                descripcion=str(cuerpo["descripcion"]),
                fecha=date.fromisoformat(cuerpo["fecha"]),
                hora=str(hora)[:5] if hora else None,
                lugar=str(cuerpo["lugar"]) if cuerpo.get("lugar") else None,
                id_abogado=int(cuerpo["id_abogado"]),
            )
        except (KeyError, TypeError, ValueError) as e:
            raise EventoInvalido(f"Mensaje audiencia.proxima incompleto o mal formado: {e}") from e


@dataclass(frozen=True)
class AvisoResumen:
    """Evento portal.resumen_publicado: el abogado publicó un resumen para el cliente en el portal."""
    id_resumen: int
    id_proceso: int
    radicado: str
    id_cliente: int

    @staticmethod
    def desde_mensaje(cuerpo: dict) -> "AvisoResumen":
        try:
            return AvisoResumen(int(cuerpo["id_resumen"]), int(cuerpo["id_proceso"]), str(cuerpo["radicado"]),
                                int(cuerpo["id_cliente"]))
        except (KeyError, TypeError, ValueError) as e:
            raise EventoInvalido(f"Mensaje portal.resumen_publicado incompleto o mal formado: {e}") from e


@dataclass(frozen=True)
class AvisoSeguridad:
    """Evento cuenta.seguridad (F15): cambió algo de la seguridad de la cuenta de una persona (lo publica identidad)."""
    tipo: str
    id_usuario: int
    usuario: str
    email: str | None
    momento: datetime

    @staticmethod
    def desde_mensaje(cuerpo: dict) -> "AvisoSeguridad":
        try:
            tipo = str(cuerpo["tipo"])
            if tipo not in TEXTOS_SEGURIDAD:
                raise ValueError(f"tipo desconocido {tipo}")
            momento = datetime.fromisoformat(str(cuerpo["momento"]).replace("Z", "+00:00"))
            return AvisoSeguridad(tipo, int(cuerpo["idUsuario"]), str(cuerpo["usuario"]), cuerpo.get("email") or None, momento)
        except (KeyError, TypeError, ValueError) as e:
            raise EventoInvalido(f"Mensaje cuenta.seguridad incompleto o mal formado: {e}") from e


# Título y explicación de cada aviso de seguridad. Todos terminan diciendo qué hacer si no fue la persona.
NO_FUE_USTED = " Si no fue usted, avise de inmediato al administrador de la firma."
TEXTOS_SEGURIDAD = {
    "CLAVE_CAMBIADA": ("Se cambió la contraseña de su cuenta",
                       "La contraseña de su cuenta {usuario} se cambió el {cuando}." + NO_FUE_USTED),
    "CLAVE_RESTABLECIDA": ("El administrador restableció su contraseña",
                           "El {cuando} el administrador le puso una contraseña temporal a su cuenta {usuario}; al entrar "
                           "deberá cambiarla. Se cerraron sus sesiones abiertas." + NO_FUE_USTED),
    "CUENTA_BLOQUEADA": ("Su cuenta se bloqueó por intentos fallidos",
                         "El {cuando} hubo 5 intentos fallidos de entrar con su cuenta {usuario} y quedó bloqueada 15 "
                         "minutos. Si no fue usted, alguien conoce su usuario: cambie su contraseña y active la "
                         "verificación en dos pasos en Mi cuenta."),
    "MFA_ACTIVADA": ("Se activó la verificación en dos pasos",
                     "Desde el {cuando}, al entrar con su cuenta {usuario} se pide además el código de su app "
                     "autenticadora." + NO_FUE_USTED),
    "MFA_DESACTIVADA": ("Se desactivó la verificación en dos pasos",
                        "El {cuando} se desactivó la verificación en dos pasos de su cuenta {usuario}: ahora basta la "
                        "contraseña para entrar." + NO_FUE_USTED),
    "MFA_REINICIADA": ("El administrador reinició su verificación en dos pasos",
                       "El {cuando} el administrador quitó la verificación en dos pasos de su cuenta {usuario} y cerró sus "
                       "sesiones. Al entrar puede volver a activarla." + NO_FUE_USTED),
    "CODIGOS_RENOVADOS": ("Se generaron nuevos códigos de recuperación",
                          "El {cuando} se generaron nuevos códigos de recuperación para su cuenta {usuario}; los "
                          "anteriores ya no sirven." + NO_FUE_USTED),
}


def contenido_seguridad(a: AvisoSeguridad) -> Contenido:
    """La hora va en la de Colombia. Cada cambio se avisa una vez por canal (la clave lleva el momento)."""
    from zoneinfo import ZoneInfo
    cuando = a.momento.astimezone(ZoneInfo("America/Bogota")).strftime("%d/%m/%Y a las %H:%M")
    titulo, plantilla = TEXTOS_SEGURIDAD[a.tipo]
    return Contenido(EVENTO_SEGURIDAD, a.id_usuario, titulo, plantilla.format(usuario=a.usuario, cuando=cuando),
                     {"tipo": a.tipo}, f"{EVENTO_SEGURIDAD}:{a.tipo}:{a.id_usuario}:{a.momento.isoformat()}:{{canal}}")


@dataclass(frozen=True)
class CuentaCliente:
    """Cuenta del portal de un cliente (identidad): a quién avisarle."""
    id_usuario: int
    nombre: str
    email: str | None


@dataclass(frozen=True)
class Contenido:
    """Lo que se le avisa a una persona, sin importar el canal. La clave lleva {canal} para completar."""
    evento: str
    id_persona: int
    titulo: str
    mensaje: str
    referencia: dict
    plantilla_clave: str


def contenido_termino(aviso: AvisoTermino, hoy: date) -> Contenido:
    """La clave incluye el día: el mismo término avisa una vez por día y canal aunque el evento llegue repetido."""
    titulo, mensaje = redactar(aviso)
    return Contenido(aviso.evento, aviso.id_abogado, titulo, mensaje,
                     {"id_proceso": aviso.id_proceso, "id_termino": aviso.id_termino},
                     f"{aviso.evento}:{aviso.id_termino}:{aviso.id_abogado}:{{canal}}:{hoy.isoformat()}")


def contenido_actuacion(a: AvisoActuacion, id_persona: int) -> Contenido:
    """Una actuación se avisa una sola vez por persona y canal (la clave no lleva día)."""
    titulo = "Nueva sentencia en un proceso" if a.tipo == "SENTENCIA" else "Nueva actuación en un proceso"
    mensaje = f"{a.actuacion} — proceso {radicado_legible(a.radicado)} — {a.fecha.strftime('%d/%m/%Y')}"
    if a.anotacion:
        mensaje += f": {a.anotacion}"
    return Contenido("actuacion.detectada", id_persona, titulo, mensaje[:LARGO_MENSAJE],
                     {"id_proceso": a.id_proceso, "referencia": a.referencia},
                     f"actuacion.detectada:{a.referencia}:{id_persona}:{{canal}}")


def contenido_audiencia(a: AvisoAudiencia, hoy: date) -> Contenido:
    """Una audiencia se avisa una vez por persona, canal y fecha (si la reprograman, se avisa de nuevo)."""
    dias = (a.fecha - hoy).days
    fecha = a.fecha.strftime("%d/%m/%Y")
    cuando = "mañana" if dias == 1 else f"el {DIAS_SEMANA[a.fecha.weekday()]} {fecha}"
    titulo = f"Audiencia {cuando}"
    detalle = a.fecha.strftime("%d/%m/%Y") + (f" a las {a.hora}" if a.hora else "")
    mensaje = f"{a.descripcion} — proceso {radicado_legible(a.radicado)} — {detalle}"
    if a.lugar:
        mensaje += f" — {a.lugar}"
    return Contenido(EVENTO_AUDIENCIA, a.id_abogado, titulo, mensaje[:LARGO_MENSAJE],
                     {"id_proceso": a.id_proceso, "id_actuacion": a.id_actuacion},
                     f"{EVENTO_AUDIENCIA}:{a.id_actuacion}:{a.id_abogado}:{{canal}}:{a.fecha.isoformat()}")


def canales(c: Contenido, preferencias: list[Preferencia], correo_respaldo: str | None,
            telegram_activo: bool = False) -> list[NotificacionNueva]:
    """
    Por qué canales avisar:
    - Dentro de la app siempre: es el registro de lo que se le avisó.
    - Por correo si tiene esa preferencia activa; si nunca configuró el correo, al de su ficha en gabinete
      (correo_respaldo). Si lo desactivó, no se le escribe.
    - Por Telegram si vinculó su cuenta (preferencia TELEGRAM activa) y el bot está configurado.
    """
    def nueva(canal: Canal, destino: str | None = None) -> NotificacionNueva:
        return NotificacionNueva(c.id_persona, canal, c.evento, c.titulo, c.mensaje,
                                 c.plantilla_clave.format(canal=canal.value), destino, c.referencia)

    resultado = [nueva(Canal.APP)]
    pref_correo = next((p for p in preferencias if p.canal == Canal.EMAIL), None)
    if pref_correo is not None:
        if pref_correo.activo and pref_correo.destino:
            resultado.append(nueva(Canal.EMAIL, pref_correo.destino))
    elif correo_respaldo:
        resultado.append(nueva(Canal.EMAIL, correo_respaldo))
    pref_telegram = next((p for p in preferencias if p.canal == Canal.TELEGRAM), None)
    if telegram_activo and pref_telegram and pref_telegram.activo and pref_telegram.destino:
        resultado.append(nueva(Canal.TELEGRAM, pref_telegram.destino))
    return resultado


def avisos_para(aviso: AvisoTermino, preferencias: list[Preferencia], correo_respaldo: str | None,
                hoy: date, telegram_activo: bool = False) -> list[NotificacionNueva]:
    """Avisos de un término (atajo de contenido_termino + canales)."""
    return canales(contenido_termino(aviso, hoy), preferencias, correo_respaldo, telegram_activo)


def enlace_de(n: NotificacionNueva, url_app: str) -> tuple[str, str]:
    """Adónde lleva el aviso: la agenda para los términos, el expediente del proceso para lo demás."""
    if not url_app:
        return "", ""
    base = url_app.rstrip("/")
    if n.evento == EVENTO_SEGURIDAD:
        return f"{base}/cuenta", "Revisar mi cuenta"
    if n.referencia.get("portal") and n.referencia.get("id_proceso"):
        return f"{base}/portal/procesos/{n.referencia['id_proceso']}", "Ver su proceso"
    if n.evento in (EVENTO_ACTUACION, EVENTO_AUDIENCIA) and n.referencia.get("id_proceso"):
        return f"{base}/procesos/{n.referencia['id_proceso']}", "Ver el expediente"
    return f"{base}/terminos", "Ver la agenda de términos"


# ------------------------------------------------------------------ avisos al cliente (F9)
# Lenguaje sencillo y sin datos internos de la firma (términos, notas). El enlace lleva al portal.

def contenido_resumen_cliente(a: AvisoResumen, cuenta: CuentaCliente) -> Contenido:
    mensaje = (f"Su abogado publicó una actualización de su proceso {radicado_legible(a.radicado)}. "
               "Puede leerla en el portal de clientes.")
    return Contenido(EVENTO_RESUMEN, cuenta.id_usuario, "Novedad en su proceso", mensaje,
                     {"id_proceso": a.id_proceso, "portal": True},
                     f"{EVENTO_RESUMEN}:{a.id_resumen}:{cuenta.id_usuario}:{{canal}}")


def contenido_audiencia_cliente(a: AvisoAudiencia, cuenta: CuentaCliente, hoy: date) -> Contenido:
    dias = (a.fecha - hoy).days
    fecha = a.fecha.strftime("%d/%m/%Y")
    cuando = "mañana" if dias == 1 else f"el {DIAS_SEMANA[a.fecha.weekday()]} {fecha}"
    mensaje = f"Su proceso {radicado_legible(a.radicado)} tiene audiencia {cuando}"
    if a.hora:
        mensaje += f" a las {a.hora}"
    if a.lugar:
        mensaje += f" en {a.lugar}"
    mensaje += ". Su abogado le indicará si debe asistir."
    return Contenido(EVENTO_AUDIENCIA, cuenta.id_usuario, f"Audiencia {cuando} en su proceso", mensaje[:LARGO_MENSAJE],
                     {"id_proceso": a.id_proceso, "id_actuacion": a.id_actuacion, "portal": True},
                     f"{EVENTO_AUDIENCIA}:{a.id_actuacion}:cliente:{cuenta.id_usuario}:{{canal}}:{a.fecha.isoformat()}")


def texto_telegram(n: NotificacionNueva, url_app: str) -> str:
    enlace, _ = enlace_de(n, url_app)
    texto = f"⚖️ {n.titulo}\n\n{n.mensaje}"
    return f"{texto}\n\n{enlace}" if enlace else texto


def correo_de(n: NotificacionNueva, url_app: str) -> tuple[str, str, str]:
    """Asunto, texto plano y HTML del correo."""
    asunto = f"[Gestión Jurídica] {n.titulo}"
    enlace, texto_enlace = enlace_de(n, url_app)
    texto = f"{n.titulo}\n\n{n.mensaje}\n"
    if enlace:
        texto += f"\n{texto_enlace}: {enlace}\n"
    pie = ("Aviso automático del portal de clientes de Gestión Jurídica." if n.referencia.get("portal")
           else "Aviso automático de Gestión Jurídica. Los términos se cuentan en días hábiles judiciales.")
    texto += "\n" + pie
    color = "#c0392b" if "vencid" in n.titulo.lower() or "hoy" in n.titulo.lower() else "#1e3a5f"
    boton = (f'<p><a href="{html.escape(enlace)}" style="background:#1e3a5f;color:#fff;padding:10px 16px;'
             f'border-radius:4px;text-decoration:none">{texto_enlace}</a></p>') if enlace else ""
    cuerpo_html = f"""<div style="font-family:Segoe UI,Arial,sans-serif;max-width:560px;color:#333">
  <h2 style="color:{color};margin:0 0 12px">{html.escape(n.titulo)}</h2>
  <p style="font-size:15px;line-height:1.5">{html.escape(n.mensaje)}</p>
  {boton}
  <p style="font-size:12px;color:#6b7a8d">{html.escape(pie)}</p>
</div>"""
    return asunto, texto, cuerpo_html
