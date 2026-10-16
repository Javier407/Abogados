"""La API REST con el servicio y sus dobles (sin conectar PostgreSQL ni RabbitMQ)."""
import asyncio
from datetime import date

from fastapi.testclient import TestClient

from app.aplicacion import ServicioNotificaciones
from app.infraestructura import AvisosEnVivoMemoria
from app.main import crear_app
from tests.test_aplicacion import ABOGADO, EVENTO, CorreoFalso, DirectorioFalso, Registro, RepoMemoria

# Lo que el gateway agrega a cada petición a partir del token verificado
COMO_ABOGADO = {"X-Usuario-Id": str(ABOGADO), "X-Usuario-Roles": "ABOGADO,SOCIO"}


def cliente(cabeceras=COMO_ABOGADO):
    repo = RepoMemoria()
    vivo = AvisosEnVivoMemoria()
    s = ServicioNotificaciones(repo, CorreoFalso(), DirectorioFalso(), vivo, Registro(), hoy=lambda: date(2026, 10, 15))
    asyncio.run(s.procesar("termino.por_vencer", EVENTO))
    return TestClient(crear_app(servicio=s, en_vivo=vivo), headers=cabeceras)


def test_salud():
    with cliente({}) as c:
        assert c.get("/salud").json() == {"estado": "ok"}


def test_lista_y_cuenta_los_avisos_de_la_persona():
    with cliente() as c:
        assert len(c.get("/notificaciones", params={"idPersona": ABOGADO}).json()) == 1
        assert c.get("/notificaciones/resumen", params={"idPersona": ABOGADO}).json()["noLeidas"] == 1


def test_marcar_leida():
    with cliente() as c:
        assert c.post("/notificaciones/1/leida", params={"idPersona": ABOGADO}).status_code == 200
        assert c.get("/notificaciones/resumen", params={"idPersona": ABOGADO}).json()["noLeidas"] == 0


def test_nadie_ve_ni_marca_los_avisos_de_otro():
    with cliente() as c:
        r = c.get("/notificaciones", params={"idPersona": 5})
        assert r.status_code == 403 and r.json()["error"] == "Solo puede consultar sus propios avisos"
        assert c.post("/notificaciones/1/leida", params={"idPersona": 5}).status_code == 403


def test_un_admin_si_puede_consultar_los_de_otra_persona():
    with cliente({"X-Usuario-Id": "1", "X-Usuario-Roles": "ADMIN"}) as c:
        assert len(c.get("/notificaciones", params={"idPersona": ABOGADO}).json()) == 1


def test_sin_la_identidad_del_gateway_es_401():
    with cliente({}) as c:
        r = c.get("/notificaciones", params={"idPersona": ABOGADO})
        assert r.status_code == 401 and "error" in r.json()


def test_falta_la_persona_es_400_con_campos():
    with cliente() as c:
        r = c.get("/notificaciones")
        assert r.status_code == 400
        assert "idPersona" in r.json()["campos"]


async def test_avisos_en_vivo_llegan_solo_a_la_persona_suscrita():
    vivo = AvisosEnVivoMemoria()
    async with vivo.suscribir(ABOGADO) as cola, vivo.suscribir(1) as otra:
        await vivo.publicar(ABOGADO, {"titulo": "Término por vencer"})
        assert (await asyncio.wait_for(cola.get(), 1))["titulo"] == "Término por vencer"
        assert otra.empty()
    await vivo.publicar(ABOGADO, {"titulo": "sin nadie escuchando"})     # no falla sin suscriptores
