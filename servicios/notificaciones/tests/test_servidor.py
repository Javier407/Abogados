"""F12: estado del servidor (lo que escribe el vigía) para la pantalla del administrador."""
import asyncio
from datetime import datetime, timedelta, timezone

from fastapi.testclient import TestClient

from app.aplicacion import ConsultaServidor, ServicioNotificaciones
from app.infraestructura import AvisosEnVivoMemoria, RepositorioServidorPostgres
from app.main import crear_app
from tests.test_aplicacion import CorreoFalso, DirectorioFalso, Registro, RepoMemoria

AHORA = datetime(2026, 10, 22, 15, 0, tzinfo=timezone.utc)
COMO_ADMIN = {"X-Usuario-Id": "1", "X-Usuario-Roles": "ADMIN"}


class ServidorFalso:
    def __init__(self, ultima_hace=timedelta(minutes=3)):
        self.ultima_hace = ultima_hace
        self.pedidos = []

    async def ultima_revision(self):
        if self.ultima_hace is None:
            return None
        return {"momento": AHORA - self.ultima_hace, "disco": 41, "memoriaMb": 3200, "respaldoHoras": 12,
                "appOk": True, "problemas": 0, "detalle": []}

    async def serie(self, desde, por_hora):
        self.pedidos.append((desde, por_hora))
        return [{"momento": AHORA - timedelta(hours=2), "disco": 40, "memoriaMb": 3300, "appOk": True,
                 "problemas": 0, "revisiones": 12, "sinApp": 0},
                {"momento": AHORA - timedelta(hours=1), "disco": 41, "memoriaMb": 900, "appOk": False,
                 "problemas": 2, "revisiones": 12, "sinApp": 3}]

    async def incidentes(self, desde):
        return [{"clave": "https", "mensaje": "La app no responde por HTTPS", "inicio": AHORA - timedelta(minutes=50),
                 "fin": AHORA - timedelta(minutes=35)},
                {"clave": "disco", "mensaje": "Disco al 91 %", "inicio": AHORA - timedelta(minutes=20), "fin": None}]


def test_resume_la_ultima_revision_la_disponibilidad_y_los_incidentes():
    repo = ServidorFalso()
    e = asyncio.run(ConsultaServidor(repo, ahora=lambda: AHORA).estado(7))

    assert e["vigiaActivo"] is True and e["ultima"]["disco"] == 41
    assert e["revisiones"] == 24 and e["disponibilidad"] == 87.5       # 21 de 24 revisiones con la app arriba
    assert repo.pedidos == [(AHORA - timedelta(days=7), True)]           # más de 2 días: por hora
    assert e["serie"][1]["momento"] == (AHORA - timedelta(hours=1)).isoformat()
    https, disco = e["incidentes"]
    assert https["abierto"] is False and https["minutos"] == 15
    assert disco["abierto"] is True and disco["fin"] is None and disco["minutos"] == 20


def test_un_dia_se_ve_revision_por_revision_y_sin_revisiones_no_hay_disponibilidad():
    class Vacio(ServidorFalso):
        async def serie(self, desde, por_hora):
            self.pedidos.append((desde, por_hora))
            return []

    repo = Vacio(ultima_hace=None)
    e = asyncio.run(ConsultaServidor(repo, ahora=lambda: AHORA).estado(1))
    assert repo.pedidos[0][1] is False
    assert e["ultima"] is None and e["vigiaActivo"] is False and e["disponibilidad"] is None


def test_si_el_vigia_lleva_mas_de_15_minutos_sin_revisar_se_avisa():
    e = asyncio.run(ConsultaServidor(ServidorFalso(timedelta(minutes=16)), ahora=lambda: AHORA).estado(1))
    assert e["vigiaActivo"] is False


def cliente(cabeceras, servidor=None):
    s = ServicioNotificaciones(RepoMemoria(), CorreoFalso(), DirectorioFalso(), AvisosEnVivoMemoria(), Registro())
    return TestClient(crear_app(servicio=s, servidor=servidor), headers=cabeceras)


def test_la_api_es_solo_para_el_administrador():
    servidor = ConsultaServidor(ServidorFalso(), ahora=lambda: AHORA)
    with cliente(COMO_ADMIN, servidor) as c:
        r = c.get("/notificaciones/servidor", params={"dias": 30})
        assert r.status_code == 200 and r.json()["dias"] == 30
        assert c.get("/notificaciones/servidor", params={"dias": 91}).status_code == 400
    with cliente({"X-Usuario-Id": "5", "X-Usuario-Roles": "SOCIO,ABOGADO"}, servidor) as c:
        r = c.get("/notificaciones/servidor")
        assert r.status_code == 403 and r.json()["error"] == "Solo el administrador ve el estado del servidor"
    with cliente({}, servidor) as c:
        assert c.get("/notificaciones/servidor").status_code == 401
    with cliente(COMO_ADMIN) as c:
        assert c.get("/notificaciones/servidor").status_code == 503


# ------------------------------------------------------------------ el repositorio PostgreSQL con dobles

class Cursor:
    def __init__(self, filas):
        self.filas = filas

    async def fetchone(self):
        return self.filas[0] if self.filas else None

    async def fetchall(self):
        return self.filas


class Conexion:
    def __init__(self, filas):
        self.filas = filas
        self.sql = []

    async def execute(self, sql, params=None):
        self.sql.append((sql, params))
        return Cursor(self.filas)


class Pool:
    def __init__(self, con):
        self.con = con

    def connection(self):
        con = self.con

        class Contexto:
            async def __aenter__(self):
                return con

            async def __aexit__(self, *a):
                return False

        return Contexto()


def test_el_repositorio_lee_lo_que_escribe_el_vigia():
    con = Conexion([(AHORA, 41, 3200, None, True, 1, "Disco al 91 %\n\n")])
    ultima = asyncio.run(RepositorioServidorPostgres(Pool(con)).ultima_revision())
    assert ultima["detalle"] == ["Disco al 91 %"] and ultima["respaldoHoras"] is None
    assert "ORDER BY momento DESC LIMIT 1" in con.sql[0][0]
    assert asyncio.run(RepositorioServidorPostgres(Pool(Conexion([]))).ultima_revision()) is None

    con = Conexion([(AHORA, 40, 3300, True, 0, 12, 0)])
    repo = RepositorioServidorPostgres(Pool(con))
    assert asyncio.run(repo.serie(AHORA, por_hora=True))[0]["revisiones"] == 12
    assert "date_trunc('hour'" in con.sql[0][0] and con.sql[0][1] == (AHORA,)
    asyncio.run(repo.serie(AHORA, por_hora=False))
    assert "date_trunc" not in con.sql[1][0]

    con = Conexion([("disco", "Disco al 91 %", AHORA, None)])
    assert asyncio.run(RepositorioServidorPostgres(Pool(con)).incidentes(AHORA)) == [
        {"clave": "disco", "mensaje": "Disco al 91 %", "inicio": AHORA, "fin": None}]
