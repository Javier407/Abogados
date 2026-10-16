from fastapi.testclient import TestClient

from app.aplicacion import ServicioVigilancia
from app.dominio import Fuente, RadicadoVigilado
from app.main import crear_app
from tests.test_vigilancia import RAD, FuenteFija, Publicador, RepoMemoria


def cliente(repo=None):
    repo = repo or RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.MANUAL))
    s = ServicioVigilancia(repo, {Fuente.SIMULADA: FuenteFija()}, Publicador(), pausa_portal_seg=0)
    return TestClient(crear_app(servicio=s))


def test_lista_los_radicados_con_su_radicado_legible():
    with cliente() as c:
        r = c.get("/vigilancia/radicados").json()
        assert r[0]["radicadoLegible"] == "11001-31-03-032-2026-00501-00"
        assert r[0]["fuente"] == "MANUAL" and r[0]["enAlerta"] is False


def test_fuentes_disponibles():
    with cliente() as c:
        assert c.get("/vigilancia/fuentes").json()["disponibles"] == ["MANUAL", "SIMULADA"]


def test_configurar_y_simular_una_actuacion():
    with cliente() as c:
        r = c.put(f"/vigilancia/radicados/{RAD}", json={"fuente": "SIMULADA", "frecuenciaHoras": 1})
        assert r.status_code == 200 and r.json()["fuente"] == "SIMULADA"
        r = c.post(f"/vigilancia/radicados/{RAD}/simuladas",
                   json={"fecha": "2026-10-15", "actuacion": "Auto admite demanda"})
        assert r.status_code == 201


def test_errores_con_el_formato_comun():
    with cliente() as c:
        r = c.post("/vigilancia/radicados/123/consultar")
        assert r.status_code == 422 and "23 dígitos" in r.json()["error"]
        r = c.post("/vigilancia/radicados/11001310303220269999900/consultar")
        assert r.status_code == 404 and "error" in r.json()
        r = c.post(f"/vigilancia/radicados/{RAD}/consultar")          # fuente MANUAL: no se consulta
        assert r.status_code == 422
        r = c.put(f"/vigilancia/radicados/{RAD}", json={"fuente": "INVENTADA"})
        assert r.status_code == 400 and "fuente" in r.json()["campos"]
