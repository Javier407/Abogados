"""API REST y SSE del servicio notificaciones. Contrato: docs/contratos/notificaciones.openapi.yaml."""
from __future__ import annotations

import asyncio
import json
import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, StreamingResponse

from .aplicacion import ConsultaServidor, ServicioNotificaciones
from .seguridad import MiddlewareToken, TokenServicio, VerificadorTokens
from .infraestructura import AvisosEnVivoMemoria

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
# httpx registra cada URL llamada; en la API de Telegram la URL lleva el token: nunca debe quedar en los logs.
logging.getLogger("httpx").setLevel(logging.WARNING)
log = logging.getLogger("notificaciones")

LATIDO_SEGUNDOS = 25


def persona_autorizada(request: Request, idPersona: int = Query(..., gt=0)) -> int:
    """
    Cada quien solo ve y marca sus propios avisos. El gateway pone X-Usuario-Id y X-Usuario-Roles tomados del
    token (y borra los que traiga la petición); solo un ADMIN puede consultar los de otra persona.
    """
    usuario = (request.headers.get("x-usuario-id") or "").strip()
    if not usuario:
        raise HTTPException(401, detail="Falta la identidad del usuario")
    roles = {r for r in (request.headers.get("x-usuario-roles") or "").split(",") if r}
    if usuario != str(idPersona) and "ADMIN" not in roles:
        raise HTTPException(403, detail="Solo puede consultar sus propios avisos")
    return idPersona


def solo_admin(request: Request) -> None:
    """El estado del servidor (F12) es solo para el administrador; el gateway también lo exige."""
    if not (request.headers.get("x-usuario-id") or "").strip():
        raise HTTPException(401, detail="Falta la identidad del usuario")
    if "ADMIN" not in (request.headers.get("x-usuario-roles") or "").split(","):
        raise HTTPException(403, detail="Solo el administrador ve el estado del servidor")


def crear_app(servicio: ServicioNotificaciones | None = None,
              en_vivo: AvisosEnVivoMemoria | None = None, verificador: VerificadorTokens | None = None,
              servidor: ConsultaServidor | None = None) -> FastAPI:
    """Sin argumentos conecta PostgreSQL, SMTP y RabbitMQ; en las pruebas se pasan dobles."""

    @asynccontextmanager
    async def ciclo_de_vida(app: FastAPI):
        if servicio is not None:
            app.state.servicio = servicio
            app.state.en_vivo = en_vivo or AvisosEnVivoMemoria()
            app.state.servidor = servidor
            yield
            return

        from psycopg_pool import AsyncConnectionPool

        from .config import Config
        from .infraestructura import (ClienteIdentidad, ClienteProcesos, CorreoSmtp, DirectorioGabinete, Mensajeria,
                                      RepositorioPostgres, RepositorioServidorPostgres, TelegramBot)

        cfg = Config.desde_entorno()
        # check: se comprueba cada conexión antes de usarla; si PostgreSQL se reinició, se abre otra.
        pool = AsyncConnectionPool(cfg.db_url, min_size=1, max_size=5, open=False,
                                   check=AsyncConnectionPool.check_connection)
        await pool.open()
        vivo = AvisosEnVivoMemoria()
        mensajeria = Mensajeria(cfg)
        app.state.en_vivo = vivo
        telegram = None
        if cfg.telegram_token:
            telegram = TelegramBot(cfg.telegram_token)
            await telegram.iniciar()
        else:
            log.info("Sin TELEGRAM_TOKEN: el canal de Telegram queda apagado")
        token = TokenServicio.desde_entorno("notificaciones")
        app.state.servicio = ServicioNotificaciones(
            RepositorioPostgres(pool), CorreoSmtp(cfg), DirectorioGabinete(cfg.gabinete_url, token), vivo, mensajeria,
            url_app=cfg.url_app, telegram=telegram, procesos=ClienteProcesos(cfg.procesos_url, token),
            cuentas=ClienteIdentidad(cfg.identidad_url, token))
        app.state.servidor = ConsultaServidor(RepositorioServidorPostgres(pool))
        tareas = [asyncio.create_task(mensajeria.iniciar(app.state.servicio.procesar))]
        if telegram is not None:
            tareas.append(asyncio.create_task(telegram.escuchar(app.state.servicio.vincular_telegram)))
        try:
            yield
        finally:
            for t in tareas:
                t.cancel()
            await mensajeria.cerrar()
            await pool.close()

    app = FastAPI(title="Servicio notificaciones", version="0.1.0", lifespan=ciclo_de_vida)
    # F9: cada petición trae un token válido (también verificado aquí, no solo en el gateway). En las pruebas
    # con dobles no se verifica, salvo que se pase un verificador.
    if servicio is None or verificador is not None:
        app.add_middleware(MiddlewareToken, verificador=verificador or VerificadorTokens.desde_entorno())

    def srv(request: Request) -> ServicioNotificaciones:
        return request.app.state.servicio

    @app.get("/salud")
    async def salud():
        return {"estado": "ok"}

    @app.get("/notificaciones")
    async def listar(request: Request, idPersona: int = Depends(persona_autorizada), soloNoLeidas: bool = False,
                     limite: int = Query(50, ge=1, le=200)):
        return await srv(request).listar(idPersona, soloNoLeidas, limite)

    @app.get("/notificaciones/resumen")
    async def resumen(request: Request, idPersona: int = Depends(persona_autorizada)):
        return {"idPersona": idPersona, "noLeidas": await srv(request).no_leidas(idPersona)}

    @app.post("/notificaciones/{id_notificacion}/leida")
    async def marcar_leida(request: Request, id_notificacion: int, idPersona: int = Depends(persona_autorizada)):
        if not await srv(request).marcar_leida(id_notificacion, idPersona):
            raise HTTPException(404, detail=f"No existe el aviso {id_notificacion} de la persona {idPersona}")
        return {"idNotificacion": id_notificacion, "leida": True}

    @app.post("/notificaciones/leidas")
    async def marcar_todas(request: Request, idPersona: int = Depends(persona_autorizada)):
        return {"marcadas": await srv(request).marcar_todas_leidas(idPersona)}

    @app.get("/notificaciones/telegram")
    async def estado_telegram(request: Request, idPersona: int = Depends(persona_autorizada)):
        return await srv(request).estado_telegram(idPersona)

    @app.post("/notificaciones/telegram/vinculo")
    async def vinculo_telegram(request: Request, idPersona: int = Depends(persona_autorizada)):
        try:
            return await srv(request).generar_vinculo(idPersona)
        except ValueError as e:
            raise HTTPException(422, detail=str(e)) from e

    @app.delete("/notificaciones/telegram")
    async def desvincular_telegram(request: Request, idPersona: int = Depends(persona_autorizada)):
        return {"desvinculado": await srv(request).desvincular_telegram(idPersona)}

    @app.get("/notificaciones/servidor")
    async def estado_servidor(request: Request, dias: int = Query(7, ge=1, le=90)):
        solo_admin(request)
        consulta = getattr(request.app.state, "servidor", None)
        if consulta is None:
            raise HTTPException(503, detail="El estado del servidor no está disponible")
        return await consulta.estado(dias)

    @app.get("/notificaciones/stream")
    async def stream(request: Request, idPersona: int = Depends(persona_autorizada)):
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
