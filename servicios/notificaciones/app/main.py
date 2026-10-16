"""API REST y SSE del servicio notificaciones. Contrato: docs/contratos/notificaciones.openapi.yaml."""
from __future__ import annotations

import asyncio
import json
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, StreamingResponse

from .aplicacion import ServicioNotificaciones
from .infraestructura import AvisosEnVivoMemoria

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("notificaciones")

LATIDO_SEGUNDOS = 25


def crear_app(servicio: ServicioNotificaciones | None = None,
              en_vivo: AvisosEnVivoMemoria | None = None) -> FastAPI:
    """Sin argumentos conecta PostgreSQL, SMTP y RabbitMQ; en las pruebas se pasan dobles."""

    @asynccontextmanager
    async def ciclo_de_vida(app: FastAPI):
        if servicio is not None:
            app.state.servicio = servicio
            app.state.en_vivo = en_vivo or AvisosEnVivoMemoria()
            yield
            return

        from psycopg_pool import AsyncConnectionPool

        from .config import Config
        from .infraestructura import CorreoSmtp, DirectorioGabinete, Mensajeria, RepositorioPostgres

        cfg = Config.desde_entorno()
        pool = AsyncConnectionPool(cfg.db_url, min_size=1, max_size=5, open=False)
        await pool.open()
        vivo = AvisosEnVivoMemoria()
        mensajeria = Mensajeria(cfg)
        app.state.en_vivo = vivo
        app.state.servicio = ServicioNotificaciones(
            RepositorioPostgres(pool), CorreoSmtp(cfg), DirectorioGabinete(cfg.gabinete_url), vivo, mensajeria,
            url_app=cfg.url_app)
        consumidor = asyncio.create_task(mensajeria.iniciar(app.state.servicio.procesar))
        try:
            yield
        finally:
            consumidor.cancel()
            await mensajeria.cerrar()
            await pool.close()

    app = FastAPI(title="Servicio notificaciones", version="0.1.0", lifespan=ciclo_de_vida)

    def srv(request: Request) -> ServicioNotificaciones:
        return request.app.state.servicio

    @app.get("/salud")
    async def salud():
        return {"estado": "ok"}

    @app.get("/notificaciones")
    async def listar(request: Request, idPersona: int = Query(..., gt=0), soloNoLeidas: bool = False,
                     limite: int = Query(50, ge=1, le=200)):
        return await srv(request).listar(idPersona, soloNoLeidas, limite)

    @app.get("/notificaciones/resumen")
    async def resumen(request: Request, idPersona: int = Query(..., gt=0)):
        return {"idPersona": idPersona, "noLeidas": await srv(request).no_leidas(idPersona)}

    @app.post("/notificaciones/{id_notificacion}/leida")
    async def marcar_leida(request: Request, id_notificacion: int, idPersona: int = Query(..., gt=0)):
        if not await srv(request).marcar_leida(id_notificacion, idPersona):
            raise HTTPException(404, detail=f"No existe el aviso {id_notificacion} de la persona {idPersona}")
        return {"idNotificacion": id_notificacion, "leida": True}

    @app.post("/notificaciones/leidas")
    async def marcar_todas(request: Request, idPersona: int = Query(..., gt=0)):
        return {"marcadas": await srv(request).marcar_todas_leidas(idPersona)}

    @app.get("/notificaciones/stream")
    async def stream(request: Request, idPersona: int = Query(..., gt=0)):
        """Avisos nuevos en vivo (Server-Sent Events). Un latido cada 25 s mantiene viva la conexión."""
        vivo: AvisosEnVivoMemoria = request.app.state.en_vivo

        async def eventos():
            async with vivo.suscribir(idPersona) as cola:
                yield "retry: 5000\n\n"
                while True:
                    try:
                        dato = await asyncio.wait_for(cola.get(), timeout=LATIDO_SEGUNDOS)
                        yield f"event: aviso\ndata: {json.dumps(dato, ensure_ascii=False)}\n\n"
                    except asyncio.TimeoutError:
                        if await request.is_disconnected():
                            break
                        yield ": latido\n\n"

        return StreamingResponse(eventos(), media_type="text/event-stream",
                                 headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

    # Mismo formato de error que los servicios Java: { "error": ..., "campos": {...} }
    @app.exception_handler(HTTPException)
    async def error_http(request: Request, exc: HTTPException):
        return JSONResponse({"error": exc.detail}, status_code=exc.status_code)

    @app.exception_handler(RequestValidationError)
    async def error_validacion(request: Request, exc: RequestValidationError):
        campos = {str(e["loc"][-1]): e["msg"] for e in exc.errors()}
        return JSONResponse({"error": "Hay datos inválidos en la petición", "campos": campos}, status_code=400)

    return app


app = crear_app()
