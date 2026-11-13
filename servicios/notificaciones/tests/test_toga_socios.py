"""Toga para los socios: el equipo, los procesos de un abogado y cualquier proceso de la firma."""
from app.consultas_bot import ConsultasBot
from tests.test_consultas_bot import CATALINA, CHAT, PROCESOS, RepoFalso, DatosFalsos

from datetime import date

ANDRES = 79123456
JULIAN = 80234567
LAURA = 1020456789

EQUIPO = [
    {"idPersona": ANDRES, "nombres": "Andrés Felipe", "apellidos": "Rodríguez Mejía", "cargo": "Socio", "area": "Civil y comercial"},
    {"idPersona": CATALINA, "nombres": "Catalina", "apellidos": "Gómez Restrepo", "cargo": "Socio", "area": "Laboral"},
    {"idPersona": JULIAN, "nombres": "Julián Andrés", "apellidos": "Torres Vargas", "cargo": "Abogado asociado", "area": None},
    {"idPersona": LAURA, "nombres": "Laura", "apellidos": "Torres Ríos", "cargo": "Abogado junior", "area": "Familia"},
]
DE_JULIAN = [{"idProceso": 90, "radicadoLegible": "05001-31-05-021-2026-00118-00", "asunto": "Despido sin justa causa",
              "rama": "Laboral", "tipoProceso": "Ordinario laboral", "etapa": "DEMANDA", "estado": "ACTIVO",
              "rol": "RESPONSABLE", "terminosPendientes": 1, "proximoVencimiento": "2026-11-20"}]


class DatosDelEquipo(DatosFalsos):
    def __init__(self, cargo="Socio", falla_de=None):
        super().__init__(personas={ANDRES: {"nombres": "Andrés Felipe", "activo": True, "cargo": cargo},
                                   CATALINA: {"nombres": "Catalina", "activo": True, "cargo": "Socio"}})
        self.falla_de = falla_de

    async def abogados(self):
        return EQUIPO

    async def procesos_de(self, id_persona):
        if id_persona == self.falla_de:
            raise RuntimeError("procesos no responde")
        return {ANDRES: [], CATALINA: PROCESOS, JULIAN: DE_JULIAN}.get(id_persona, [])

    async def terminos_de(self, id_persona):
        return []

    async def terminos_del_proceso(self, id_proceso):
        return [{"descripcion": "Subsanar la demanda", "fechaVencimiento": "2026-11-20", "estado": "PENDIENTE",
                 "diasHabilesRestantes": 6}] if id_proceso == 90 else []


def toga(datos):
    return ConsultasBot(RepoFalso(chats={CHAT: ANDRES}), datos, hoy=lambda: date(2026, 11, 12))


async def test_un_socio_ve_el_equipo_con_sus_procesos():
    r = await toga(DatosDelEquipo()).responder(CHAT, "abogados")
    assert r.startswith("El equipo tiene 4 personas activas:")
    assert "• Catalina Gómez Restrepo (Socio, Laboral) — 2 procesos abiertos" in r
    assert "• Julián Andrés Torres Vargas (Abogado asociado) — 1 proceso abierto" in r
    assert "• Andrés Felipe Rodríguez Mejía (Socio, Civil y comercial) — 0 procesos abiertos" in r
    assert r.endswith("Escriba /abogado y el nombre para ver sus procesos.")
    assert r == await toga(DatosDelEquipo()).responder(CHAT, "/abogados")


async def test_si_fallan_los_procesos_de_uno_lo_dice_sin_romperse():
    r = await toga(DatosDelEquipo(falla_de=JULIAN)).responder(CHAT, "/equipo")
    assert "Julián Andrés Torres Vargas (Abogado asociado) — procesos: sin datos ahora" in r


async def test_un_socio_pregunta_por_un_abogado_y_ve_sus_procesos():
    for pregunta in ("/abogado julian", "procesos de Julián", "casos del abogado Julián Torres", "abogado Julián Andrés"):
        r = await toga(DatosDelEquipo()).responder(CHAT, pregunta)
        assert r.startswith("Julián Andrés Torres Vargas tiene 1 proceso abierto:"), pregunta
        assert "05001-31-05-021-2026-00118-00 — Despido sin justa causa" in r


async def test_nombre_ambiguo_inexistente_o_vacio():
    ambiguo = await toga(DatosDelEquipo()).responder(CHAT, "/abogado Torres")
    assert ambiguo.startswith("Hay 2 personas con ese nombre. ¿Cuál?")
    assert "• Julián Andrés Torres Vargas" in ambiguo and "• Laura Torres Ríos" in ambiguo
    assert "No encuentro a nadie del equipo activo que se llame «pedro»" in \
        await toga(DatosDelEquipo()).responder(CHAT, "procesos de pedro")
    assert "Escriba el nombre del abogado" in await toga(DatosDelEquipo()).responder(CHAT, "/abogado")
    sin_procesos = await toga(DatosDelEquipo()).responder(CHAT, "/abogado Laura")
    assert sin_procesos == "Laura Torres Ríos no tiene procesos abiertos."


async def test_un_socio_consulta_cualquier_proceso_de_la_firma():
    r = await toga(DatosDelEquipo()).responder(CHAT, "/proceso 05001310502120260011800")
    assert r.startswith("05001-31-05-021-2026-00118-00 — Despido sin justa causa")
    assert "Subsanar la demanda" in r
    nada = await toga(DatosDelEquipo()).responder(CHAT, "/proceso 99999999999999999999999")
    assert "No encuentro el proceso 99999-99-99-999-9999-99999-99 en la firma." in nada


async def test_quien_no_es_socio_no_ve_el_equipo_ni_procesos_ajenos():
    datos = DatosDelEquipo(cargo="Abogado asociado")
    repo = RepoFalso(chats={CHAT: ANDRES})
    bot = ConsultasBot(repo, datos, hoy=lambda: date(2026, 11, 12))
    assert (await bot.responder(CHAT, "abogados")).startswith("Esa consulta es solo para los socios")
    assert (await bot.responder(CHAT, "procesos de Julián")).startswith("Esa consulta es solo para los socios")
    assert repo.registro[-1][3] == "NO_AUTORIZADO"
    ajeno = await bot.responder(CHAT, "/proceso 05001310502120260011800")
    assert "entre los suyos" in ajeno                              # como siempre: solo lo suyo
    assert "/abogados" not in await bot.responder(CHAT, "/ayuda")


async def test_la_ayuda_del_socio_explica_las_consultas_nuevas():
    ayuda = await toga(DatosDelEquipo()).responder(CHAT, "/ayuda")
    assert "Como socio, también:\n/abogados — el equipo" in ayuda and "/abogado <nombre>" in ayuda
    portal = await toga(DatosDelEquipo()).responder_portal(ANDRES, "hola")
    assert "escribir «abogados»" in portal and "/abogados" not in portal
    portal_equipo = await toga(DatosDelEquipo()).responder_portal(ANDRES, "quiénes son los abogados?")
    assert portal_equipo.endswith("Escriba «procesos de» y el nombre de un abogado para ver los suyos.")


async def test_mis_procesos_sigue_siendo_lo_suyo():
    r = await toga(DatosDelEquipo()).responder(CHAT, "mis procesos")
    assert r == "No tiene procesos abiertos."
