"""Dominio, casos de uso y fuente del portal con dobles: sin base de datos, RabbitMQ ni internet."""
from datetime import date, datetime, timedelta, timezone

import httpx
import pytest

from app.aplicacion import ServicioVigilancia
from app.dominio import Actuacion, ErrorFuente, Fuente, RadicadoVigilado, ResultadoConsulta, nuevas, validar_radicado
from app.infraestructura import FuentePortalHttp

RAD = "11001310303220260050100"
AHORA = datetime(2026, 10, 15, 15, 0, tzinfo=timezone.utc)


def act(ref: str, dia: int, texto: str = "Auto fija fecha de audiencia") -> Actuacion:
    return Actuacion(ref, date(2026, 10, dia), texto)


# ------------------------------------------------------------------ dominio

def test_solo_se_consultan_las_fuentes_automaticas_y_cuando_toca():
    rv = RadicadoVigilado(RAD, 1, Fuente.SIMULADA, frecuencia_horas=24)
    assert rv.toca_consultar(AHORA)
    rv.ultima_consulta = AHORA - timedelta(hours=23)
    assert not rv.toca_consultar(AHORA)
    rv.ultima_consulta = AHORA - timedelta(hours=24)
    assert rv.toca_consultar(AHORA)
    assert not RadicadoVigilado(RAD, 1, Fuente.MANUAL).toca_consultar(AHORA)
    assert not RadicadoVigilado(RAD, 1, Fuente.PORTAL_HTTP, activo=False).toca_consultar(AHORA)


def test_nuevas_descarta_conocidas_y_repetidas_y_ordena_por_fecha():
    consultadas = [act("P:3", 14), act("P:2", 10), act("P:1", 8), act("P:3", 14)]
    assert [a.referencia for a in nuevas(consultadas, {"P:1"})] == ["P:2", "P:3"]


def test_clasifica_sentencias():
    assert Actuacion("x", date(2026, 1, 1), "Sentencia de primera instancia").tipo == "SENTENCIA"
    assert Actuacion("x", date(2026, 1, 1), "Fijación estado", "Auto admite demanda").tipo == "AUTO"


def test_valida_radicado():
    assert validar_radicado("11001-31-03-032-2026-00501-00") == RAD
    with pytest.raises(ValueError):
        validar_radicado("123")


# ------------------------------------------------------------------ dobles

class RepoMemoria:
    def __init__(self, *radicados: RadicadoVigilado):
        self.datos = {r.radicado: r for r in radicados}
        self.conocidas: dict[str, set[str]] = {}
        self.consultas_hechas: list[tuple] = []
        self.simuladas: list[tuple] = []

    async def radicados(self):
        return list(self.datos.values())

    async def radicado(self, radicado):
        return self.datos.get(radicado)

    async def registrar(self, radicado, id_proceso, fuente):
        if radicado in self.datos:
            return False
        self.datos[radicado] = RadicadoVigilado(radicado, id_proceso, fuente)
        return True

    async def configurar(self, radicado, fuente, frecuencia_horas, activo):
        rv = self.datos[radicado]
        if rv.fuente != fuente:
            rv.linea_base_hecha = False
        rv.fuente, rv.frecuencia_horas, rv.activo = fuente, frecuencia_horas, activo

    async def referencias_conocidas(self, radicado):
        return set(self.conocidas.get(radicado, set()))

    async def guardar_conocidas(self, radicado, actuaciones, avisadas):
        self.conocidas.setdefault(radicado, set()).update(a.referencia for a in actuaciones)

    async def registrar_consulta(self, radicado, exitosa, nuevas, detalle, despacho=None, ultima_actuacion=None):
        rv = self.datos[radicado]
        rv.ultima_consulta = AHORA
        rv.fallos_consecutivos = 0 if exitosa else rv.fallos_consecutivos + 1
        rv.linea_base_hecha = rv.linea_base_hecha or exitosa
        self.consultas_hechas.append((radicado, exitosa, nuevas, detalle))

    async def consultas(self, radicado, limite):
        return [c for c in self.consultas_hechas if c[0] == radicado][:limite]

    async def agregar_simulada(self, radicado, fecha, actuacion, anotacion):
        self.simuladas.append((radicado, fecha, actuacion))
        return len(self.simuladas)


class FuenteFija:
    def __init__(self, *actuaciones, falla=False, encontrado=True):
        self.actuaciones = list(actuaciones)
        self.falla = falla
        self.encontrado = encontrado

    async def consultar(self, radicado):
        if self.falla:
            raise ErrorFuente("tiempo de espera agotado")
        return ResultadoConsulta(self.encontrado, list(self.actuaciones), "Juzgado 032 Civil del Circuito")


class Publicador:
    def __init__(self, falla=False):
        self.eventos = []
        self.falla = falla

    async def actuacion_detectada(self, rv, a, fuente):
        if self.falla:
            raise ConnectionError("RabbitMQ caído")
        self.eventos.append((rv.radicado, a.referencia))


def servicio(repo, fuente, publicador=None):
    return ServicioVigilancia(repo, {Fuente.SIMULADA: fuente}, publicador or Publicador(), pausa_portal_seg=0,
                              ahora=lambda: AHORA)


# ------------------------------------------------------------------ casos de uso

async def test_la_primera_consulta_es_linea_base_y_no_avisa():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA))
    fuente, pub = FuenteFija(act("S:1", 1), act("S:2", 5)), Publicador()

    r = await servicio(repo, fuente, pub).consultar(RAD)

    assert r["lineaBase"] and r["nuevas"] == 0
    assert pub.eventos == []
    assert repo.conocidas[RAD] == {"S:1", "S:2"}


async def test_una_actuacion_nueva_se_publica_una_sola_vez():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA))
    fuente, pub = FuenteFija(act("S:1", 1)), Publicador()
    s = servicio(repo, fuente, pub)
    await s.consultar(RAD)                         # línea base

    fuente.actuaciones.append(act("S:2", 14, "Auto decreta pruebas"))
    r = await s.consultar(RAD)
    otra_vez = await s.consultar(RAD)

    assert r["nuevas"] == 1 and not r["lineaBase"]
    assert pub.eventos == [(RAD, "S:2")]
    assert otra_vez["nuevas"] == 0


async def test_linea_base_vacia_no_impide_detectar_la_siguiente():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA))
    fuente, pub = FuenteFija(), Publicador()
    s = servicio(repo, fuente, pub)
    await s.consultar(RAD)                         # sin actuaciones todavía

    fuente.actuaciones.append(act("S:1", 15))
    assert (await s.consultar(RAD))["nuevas"] == 1


async def test_si_no_se_puede_publicar_no_se_marca_como_conocida():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA, linea_base_hecha=True))
    repo.conocidas[RAD] = {"S:1"}
    fuente = FuenteFija(act("S:1", 1), act("S:2", 14))

    r = await servicio(repo, fuente, Publicador(falla=True)).consultar(RAD)

    assert not r["exitosa"]
    assert repo.conocidas[RAD] == {"S:1"}           # S:2 se volverá a detectar
    pub = Publicador()
    assert (await servicio(repo, fuente, pub).consultar(RAD))["nuevas"] == 1


async def test_los_fallos_se_cuentan_y_ponen_el_radicado_en_alerta():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA))
    s = servicio(repo, FuenteFija(falla=True))
    for _ in range(3):
        assert not (await s.consultar(RAD))["exitosa"]
    assert repo.datos[RAD].fallos_consecutivos == 3 and repo.datos[RAD].en_alerta


async def test_radicado_no_encontrado_cuenta_como_fallo():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA))
    r = await servicio(repo, FuenteFija(encontrado=False)).consultar(RAD)
    assert not r["exitosa"] and "no aparece" in r["error"]


async def test_un_proceso_nuevo_entra_como_manual():
    repo = RepoMemoria()
    s = servicio(repo, FuenteFija())
    assert await s.proceso_creado({"id_proceso": 34, "radicado": RAD, "id_abogado_responsable": 1})
    assert not await s.proceso_creado({"id_proceso": 34, "radicado": RAD, "id_abogado_responsable": 1})
    assert repo.datos[RAD].fuente == Fuente.MANUAL


async def test_el_ciclo_solo_consulta_a_quien_le_toca():
    al_dia = RadicadoVigilado("11001310303220260000100", 2, Fuente.SIMULADA, ultima_consulta=AHORA)
    manual = RadicadoVigilado("11001310303220260000200", 3, Fuente.MANUAL)
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.SIMULADA), al_dia, manual)
    assert await servicio(repo, FuenteFija()).ciclo() == 1


async def test_reglas_de_configuracion():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.MANUAL))
    s = ServicioVigilancia(repo, {Fuente.SIMULADA: FuenteFija(), Fuente.PORTAL_HTTP: FuenteFija()}, Publicador())
    with pytest.raises(ValueError, match="12 horas"):
        await s.configurar(RAD, Fuente.PORTAL_HTTP, 6, True)
    with pytest.raises(LookupError):
        await s.configurar("11001310303220269999900", Fuente.SIMULADA, 24, True)
    rv = await s.configurar(RAD, Fuente.SIMULADA, 1, True)
    assert rv.fuente == Fuente.SIMULADA and not rv.linea_base_hecha


async def test_solo_se_simulan_actuaciones_en_radicados_simulados():
    repo = RepoMemoria(RadicadoVigilado(RAD, 1, Fuente.MANUAL))
    s = servicio(repo, FuenteFija())
    with pytest.raises(ValueError, match="SIMULADA"):
        await s.agregar_simulada(RAD, date(2026, 10, 15), "Auto", "")


# ------------------------------------------------------------------ fuente del portal (respuestas simuladas)

BUSQUEDA = {"tipoConsulta": "NumeroRadicacion", "procesos": [
    {"idProceso": 111, "llaveProceso": RAD, "fechaUltimaActuacion": "2026-09-01T00:00:00",
     "despacho": "JUZGADO 032 CIVIL DEL CIRCUITO DE BOGOTÁ ", "esPrivado": False},
    {"idProceso": 222, "llaveProceso": RAD, "fechaUltimaActuacion": "2026-10-14T00:00:00",
     "despacho": "JUZGADO 032 CIVIL  DEL CIRCUITO DE BOGOTÁ", "esPrivado": False}]}
ACTUACIONES = {"actuaciones": [
    {"idRegActuacion": 9002, "llaveProceso": RAD, "consActuacion": 2, "fechaActuacion": "2026-10-14T00:00:00",
     "actuacion": "Auto  fija fecha", "anotacion": "Audiencia el 3 de noviembre", "fechaInicial": None,
     "fechaFinal": None, "fechaRegistro": "2026-10-14T00:00:00", "codRegla": "00", "conDocumentos": False, "cant": 2},
    {"idRegActuacion": 9001, "llaveProceso": RAD, "consActuacion": 1, "fechaActuacion": "2026-09-01T00:00:00",
     "actuacion": "Radicación de proceso", "anotacion": None, "fechaInicial": None, "fechaFinal": None,
     "fechaRegistro": "2026-09-01T00:00:00", "codRegla": "00", "conDocumentos": False, "cant": 2}],
    "paginacion": {"cantidadRegistros": 2}}


def portal(manejador) -> FuentePortalHttp:
    return FuentePortalHttp("https://portal.test/api/v2",
                            cliente=httpx.AsyncClient(transport=httpx.MockTransport(manejador)))


async def test_portal_lee_el_proceso_mas_reciente_y_sus_actuaciones():
    pedidas = []

    def manejador(req: httpx.Request):
        pedidas.append(req.url.path)
        if req.url.path.endswith("NumeroRadicacion"):
            assert req.url.params["numero"] == RAD
            return httpx.Response(200, json=BUSQUEDA)
        return httpx.Response(200, json=ACTUACIONES)

    r = await portal(manejador).consultar(RAD)

    assert pedidas[-1].endswith("/Proceso/Actuaciones/222")
    assert r.encontrado and r.despacho == "JUZGADO 032 CIVIL DEL CIRCUITO DE BOGOTÁ"
    assert [a.referencia for a in r.actuaciones] == ["PORTAL:9002", "PORTAL:9001"]
    assert r.actuaciones[0].actuacion == "Auto fija fecha" and r.actuaciones[0].fecha == date(2026, 10, 14)
    assert r.actuaciones[1].anotacion == ""


async def test_portal_sin_el_radicado_o_privado_es_no_encontrado():
    privado = {"procesos": [{"idProceso": 1, "llaveProceso": RAD, "esPrivado": True}]}
    r = await portal(lambda req: httpx.Response(200, json=privado)).consultar(RAD)
    assert not r.encontrado


async def test_portal_caido_es_error_de_fuente():
    with pytest.raises(ErrorFuente, match="HTTP 503"):
        await portal(lambda req: httpx.Response(503)).consultar(RAD)

    def sin_red(req):
        raise httpx.ConnectTimeout("timeout")

    with pytest.raises(ErrorFuente, match="no respondió"):
        await portal(sin_red).consultar(RAD)
