"""La API REST con el servicio y sus dobles (sin conectar PostgreSQL ni RabbitMQ)."""
import asyncio
from datetime import date

from fastapi.testclient import TestClient

from app.aplicacion import ServicioNotificaciones
from app.infraestructura import AvisosEnVivoMemoria
from app.main import crear_app
from tests.test_aplicacion import ABOGADO, EVENTO, CorreoFalso, DirectorioFalso, Registro, RepoMemoria


def cliente():
    repo = RepoMemoria()
    vivo = AvisosEnVivoMemoria()
    s = ServicioNotificaciones(repo, CorreoFalso(), DirectorioFalso(), vivo, Registro(), hoy=lambda: date(2026, 10, 15))
    asyncio.run(s.procesar("termino.por_vencer", EVENTO))
    return TestClient(crear_app(servicio=s, en_vivo=vivo))


def test_salud():
    with cliente() as c:
        assert c.get("/salud").json() == {"estado": "ok"}


def test_lista_y_cuenta_los_avisos_de_la_persona():
    with cliente() as c:
        assert len(c.get("/notificaciones", params={"idPersona": ABOGADO}).json()) == 1
        assert c.get("/notificaciones/resumen", params={"idPersona": ABOGADO}).json()["noLeidas"] == 1
        assert c.get("/notificaciones", params={"idPersona": 1}).json() == []


def test_marcar_leida():
    with cliente() as c:
        assert c.post("/notificaciones/1/leida", params={"idPersona": ABOGADO}).status_code == 200
        assert c.get("/notificaciones/resumen", params={"idPersona": ABOGADO}).json()["noLeidas"] == 0


def test_aviso_de_otra_persona_es_404_con_el_formato_de_error_comun():
    with cliente() as c:
        r = c.post("/notificaciones/1/leida", params={"idPersona": 5})
        assert r.status_code == 404
        assert "error" in r.json()


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
