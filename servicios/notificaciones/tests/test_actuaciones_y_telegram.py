"""F3: avisos de actuaciones detectadas y canal de Telegram (con dobles, sin red)."""
import logging
from datetime import date

import httpx
import pytest

from app.aplicacion import ServicioNotificaciones
from app.dominio import (AvisoActuacion, Canal, EventoInvalido, Preferencia, canales, contenido_actuacion,
                         texto_telegram)
from app.infraestructura import TelegramBot
from tests.test_aplicacion import ABOGADO, CorreoFalso, DirectorioFalso, Registro, RepoMemoria

ACTUACION = {"radicado": "11001400301520260077700", "id_proceso": 34, "referencia": "SIM:1",
             "fecha": "2026-10-15", "tipo": "AUTO", "actuacion": "Auto requiere al demandante",
             "anotacion": "Allegue el poder en 5 días", "fuente": "SIMULADA", "ocurrido": "2026-10-15T20:00:00Z"}


class TelegramFalso:
    def __init__(self, activo=True, falla=False):
        self.activo, self.falla, self.usuario = activo, falla, "gestion_juridica_bot"
        self.enviados = []

    async def enviar(self, chat_id, texto):
        if self.falla:
            raise ConnectionError("Telegram caído")
        self.enviados.append((chat_id, texto))


class ProcesosFalso:
    def __init__(self, responsable=ABOGADO):
        self.resp = responsable

    async def responsable(self, id_proceso):
        return self.resp


def servicio(repo, telegram=None, procesos=None, correo=None):
    return ServicioNotificaciones(repo, correo or CorreoFalso(), DirectorioFalso(), Registro(), Registro(),
                                  url_app="http://app", hoy=lambda: date(2026, 10, 15),
                                  telegram=telegram or TelegramFalso(), procesos=procesos or ProcesosFalso())


# ------------------------------------------------------------------ dominio

def test_redacta_la_actuacion_con_el_radicado_legible():
    c = contenido_actuacion(AvisoActuacion.desde_mensaje(ACTUACION), ABOGADO)
    assert c.titulo == "Nueva actuación en un proceso"
    assert c.mensaje == ("Auto requiere al demandante — proceso 11001-40-03-015-2026-00777-00 — 15/10/2026: "
                         "Allegue el poder en 5 días")
    sentencia = AvisoActuacion.desde_mensaje({**ACTUACION, "tipo": "SENTENCIA"})
    assert contenido_actuacion(sentencia, ABOGADO).titulo == "Nueva sentencia en un proceso"


def test_actuacion_mal_formada():
    with pytest.raises(EventoInvalido):
        AvisoActuacion.desde_mensaje({"radicado": "1"})


def test_telegram_solo_si_esta_vinculado_y_el_bot_activo():
    c = contenido_actuacion(AvisoActuacion.desde_mensaje(ACTUACION), ABOGADO)
    prefs = [Preferencia(Canal.TELEGRAM, "555")]
    assert Canal.TELEGRAM in [n.canal for n in canales(c, prefs, None, telegram_activo=True)]
    assert Canal.TELEGRAM not in [n.canal for n in canales(c, prefs, None, telegram_activo=False)]
    assert Canal.TELEGRAM not in [n.canal for n in canales(c, [], None, telegram_activo=True)]
    inactivo = [Preferencia(Canal.TELEGRAM, "555", activo=False)]
    assert Canal.TELEGRAM not in [n.canal for n in canales(c, inactivo, None, telegram_activo=True)]


def test_el_aviso_de_actuacion_lleva_al_proceso():
    c = contenido_actuacion(AvisoActuacion.desde_mensaje(ACTUACION), ABOGADO)
    n = canales(c, [Preferencia(Canal.TELEGRAM, "555")], None, telegram_activo=True)[-1]
    assert texto_telegram(n, "http://app/").endswith("http://app/procesos/34/editar")
    assert n.clave == "actuacion.detectada:SIM:1:79123456:TELEGRAM"


# ------------------------------------------------------------------ casos de uso

async def test_una_actuacion_llega_al_responsable_por_app_correo_y_telegram():
    repo = RepoMemoria({ABOGADO: [Preferencia(Canal.EMAIL, "a@firma.co"), Preferencia(Canal.TELEGRAM, "555")]})
    telegram, correo = TelegramFalso(), CorreoFalso()

    assert await servicio(repo, telegram, correo=correo).procesar("actuacion.detectada", ACTUACION) == 3

    assert telegram.enviados[0][0] == "555" and "Auto requiere al demandante" in telegram.enviados[0][1]
    assert correo.enviados[0][1] == "[Gestión Jurídica] Nueva actuación en un proceso"
    assert repo.por_canal(Canal.TELEGRAM)[0]["estado"] == "ENVIADA"


async def test_la_misma_actuacion_no_se_avisa_dos_veces_ni_otro_dia():
    repo = RepoMemoria({ABOGADO: [Preferencia(Canal.TELEGRAM, "555")]})
    telegram = TelegramFalso()
    s = servicio(repo, telegram)
    await s.procesar("actuacion.detectada", ACTUACION)
    s.hoy = lambda: date(2026, 10, 16)
    assert await s.procesar("actuacion.detectada", ACTUACION) == 0
    assert len(telegram.enviados) == 1


async def test_si_telegram_falla_queda_fallida():
    repo = RepoMemoria({ABOGADO: [Preferencia(Canal.TELEGRAM, "555")]})
    await servicio(repo, TelegramFalso(falla=True)).procesar("actuacion.detectada", ACTUACION)
    assert repo.por_canal(Canal.TELEGRAM)[0]["estado"] == "FALLIDA"
    assert repo.por_canal(Canal.APP)


async def test_proceso_sin_responsable_no_avisa():
    repo = RepoMemoria()
    assert await servicio(repo, procesos=ProcesosFalso(None)).procesar("actuacion.detectada", ACTUACION) == 0
    assert repo.filas == {}


async def test_los_terminos_tambien_llegan_por_telegram():
    repo = RepoMemoria({ABOGADO: [Preferencia(Canal.TELEGRAM, "555")]})
    telegram = TelegramFalso()
    termino = {"id_termino": 7, "id_proceso": 2, "radicado": "11001310303220240045800", "descripcion": "Alegatos",
               "vence": "2026-10-20", "dias_habiles_restantes": 3, "id_abogado": ABOGADO}
    await servicio(repo, telegram).procesar("termino.por_vencer", termino)
    assert "Término por vencer" in telegram.enviados[0][1]


async def test_vincular_telegram_con_codigo_de_un_solo_uso():
    repo = RepoMemoria()
    s = servicio(repo)
    v = await s.generar_vinculo(ABOGADO)
    assert v["enlace"] == f"https://t.me/gestion_juridica_bot?start={v['codigo']}"

    assert await s.vincular_telegram(v["codigo"], "555") == ABOGADO
    assert await s.vincular_telegram(v["codigo"], "666") is None           # ya se usó
    assert (await s.estado_telegram(ABOGADO))["vinculado"]
    assert await s.desvincular_telegram(ABOGADO)
    assert not (await s.estado_telegram(ABOGADO))["vinculado"]


async def test_sin_bot_no_se_generan_vinculos():
    with pytest.raises(ValueError, match="TELEGRAM_TOKEN"):
        await servicio(RepoMemoria(), TelegramFalso(activo=False)).generar_vinculo(ABOGADO)


# ------------------------------------------------------------------ bot (API de Telegram simulada)

async def test_el_bot_responde_al_start_y_nunca_registra_el_token(caplog):
    llamadas = []

    def api(req: httpx.Request):
        metodo = req.url.path.rsplit("/", 1)[-1]
        llamadas.append((metodo, req.content.decode()))
        if metodo == "getMe":
            return httpx.Response(200, json={"ok": True, "result": {"username": "gestion_juridica_bot"}})
        return httpx.Response(200, json={"ok": True, "result": {}})

    bot = TelegramBot("123:SECRETO", cliente=httpx.AsyncClient(transport=httpx.MockTransport(api)))
    with caplog.at_level(logging.DEBUG):
        await bot.iniciar()
        vinculados = []

        async def vincular(codigo, chat_id):
            vinculados.append((codigo, chat_id))
            return ABOGADO if codigo == "abc" else None

        await bot._atender({"text": "/start abc", "chat": {"id": 555}}, vincular)
        await bot._atender({"text": "/start", "chat": {"id": 556}}, vincular)
        await bot._atender({"text": "hola", "chat": {"id": 557}}, vincular)

    assert bot.activo and bot.usuario == "gestion_juridica_bot"
    assert vinculados == [("abc", "555")]
    respuestas = [c for m, c in llamadas if m == "sendMessage"]
    assert len(respuestas) == 2 and "vinculado" in respuestas[0] and "Vincular Telegram" in respuestas[1]
    assert "SECRETO" not in caplog.text
