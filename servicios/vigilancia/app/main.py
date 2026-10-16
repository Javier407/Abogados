"""API REST del servicio vigilancia. Contrato: docs/contratos/vigilancia.openapi.yaml."""
from __future__ import annotations

import asyncio
import logging
import os
from contextlib import asynccontextmanager
from datetime import date

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from .aplicacion import ServicioVigilancia
from .dominio import Fuente, RadicadoVigilado, validar_radicado

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("vigilancia")


class Configuracion(BaseModel):
    fuente: Fuente
    frecuenciaHoras: int = Field(24, ge=1, le=168)
    activo: bool = True


class ActuacionSimulada(BaseModel):
    fecha: date
    actuacion: str = Field(..., min_length=3, max_length=200)
    anotacion: str = Field("", max_length=1000)


def a_json(rv: RadicadoVigilado) -> dict:
    r = rv.radicado
    return {
        "radicado": r,
        "radicadoLegible": "-".join((r[0:5], r[5:7], r[7:9], r[9:12], r[12:16], r[16:21], r[21:23])),
        "idProceso": rv.id_proceso, "fuente": rv.fuente.value, "frecuenciaHoras": rv.frecuencia_horas,
        "activo": rv.activo, "ultimaConsulta": rv.ultima_consulta.isoformat() if rv.ultima_consulta else None,
        "fallosConsecutivos": rv.fallos_consecutivos, "enAlerta": rv.en_alerta, "despacho": rv.despacho,
        "ultimaActuacion": rv.ultima_actuacion.isoformat() if rv.ultima_actuacion else None,
        "ultimoError": rv.ultimo_error, "lineaBaseHecha": rv.linea_base_hecha,
    }


async def _tarea_periodica(servicio: ServicioVigilancia, minutos: float) -> None:
    """Sincroniza los procesos vigilados al arrancar y luego revisa cada N minutos a quién le toca consulta."""
    while True:
        try:
            agregados = await servicio.sincronizar()
            if agregados:
                log.info("%s radicado(s) agregados a la vigilancia al sincronizar con procesos", agregados)
            break
        except Exception as e:   # noqa: BLE001 - procesos puede no estar listo todavía
            log.warning("No se pudo sincronizar con procesos (%s); reintento en 30 s", e)
            await asyncio.sleep(30)
    while True:
        try:
            hechas = await servicio.ciclo()
            if hechas:
                log.info("Ciclo de vigilancia: %s consulta(s)", hechas)
        except Exception:   # noqa: BLE001
            log.exception("Error en el ciclo de vigilancia")
        await asyncio.sleep(minutos * 60)


def crear_app(servicio: ServicioVigilancia | None = None) -> FastAPI:
    """Sin argumentos conecta PostgreSQL, RabbitMQ y las fuentes; en las pruebas se pasa un servicio con dobles."""

    @asynccontextmanager
    async def ciclo_de_vida(app: FastAPI):
        if servicio is not None:
            app.state.servicio = servicio
            yield
            return

        from psycopg_pool import AsyncConnectionPool

        from .infraestructura import (ClienteProcesos, FuentePortalHttp, FuenteSimulada, Mensajeria,
                                      RepositorioPostgres)

        e = os.environ.get
        pool = AsyncConnectionPool(e("DB_URL", "postgresql://svc_vigilancia@localhost:5432/gabinete"),
                                   min_size=1, max_size=4, open=False)
        await pool.open()
        repo = RepositorioPostgres(pool)
        fuentes = {Fuente.SIMULADA: FuenteSimulada(repo)}
        if e("PORTAL_HABILITADO", "true").lower() == "true":
            fuentes[Fuente.PORTAL_HTTP] = FuentePortalHttp(
                e("PORTAL_URL", "https://consultaprocesos.ramajudicial.gov.co:448/api/v2"))
        mensajeria = Mensajeria(e("RABBITMQ_URL", "amqp://guest:guest@localhost/"),
                                e("EVENTOS_EXCHANGE", "gabinete.eventos"))
        srv = ServicioVigilancia(repo, fuentes, mensajeria, ClienteProcesos(e("PROCESOS_URL", "http://localhost:8082")),
                                 pausa_portal_seg=float(e("PAUSA_PORTAL_SEG", "10")))
        app.state.servicio = srv
        tareas = [asyncio.create_task(mensajeria.iniciar(srv.proceso_creado)),
                  asyncio.create_task(_tarea_periodica(srv, float(e("CICLO_MINUTOS", "10"))))]
        try:
            yield
        finally:
            for t in tareas:
                t.cancel()
            await mensajeria.cerrar()
            await pool.close()

    app = FastAPI(title="Servicio vigilancia", version="0.1.0", lifespan=ciclo_de_vida)

    def srv(request: Request) -> ServicioVigilancia:
        return request.app.state.servicio

    def radicado_valido(radicado: str) -> str:
        try:
            return validar_radicado(radicado)
        except ValueError as ex:
            raise HTTPException(422, detail=str(ex)) from ex

    @app.get("/salud")
    async def salud():
        return {"estado": "ok"}

    @app.get("/vigilancia/fuentes")
    async def fuentes(request: Request):
        disponibles = [Fuente.MANUAL.value] + [f.value for f in srv(request).fuentes]
        return {"disponibles": disponibles}

    @app.get("/vigilancia/radicados")
    async def listar(request: Request):
        return [a_json(rv) for rv in await srv(request).radicados()]

    @app.put("/vigilancia/radicados/{radicado}")
    async def configurar(request: Request, radicado: str, cfg: Configuracion):
        rv = await srv(request).configurar(radicado_valido(radicado), cfg.fuente, cfg.frecuenciaHoras, cfg.activo)
        return a_json(rv)

    @app.post("/vigilancia/radicados/{radicado}/consultar")
    async def consultar(request: Request, radicado: str):
        return await srv(request).consultar(radicado_valido(radicado))

    @app.get("/vigilancia/radicados/{radicado}/consultas")
    async def historial(request: Request, radicado: str):
        return await srv(request).historial(radicado_valido(radicado))

    @app.post("/vigilancia/radicados/{radicado}/simuladas", status_code=201)
    async def simulada(request: Request, radicado: str, a: ActuacionSimulada):
        id_ = await srv(request).agregar_simulada(radicado_valido(radicado), a.fecha, a.actuacion, a.anotacion)
        return {"id": id_, "mensaje": "Actuación simulada agregada; aparecerá como nueva en la próxima consulta"}

    @app.post("/vigilancia/sincronizar")
    async def sincronizar(request: Request):
        return {"agregados": await srv(request).sincronizar()}

    # Mismo formato de error que los demás servicios: { "error": ..., "campos": {...} }
    @app.exception_handler(LookupError)
    async def no_encontrado(request: Request, exc: LookupError):
        return JSONResponse({"error": str(exc).strip("'\"")}, status_code=404)

    @app.exception_handler(ValueError)
    async def regla(request: Request, exc: ValueError):
        return JSONResponse({"error": str(exc)}, status_code=422)

    @app.exception_handler(HTTPException)
    async def error_http(request: Request, exc: HTTPException):
        return JSONResponse({"error": exc.detail}, status_code=exc.status_code)

    @app.exception_handler(RequestValidationError)
    async def error_validacion(request: Request, exc: RequestValidationError):
        campos = {str(err["loc"][-1]): err["msg"] for err in exc.errors()}
        return JSONResponse({"error": "Hay datos inválidos en la petición", "campos": campos}, status_code=400)

    return app


app = crear_app()
