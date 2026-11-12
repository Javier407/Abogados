"""Bot de consultas por Telegram: responde solo a la firma, solo lo suyo y solo en chat privado."""
import json
import logging
from datetime import date

import httpx
import pytest

from app.consultas_bot import ConsultasBot
from app.infraestructura import DatosBotHttp, TelegramBot

CATALINA = 52345678
CLIENTE = 900123
CHAT = "555"

PROCESOS = [
    {"idProceso": 34, "radicadoLegible": "11001-31-03-032-2026-00501-00", "asunto": "Incumplimiento de contrato",
     "rama": "Civil y comercial", "tipoProceso": "Verbal", "etapa": "PRUEBAS", "estado": "ACTIVO", "rol": "RESPONSABLE",
     "terminosPendientes": 2, "proximoVencimiento": "2026-11-14", "cuantia": 320000000},
    {"idProceso": 35, "radicadoLegible": "11001-40-03-015-2026-00777-00", "asunto": "Cobro de facturas",
     "rama": "Civil y comercial", "tipoProceso": "Ejecutivo", "etapa": "DEMANDA", "estado": "ACTIVO", "rol": "APOYO",
     "terminosPendientes": 0, "proximoVencimiento": None},
    {"idProceso": 36, "radicadoLegible": "11001-31-03-001-2025-00100-00", "asunto": "Archivado",
     "rama": "Civil y comercial", "tipoProceso": "Verbal", "etapa": "CERRADO", "estado": "TERMINADO",
     "rol": "RESPONSABLE", "terminosPendientes": 0},
]
TERMINOS = [
    {"idTermino": 2, "radicadoLegible": "11001-31-03-032-2026-00501-00", "descripcion": "Aportar dictamen",
     "fechaVencimiento": "2026-11-30", "estado": "PENDIENTE", "diasHabilesRestantes": 12},
    {"idTermino": 1, "radicadoLegible": "11001-31-03-032-2026-00501-00", "descripcion": "Pronunciarse sobre excepciones",
     "fechaVencimiento": "2026-11-10", "estado": "PENDIENTE", "diasHabilesRestantes": -1},
    {"idTermino": 3, "radicadoLegible": "11001-31-03-032-2026-00501-00", "descripcion": "Ya hecho",
     "fechaVencimiento": "2026-11-01", "estado": "CUMPLIDO", "diasHabilesRestantes": -5},
]


class RepoFalso:
    def __init__(self, chats=None):
        self.chats = chats if chats is not None else {CHAT: CATALINA}
        self.registro = []

    async def persona_de_chat(self, chat_id):
        return self.chats.get(chat_id)

    async def registrar_consulta_bot(self, id_persona, chat_id, comando, resultado):
        self.registro.append((id_persona, chat_id, comando, resultado))


class DatosFalsos:
    def __init__(self, personas=None, falla=False):
        self.personas = personas if personas is not None else {CATALINA: {"nombres": "Catalina", "activo": True}}
        self.falla = falla
        self.pedidos = []

    async def persona(self, id_persona):
        if self.falla:
            raise httpx.ConnectError("gabinete no responde")
        return self.personas.get(id_persona)

    async def procesos_de(self, id_persona):
        self.pedidos.append(("procesos", id_persona))
        return PROCESOS

    async def terminos_de(self, id_persona):
        self.pedidos.append(("terminos", id_persona))
        return TERMINOS

    async def terminos_del_proceso(self, id_proceso):
        self.pedidos.append(("terminos_proceso", id_proceso))
        return TERMINOS

    async def audiencias_de(self, id_persona, desde, hasta):
        self.pedidos.append(("audiencias", id_persona, desde, hasta))
        return [{"fecha": "2026-11-20", "hora": "09:30:00", "descripcion": "Audiencia inicial",
                 "radicadoLegible": "11001-31-03-032-2026-00501-00", "lugar": "Sala 3"}]


def bot(repo=None, datos=None, limite=10):
    reloj = iter(range(1000))
    return ConsultasBot(repo or RepoFalso(), datos or DatosFalsos(), hoy=lambda: date(2026, 11, 12),
                        reloj=lambda: float(next(reloj)), limite_por_minuto=limite)


@pytest.mark.parametrize("texto, esperado", [
    ("/mis_procesos", ("mis_procesos", "")),
    ("/terminos@gestion_juridica_bot", ("terminos", "")),
    ("/proceso 11001-31-03-032-2026-00501-00", ("proceso", "11001-31-03-032-2026-00501-00")),
    ("11001-31-03-032-2026-00501-00", ("proceso", "11001310303220260050100")),
    ("¿Qué términos me vencen esta semana?", ("terminos", "")),
    ("tengo audiencias?", ("audiencias", "")),
    ("mis casos", ("mis_procesos", "")),
    ("/start", ("ayuda", "")),
    ("hola", ("ayuda", "")),
])
def test_entiende_comandos_y_frases(texto, esperado):
    assert ConsultasBot.interpretar(texto) == esperado


async def test_sus_procesos_abiertos_con_su_papel_y_sin_datos_de_mas():
    repo = RepoFalso()
    r = await bot(repo).responder(CHAT, "/mis_procesos")

    assert r.startswith("Tiene 2 procesos abiertos:")
    assert "11001-31-03-032-2026-00501-00 — Incumplimiento de contrato" in r
    assert "Verbal, etapa pruebas (a cargo)" in r
    assert "2 término(s), 1 VENCIDO(S); el más antiguo desde el 10/11/2026" in r
    assert "(apoyo)" in r
    assert "Archivado" not in r                                 # los cerrados no salen
    assert "320000000" not in r and "cuantia" not in r          # lista blanca de campos
    assert repo.registro == [(CATALINA, CHAT, "mis_procesos", "RESPONDIDA")]


async def test_sus_terminos_del_mas_urgente_al_mas_lejano():
    r = await bot().responder(CHAT, "qué me vence?")

    assert r.startswith("Tiene 2 término(s) pendiente(s), 1 vencido(s). Empiece por esos:")
    assert r.index("Pronunciarse sobre excepciones") < r.index("Aportar dictamen")
    assert "venció hace 1 día hábil" in r and "faltan 12 días hábiles" in r
    assert "Ya hecho" not in r


async def test_un_proceso_suyo_si_y_uno_ajeno_como_si_no_existiera():
    repo = RepoFalso()
    b = bot(repo)

    suyo = await b.responder(CHAT, "/proceso 11001310303220260050100")
    assert suyo.startswith("11001-31-03-032-2026-00501-00 — Incumplimiento de contrato\nVerbal (Civil y comercial)")
    assert "Términos pendientes (2):" in suyo

    ajeno = await b.responder(CHAT, "/proceso 05001310502120260011800")
    assert "No encuentro el proceso 05001-31-05-021-2026-00118-00 entre los suyos" in ajeno
    corto = await b.responder(CHAT, "/proceso 123")
    assert "23 dígitos" in corto and "con o sin guiones" in corto
    assert [x[3] for x in repo.registro] == ["RESPONDIDA", "NO_ENCONTRADO", "RESPONDIDA"]


async def test_sus_audiencias_de_dos_semanas():
    datos = DatosFalsos()
    r = await bot(datos=datos).responder(CHAT, "/audiencias")

    assert "• 20/11/2026 a las 09:30 — Audiencia inicial" in r and "Sala 3" in r
    assert ("audiencias", CATALINA, date(2026, 11, 12), date(2026, 11, 26)) in datos.pedidos


async def test_un_chat_sin_vincular_no_recibe_datos():
    repo, datos = RepoFalso(chats={}), DatosFalsos()
    r = await bot(repo, datos).responder("999", "/mis_procesos")

    assert "no está vinculado" in r
    assert datos.pedidos == []
    assert repo.registro == [(None, "999", "mis_procesos", "SIN_VINCULO")]


async def test_un_cliente_o_alguien_inactivo_no_recibe_datos():
    repo = RepoFalso(chats={CHAT: CLIENTE, "556": CATALINA})
    datos = DatosFalsos(personas={CATALINA: {"nombres": "Catalina", "activo": False}})
    b = bot(repo, datos)

    for chat in (CHAT, "556"):
        r = await b.responder(chat, "/terminos")
        assert "solo para el equipo de la firma" in r
    assert datos.pedidos == []
    assert [x[3] for x in repo.registro] == ["NO_AUTORIZADO", "NO_AUTORIZADO"]


async def test_si_gabinete_no_responde_no_arriesga_datos():
    repo = RepoFalso()
    r = await bot(repo, DatosFalsos(falla=True)).responder(CHAT, "/terminos")

    assert "No pude comprobar su cuenta" in r
    assert repo.registro[0][3] == "ERROR"


async def test_limite_de_consultas_por_minuto():
    repo = RepoFalso()
    b = bot(repo, limite=2)
    await b.responder(CHAT, "/ayuda")
    await b.responder(CHAT, "/ayuda")
    r = await b.responder(CHAT, "/ayuda")

    assert "Espere un minuto" in r
    assert repo.registro[-1][3] == "LIMITE"


async def test_en_el_portal_la_persona_viene_de_la_sesion_y_la_ayuda_no_habla_de_comandos():
    repo, datos = RepoFalso(chats={}), DatosFalsos()
    b = bot(repo, datos)

    ayuda = await b.responder_portal(CATALINA, "hola")
    assert ayuda.startswith("Hola, Catalina. Soy Toga") and "/mis_procesos" not in ayuda
    assert "Tiene 2 procesos abiertos" in await b.responder_portal(CATALINA, "mis procesos")
    assert "etapa pruebas" in await b.responder_portal(CATALINA, "mis procesos")
    assert "solo para el equipo" in await b.responder_portal(CLIENTE, "mis procesos")   # un cliente, no
    assert [x[1] for x in repo.registro] == ["portal"] * 4
    assert [x[3] for x in repo.registro] == ["RESPONDIDA", "RESPONDIDA", "RESPONDIDA", "NO_AUTORIZADO"]


def test_las_etapas_llevan_tilde():
    from app.consultas_bot import _etiqueta
    assert _etiqueta("ADMISION") == "admisión"
    assert _etiqueta("SENTENCIA_ANTICIPADA") == "sentencia anticipada"


def test_preguntar_a_toga_desde_el_portal_con_la_sesion():
    from fastapi.testclient import TestClient
    from app.main import crear_app
    from tests.test_actuaciones_y_telegram import servicio as servicio_de_prueba
    from tests.test_aplicacion import RepoMemoria

    app = crear_app(servicio=servicio_de_prueba(RepoMemoria()), toga=bot(RepoFalso(chats={}), DatosFalsos()))
    with TestClient(app) as c:
        r = c.post("/notificaciones/toga", json={"texto": "qué me vence?"}, headers={"X-Usuario-Id": str(CATALINA)})
        assert r.status_code == 200 and r.json()["respuesta"].startswith("Tiene 2 término(s) pendiente(s)")
        assert c.post("/notificaciones/toga", json={"texto": "hola"}).status_code == 401           # sin sesión
        assert c.post("/notificaciones/toga", json={"texto": ""}, headers={"X-Usuario-Id": "1"}).status_code == 400
        sin_toga = crear_app(servicio=servicio_de_prueba(RepoMemoria()))
    with TestClient(sin_toga) as c:
        assert c.post("/notificaciones/toga", json={"texto": "hola"}, headers={"X-Usuario-Id": "1"}).status_code == 503


async def test_la_ayuda_saluda_y_explica_los_comandos():
    r = await bot().responder(CHAT, "hola")
    assert r.startswith("Hola, Catalina. Soy Toga")
    assert "/mis_procesos" in r and "/proceso" in r and "Solo consulto" in r


async def test_un_fallo_al_leer_no_rompe_la_respuesta_ni_el_registro():
    class Rota(DatosFalsos):
        async def terminos_de(self, id_persona):
            raise httpx.ReadTimeout("procesos lento")

    class RepoQueFalla(RepoFalso):
        async def registrar_consulta_bot(self, *a):
            raise RuntimeError("base caída")

    r = await bot(RepoQueFalla(), Rota()).responder(CHAT, "/terminos")
    assert "No pude consultar eso ahora" in r


# ------------------------------------------------------------------ el bot de Telegram con consultas

async def test_el_bot_contesta_en_privado_y_calla_en_grupos(caplog):
    enviados = []

    def api(req: httpx.Request):
        metodo = req.url.path.rsplit("/", 1)[-1]
        if metodo == "sendMessage":
            enviados.append(json.loads(req.content))
        return httpx.Response(200, json={"ok": True, "result": {"username": "gestion_juridica_bot"}})

    tg = TelegramBot("123:SECRETO", cliente=httpx.AsyncClient(transport=httpx.MockTransport(api)))
    await tg.iniciar()

    async def vincular(codigo, chat_id):
        return None

    async def consultar(chat_id, texto):
        return f"respuesta a {texto}"

    async def consultar_roto(chat_id, texto):
        raise RuntimeError("falla")

    with caplog.at_level(logging.ERROR):
        await tg._atender({"text": "/terminos", "chat": {"id": 555, "type": "private"}}, vincular, consultar)
        await tg._atender({"text": "/terminos", "chat": {"id": -100, "type": "group"}}, vincular, consultar)
        await tg._atender({"text": "/start", "chat": {"id": 556, "type": "private"}}, vincular, consultar)
        await tg._atender({"text": "hola", "chat": {"id": 557, "type": "private"}}, vincular, None)
        await tg._atender({"text": "hola", "chat": {"id": 558, "type": "private"}}, vincular, consultar_roto)

    assert [(e["chat_id"], e["text"]) for e in enviados] == [("555", "respuesta a /terminos"), ("556", "respuesta a /start")]
    assert "SECRETO" not in caplog.text


async def test_los_datos_se_piden_a_cada_servicio_con_el_token_de_servicio():
    pedidos = []

    def api(req: httpx.Request):
        pedidos.append((req.url.host, req.url.path, dict(req.url.params), req.headers.get("authorization")))
        if req.url.path == "/abogados/1":
            return httpx.Response(404)
        if req.url.path.startswith("/abogados/"):
            return httpx.Response(200, json={"nombres": "Catalina", "activo": True, "email": "c@f.test",
                                             "cargo": "Socio"})
        if req.url.path == "/abogados":
            return httpx.Response(200, json=[{"idPersona": 5, "nombres": "Ana", "apellidos": "Ruiz", "cargo": "Socio",
                                              "area": "Laboral", "email": "a@f.test", "tarifaHora": 1}])
        return httpx.Response(200, json=[{"x": 1}])

    class Token:
        async def cabeceras(self):
            return {"Authorization": "Bearer servicio"}

    transporte = httpx.MockTransport(api)
    original = httpx.AsyncClient.__init__

    def con_transporte(self, *a, **k):
        k["transport"] = transporte
        original(self, *a, **k)

    httpx.AsyncClient.__init__ = con_transporte
    try:
        d = DatosBotHttp("http://gabinete:8080/", "http://procesos:8080", Token())
        assert await d.persona(CATALINA) == {"nombres": "Catalina", "activo": True, "cargo": "Socio"}   # sin el correo
        assert await d.abogados() == [{"idPersona": 5, "nombres": "Ana", "apellidos": "Ruiz", "cargo": "Socio",
                                       "area": "Laboral"}]                         # sin correo ni tarifa
        assert await d.persona(1) is None
        assert await d.procesos_de(CATALINA) == [{"x": 1}]
        await d.terminos_de(CATALINA)
        await d.terminos_del_proceso(34)
        await d.audiencias_de(CATALINA, date(2026, 11, 12), date(2026, 11, 26))
    finally:
        httpx.AsyncClient.__init__ = original

    assert all(p[3] == "Bearer servicio" for p in pedidos)
    assert ("procesos", "/procesos/de-abogado/52345678", {}, "Bearer servicio") in pedidos
    assert ("procesos", "/terminos", {"idResponsable": "52345678"}, "Bearer servicio") in pedidos
    assert ("gabinete", "/abogados", {"soloActivos": "true"}, "Bearer servicio") in pedidos
    assert ("procesos", "/terminos", {"idProceso": "34"}, "Bearer servicio") in pedidos
    assert ("procesos", "/audiencias", {"desde": "2026-11-12", "hasta": "2026-11-26", "idAsistente": "52345678"},
            "Bearer servicio") in pedidos


# ------------------------------------------------------------------ SQL del repositorio (con un doble del pool)

class _Cursor:
    def __init__(self, filas):
        self.filas = filas

    async def fetchall(self):
        return self.filas


class _Conexion:
    def __init__(self, filas, sql):
        self.filas, self.sql = filas, sql

    async def execute(self, sql, params=()):
        self.sql.append((sql, params))
        return _Cursor(self.filas)


class _Pool:
    def __init__(self, filas):
        self.filas, self.sql = filas, []

    def connection(self):
        pool = self

        class _Ctx:
            async def __aenter__(self):
                return _Conexion(pool.filas, pool.sql)

            async def __aexit__(self, *a):
                return False

        return _Ctx()


async def test_un_chat_a_nombre_de_dos_personas_no_es_de_nadie():
    from app.infraestructura import RepositorioPostgres

    assert await RepositorioPostgres(_Pool([(CATALINA,)])).persona_de_chat(CHAT) == CATALINA
    assert await RepositorioPostgres(_Pool([])).persona_de_chat(CHAT) is None
    ambiguo = _Pool([(CATALINA,), (CLIENTE,)])
    assert await RepositorioPostgres(ambiguo).persona_de_chat(CHAT) is None
    assert "canal = 'TELEGRAM' AND activo AND destino = %s LIMIT 2" in " ".join(ambiguo.sql[0][0].split())

    registro = _Pool([])
    await RepositorioPostgres(registro).registrar_consulta_bot(CATALINA, CHAT, "terminos", "RESPONDIDA")
    assert registro.sql[0][1] == (CATALINA, CHAT, "terminos", "RESPONDIDA")
    assert "INSERT INTO notificaciones.consultas_bot" in registro.sql[0][0]


async def test_mis_procesos_marca_como_vencido_el_termino_con_fecha_pasada():
    datos = DatosFalsos()
    vencido = {**PROCESOS[1], "terminosPendientes": 1, "proximoVencimiento": "2026-10-16"}

    async def procesos_de(_):
        return [PROCESOS[0], vencido]
    datos.procesos_de = procesos_de
    r = await bot(datos=datos).responder(CHAT, "/mis_procesos")
    assert "1 término(s), VENCIDO desde el 16/10/2026" in r
    assert "2 término(s), 1 VENCIDO(S); el más antiguo desde el 10/11/2026" in r


async def test_mis_procesos_dice_todos_vencidos_y_el_mas_antiguo():
    datos = DatosFalsos()
    proceso = {**PROCESOS[0], "terminosPendientes": 2, "proximoVencimiento": "2026-10-16"}
    terminos = [{"radicadoLegible": proceso["radicadoLegible"], "estado": "PENDIENTE", "diasHabilesRestantes": d,
                 "fechaVencimiento": f, "descripcion": "x"} for d, f in ((-16, "2026-10-20"), (-18, "2026-10-16"))]

    async def procesos_de(_):
        return [proceso]

    async def terminos_de(_):
        return terminos
    datos.procesos_de, datos.terminos_de = procesos_de, terminos_de
    r = await bot(datos=datos).responder(CHAT, "/mis_procesos")
    assert "2 término(s), todos VENCIDOS; el más antiguo desde el 16/10/2026" in r


async def test_si_no_se_pueden_contar_los_vencidos_usa_la_fecha_del_proximo():
    datos = DatosFalsos()
    proceso = {**PROCESOS[0], "terminosPendientes": 3, "proximoVencimiento": "2026-10-16"}

    async def procesos_de(_):
        return [proceso]

    async def terminos_de(_):
        raise RuntimeError("procesos no responde")
    datos.procesos_de, datos.terminos_de = procesos_de, terminos_de
    r = await bot(datos=datos).responder(CHAT, "/mis_procesos")
    assert "3 término(s), 1 VENCIDO(S); el más antiguo desde el 16/10/2026" in r


async def test_la_ayuda_usa_un_radicado_propio_de_ejemplo():
    ayuda = await bot().responder(CHAT, "/ayuda")
    assert "/proceso 11001310303220260050100 — cómo va ese proceso suyo" in ayuda    # el primero abierto

    datos = DatosFalsos()

    async def sin_procesos(_):
        return []
    datos.procesos_de = sin_procesos
    assert "/proceso <radicado de 23 dígitos>" in await bot(datos=datos).responder(CHAT, "/ayuda")

    async def falla(_):
        raise RuntimeError("procesos no responde")
    datos.procesos_de = falla
    assert "/proceso <radicado de 23 dígitos>" in await bot(datos=datos).responder(CHAT, "/ayuda")


async def test_respuestas_cortas_a_registrar_y_quien_es_toga():
    registrar = await bot().responder(CHAT, "¿Puedo registrar un término?")
    assert registrar.startswith("No: solo consulto.") and "/mis_procesos" not in registrar
    quien = await bot().responder(CHAT, "¿Eres un bot?")
    assert quien.startswith("Soy Toga") and "No soy una persona" in quien and "/mis_procesos" not in quien
    portal = await bot().responder_portal(CATALINA, "quiero borrar un proceso")
    assert portal.startswith("No: solo consulto.") and "/ayuda" not in portal
    assert "/ayuda" not in await bot().responder_portal(CATALINA, "eres humano?")
