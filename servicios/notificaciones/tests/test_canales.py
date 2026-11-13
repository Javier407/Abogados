"""Toga por WhatsApp e Instagram (vía Talki): vínculo con código, clave compartida y mismas reglas que Telegram."""
from fastapi.testclient import TestClient

from app.canales import ALFABETO, LARGO_CODIGO, CanalesExternos, chat_id, normalizar_codigo
from app.consultas_bot import ConsultasBot
from app.main import crear_app
from tests.test_consultas_bot import CATALINA, CLIENTE, DatosFalsos

CLAVE = "clave-de-prueba-de-talki"
WA = "573001234567"


class RepoCanales:
    """Doble en memoria de las tablas de la migración 021 y del registro de consultas."""

    def __init__(self):
        self.codigos: dict[str, tuple[int, str, bool]] = {}
        self.chats: dict[tuple[str, str], int] = {}
        self.registro = []

    async def crear_vinculo_canal(self, codigo, id_persona, canal):
        self.codigos[codigo] = (id_persona, canal, False)

    async def usar_vinculo_canal(self, codigo, canal):
        dato = self.codigos.get(codigo)
        if not dato or dato[1] != canal or dato[2]:
            return None
        self.codigos[codigo] = (dato[0], canal, True)
        return dato[0]

    async def guardar_canal(self, id_persona, canal, id_externo):
        self.chats = {k: v for k, v in self.chats.items()
                      if not (k[0] == canal and (v == id_persona or k[1] == id_externo))}
        self.chats[(canal, id_externo)] = id_persona

    async def persona_de_canal(self, canal, id_externo):
        return self.chats.get((canal, id_externo))

    async def canales_de(self, id_persona):
        return [c for (c, _), p in self.chats.items() if p == id_persona]

    async def quitar_canal(self, id_persona, canal):
        antes = len(self.chats)
        self.chats = {k: v for k, v in self.chats.items() if not (k[0] == canal and v == id_persona)}
        return len(self.chats) < antes

    async def persona_de_chat(self, chat):   # lo usa ConsultasBot para Telegram; aquí no aplica
        return None

    async def registrar_consulta_bot(self, id_persona, chat, comando, resultado):
        self.registro.append((id_persona, chat, comando, resultado))


def montar(datos=None, intentos=5):
    repo = RepoCanales()
    toga = ConsultasBot(repo, datos or DatosFalsos())
    return repo, CanalesExternos(repo, toga, reloj=lambda: 0.0, intentos_por_hora=intentos)


async def test_codigo_de_un_uso_que_vincula_el_chat_y_toga_responde():
    repo, canales = montar()
    v = await canales.generar_vinculo(CATALINA, "WHATSAPP")
    assert len(v["codigo"]) == LARGO_CODIGO and set(v["codigo"]) <= set(ALFABETO)
    assert v["mensaje"] == f"vincular {v['codigo']}"

    r = await canales.vincular("WHATSAPP", WA, v["codigo"].lower())     # minúsculas también sirven
    assert r["vinculado"] and r["mensaje"].startswith("Listo")
    assert await canales.estado(CATALINA) == [
        {"canal": "WHATSAPP", "nombre": "WhatsApp", "vinculado": True},
        {"canal": "INSTAGRAM", "nombre": "Instagram", "vinculado": False}]

    p = await canales.preguntar("WHATSAPP", WA, "¿qué términos me vencen?")
    assert p["vinculado"] and p["respuesta"].startswith("Tiene 2 término(s) pendiente(s)")
    assert repo.registro[-1] == (CATALINA, "whatsapp:" + WA, "terminos", "RESPONDIDA")

    # El mismo código no sirve dos veces.
    assert not (await canales.vincular("WHATSAPP", "573009999999", v["codigo"]))["vinculado"]


async def test_un_codigo_de_whatsapp_no_sirve_en_instagram():
    _, canales = montar()
    v = await canales.generar_vinculo(CATALINA, "WHATSAPP")
    r = await canales.vincular("INSTAGRAM", "1789", v["codigo"])
    assert not r["vinculado"] and "no sirve" in r["mensaje"]


async def test_chat_sin_vinculo_no_recibe_datos():
    repo, canales = montar()
    p = await canales.preguntar("INSTAGRAM", "1789", "mis procesos")
    assert not p["vinculado"] and "Vincular Instagram" in p["respuesta"]
    assert repo.registro == [(None, "instagram:1789", "consulta", "SIN_VINCULO")]


async def test_un_cliente_vinculado_no_recibe_datos_de_procesos():
    repo, canales = montar(DatosFalsos(personas={CLIENTE: {"nombres": "Cliente", "activo": False}}))
    v = await canales.generar_vinculo(CLIENTE, "WHATSAPP")
    await canales.vincular("WHATSAPP", WA, v["codigo"])
    p = await canales.preguntar("WHATSAPP", WA, "mis procesos")
    assert p["vinculado"] and "solo para el equipo de la firma" in p["respuesta"]
    assert repo.registro[-1][3] == "NO_AUTORIZADO"


async def test_la_ayuda_no_muestra_comandos_de_telegram():
    _, canales = montar()
    v = await canales.generar_vinculo(CATALINA, "WHATSAPP")
    await canales.vincular("WHATSAPP", WA, v["codigo"])
    ayuda = (await canales.preguntar("WHATSAPP", WA, "hola"))["respuesta"]
    assert ayuda.startswith("Hola, Catalina. Soy Lawy") and "/mis_procesos" not in ayuda


async def test_volver_a_vincular_mueve_el_chat_y_desvincular_lo_quita():
    repo, canales = montar()
    await canales.vincular("WHATSAPP", WA, (await canales.generar_vinculo(CATALINA, "WHATSAPP"))["codigo"])
    nuevo = "573008888888"
    await canales.vincular("WHATSAPP", nuevo, (await canales.generar_vinculo(CATALINA, "WHATSAPP"))["codigo"])
    assert repo.chats == {("WHATSAPP", nuevo): CATALINA}          # un solo chat por persona y canal
    assert await canales.desvincular(CATALINA, "WHATSAPP")
    assert not (await canales.preguntar("WHATSAPP", nuevo, "hola"))["vinculado"]
    assert not await canales.desvincular(CATALINA, "WHATSAPP")


async def test_limita_los_intentos_de_codigo_por_chat():
    repo, canales = montar(intentos=2)
    for _ in range(2):
        assert not (await canales.vincular("WHATSAPP", WA, "NOEXISTE"))["vinculado"]
    v = await canales.generar_vinculo(CATALINA, "WHATSAPP")
    r = await canales.vincular("WHATSAPP", WA, v["codigo"])        # ni siquiera con un código bueno
    assert not r["vinculado"] and "demasiados intentos" in r["mensaje"]
    assert repo.registro[-1] == (None, "whatsapp:" + WA, "vincular", "LIMITE")
    # Otro chat no queda bloqueado.
    assert (await canales.vincular("WHATSAPP", "573007777777", v["codigo"]))["vinculado"]


async def test_si_el_registro_falla_igual_responde():
    repo, canales = montar()

    async def falla(*_):
        raise RuntimeError("sin base")
    repo.registrar_consulta_bot = falla
    assert not (await canales.preguntar("WHATSAPP", WA, "hola"))["vinculado"]


def test_ayudas():
    assert normalizar_codigo(" k7p2-m9qx ") == "K7P2M9QX"
    assert chat_id("WHATSAPP", "5" * 60) == ("whatsapp:" + "5" * 60)[:40]


# ------------------------------------------------------------------ API

def app_de_prueba(clave=CLAVE, con_canales=True, en_portal=True):
    from tests.test_actuaciones_y_telegram import servicio as servicio_de_prueba
    from tests.test_aplicacion import RepoMemoria

    repo, canales = montar()
    app = crear_app(servicio=servicio_de_prueba(RepoMemoria()), canales=canales if con_canales else None,
                    canal_clave=clave, canales_en_portal=en_portal)
    return repo, app


def test_api_de_talki_exige_la_clave_compartida():
    _, app = app_de_prueba()
    cuerpo = {"canal": "WHATSAPP", "idExterno": WA, "texto": "hola"}
    with TestClient(app) as c:
        assert c.post("/canales/toga", json=cuerpo).status_code == 401
        assert c.post("/canales/toga", json=cuerpo, headers={"X-Canal-Clave": "otra"}).status_code == 401
        r = c.post("/canales/toga", json=cuerpo, headers={"X-Canal-Clave": CLAVE})
        assert r.status_code == 200 and r.json()["vinculado"] is False
        malo = {**cuerpo, "canal": "SMS"}
        assert c.post("/canales/toga", json=malo, headers={"X-Canal-Clave": CLAVE}).status_code == 400
        largo = {**cuerpo, "texto": "a" * 301}
        assert c.post("/canales/toga", json=largo, headers={"X-Canal-Clave": CLAVE}).status_code == 400


def test_sin_clave_configurada_los_canales_estan_apagados():
    _, app = app_de_prueba(clave="")
    with TestClient(app) as c:
        r = c.post("/canales/vinculo", json={"canal": "WHATSAPP", "idExterno": WA, "codigo": "ABCD1234"},
                   headers={"X-Canal-Clave": ""})
        assert r.status_code == 503
        estado = c.get("/notificaciones/canales", params={"idPersona": CATALINA},
                       headers={"X-Usuario-Id": str(CATALINA)})
        assert estado.status_code == 200 and estado.json()["configurado"] is False


def test_de_punta_a_punta_portal_y_talki():
    _, app = app_de_prueba()
    yo = {"X-Usuario-Id": str(CATALINA)}
    talki = {"X-Canal-Clave": CLAVE}
    with TestClient(app) as c:
        v = c.post("/notificaciones/canales/whatsapp/vinculo", params={"idPersona": CATALINA}, headers=yo)
        assert v.status_code == 200 and v.json()["canal"] == "WHATSAPP"
        r = c.post("/canales/vinculo", json={"canal": "WHATSAPP", "idExterno": WA, "codigo": v.json()["codigo"]},
                   headers=talki)
        assert r.json()["vinculado"] is True
        p = c.post("/canales/toga", json={"canal": "WHATSAPP", "idExterno": WA, "texto": "mis procesos"},
                   headers=talki)
        assert p.json()["respuesta"].startswith("Tiene 2 procesos abiertos")
        estado = c.get("/notificaciones/canales", params={"idPersona": CATALINA}, headers=yo).json()
        assert estado["configurado"] and estado["canales"][0]["vinculado"]
        # Nadie vincula ni desvincula por otra persona.
        assert c.post("/notificaciones/canales/whatsapp/vinculo", params={"idPersona": 1}, headers=yo).status_code == 403
        assert c.delete("/notificaciones/canales/sms", params={"idPersona": CATALINA}, headers=yo).status_code == 404
        assert c.delete("/notificaciones/canales/whatsapp", params={"idPersona": CATALINA},
                        headers=yo).json() == {"desvinculado": True}


def test_con_la_tarjeta_apagada_el_portal_no_la_muestra_pero_talki_sigue():
    _, app = app_de_prueba(en_portal=False)
    with TestClient(app) as c:
        estado = c.get("/notificaciones/canales", params={"idPersona": CATALINA},
                       headers={"X-Usuario-Id": str(CATALINA)})
        assert estado.json()["configurado"] is False
        r = c.post("/canales/toga", json={"canal": "WHATSAPP", "idExterno": WA, "texto": "hola"},
                   headers={"X-Canal-Clave": CLAVE})
        assert r.status_code == 200


def test_sin_motor_de_canales_es_503():
    _, app = app_de_prueba(con_canales=False)
    with TestClient(app) as c:
        assert c.get("/notificaciones/canales", params={"idPersona": CATALINA},
                     headers={"X-Usuario-Id": str(CATALINA)}).status_code == 503
        assert c.post("/canales/toga", json={"canal": "WHATSAPP", "idExterno": WA, "texto": "hola"},
                      headers={"X-Canal-Clave": CLAVE}).status_code == 503


# ------------------------------------------------------------------ Telegram por Talki (TELEGRAM_ESCUCHA=false)

TG = "6612345678"


def montar_telegram(codigos=None):
    """El vínculo de Telegram es el de los avisos: el código de /start lo resuelve ServicioNotificaciones."""
    repo = RepoCanales()
    chats = {}
    codigos = dict(codigos or {"Ab3_x-9QzK1": CATALINA})

    async def persona_de_chat(chat):
        return chats.get(chat)
    repo.persona_de_chat = persona_de_chat

    async def vincular_telegram(codigo, chat):
        persona = codigos.pop(codigo, None)
        if persona:
            chats[chat] = persona
        return persona

    toga = ConsultasBot(repo, DatosFalsos())
    return repo, chats, CanalesExternos(repo, toga, reloj=lambda: 0.0, vincular_telegram=vincular_telegram)


async def test_telegram_por_talki_vincula_con_el_codigo_del_enlace_y_responde_con_comandos():
    repo, chats, canales = montar_telegram()
    sin = await canales.preguntar("TELEGRAM", TG, "/mis_procesos")
    assert not sin["vinculado"] and "Vincular Telegram" in sin["respuesta"]

    assert not (await canales.vincular("TELEGRAM", TG, "ab3_x-9qzk1"))["vinculado"]   # distingue mayúsculas
    r = await canales.vincular("TELEGRAM", TG, " Ab3_x-9QzK1 ")
    assert r["vinculado"] and "avisos" in r["mensaje"] and chats == {TG: CATALINA}
    assert repo.registro[-1] == (CATALINA, TG, "vincular", "RESPONDIDA")

    p = await canales.preguntar("TELEGRAM", TG, "/terminos")
    assert p["vinculado"] and p["respuesta"].startswith("Tiene 2 término(s) pendiente(s)")
    ayuda = (await canales.preguntar("TELEGRAM", TG, "/start"))["respuesta"]
    assert "/mis_procesos" in ayuda                                   # en Telegram sí van los comandos


async def test_telegram_sin_vinculador_no_vincula():
    _, canales = montar()
    r = await canales.vincular("TELEGRAM", TG, "Ab3_x-9QzK1")
    assert not r["vinculado"] and "no está disponible" in r["mensaje"]


def test_api_telegram_solo_chats_privados():
    _, app = app_de_prueba()
    talki = {"X-Canal-Clave": CLAVE}
    with TestClient(app) as c:
        grupo = {"canal": "TELEGRAM", "idExterno": "-100123456", "texto": "mis procesos"}
        assert c.post("/canales/toga", json=grupo, headers=talki).status_code == 400
        privado = {**grupo, "idExterno": TG}
        r = c.post("/canales/toga", json=privado, headers=talki)
        assert r.status_code == 200 and r.json()["vinculado"] is False
