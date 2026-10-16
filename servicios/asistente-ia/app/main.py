"""API del servicio asistente-ia. Contrato: docs/contratos/asistente.openapi.yaml."""
from __future__ import annotations

import json
import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

from .aplicacion import ProcesoNoEncontrado, ServicioAsistente
from .dominio import SolicitudInvalida, TipoBorrador

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)


class PedidoBorrador(BaseModel):
    idProceso: int = Field(..., gt=0)
    tipo: TipoBorrador
    instrucciones: str = Field("", max_length=800)


def crear_app(servicio: ServicioAsistente | None = None) -> FastAPI:

    @asynccontextmanager
    async def ciclo_de_vida(app: FastAPI):
        if servicio is None:
            from .infraestructura import ClienteGabinete, ClienteProcesos, ModeloOllama
            e = os.environ.get
            app.state.servicio = ServicioAsistente(
                ModeloOllama(e("OLLAMA_URL", "http://localhost:11434"), e("MODELO", "qwen2.5:3b"),
                             max_tokens=int(e("MAX_TOKENS", "1500"))),
                ClienteProcesos(e("PROCESOS_URL", "http://localhost:8082")),
                ClienteGabinete(e("GABINETE_URL", "http://localhost:8081")))
        else:
            app.state.servicio = servicio
        yield

    app = FastAPI(title="Servicio asistente-ia", version="0.1.0", lifespan=ciclo_de_vida)

    def srv(request: Request) -> ServicioAsistente:
        return request.app.state.servicio

    @app.get("/salud")
    async def salud():
        return {"estado": "ok"}

    @app.get("/asistente/estado")
    async def estado(request: Request):
        return await srv(request).estado()

    @app.post("/asistente/borradores")
    async def borrador(request: Request, pedido: PedidoBorrador):
        """Redacta por partes (Server-Sent Events). Los errores del pedido llegan antes, como JSON normal."""
        s = srv(request)
        caso = await s.preparar(pedido.tipo, pedido.idProceso)

        async def eventos():
            async for ev in s.redactar(pedido.tipo, caso, pedido.instrucciones):
                if await request.is_disconnected():
                    break
                yield f"event: {ev['evento']}\ndata: {json.dumps(ev, ensure_ascii=False)}\n\n"

        return StreamingResponse(eventos(), media_type="text/event-stream",
                                 headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

    @app.exception_handler(ProcesoNoEncontrado)
    async def no_encontrado(request: Request, exc: ProcesoNoEncontrado):
        return JSONResponse({"error": str(exc)}, status_code=404)

    @app.exception_handler(SolicitudInvalida)
    async def invalida(request: Request, exc: SolicitudInvalida):
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
