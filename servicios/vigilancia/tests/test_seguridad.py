"""F9: el servicio no confía en las cabeceras; la identidad sale del token firmado por identidad."""
import time
import uuid
from types import SimpleNamespace

import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi import FastAPI, Request
from fastapi.testclient import TestClient

from app.seguridad import MiddlewareToken, VerificadorTokens

LLAVE = rsa.generate_private_key(public_exponent=65537, key_size=2048)
OTRA = rsa.generate_private_key(public_exponent=65537, key_size=2048)


class LlavesFijas:
    """Doble de PyJWKClient: siempre la llave pública de LLAVE."""

    def get_signing_key_from_jwt(self, token):
        return SimpleNamespace(key=LLAVE.public_key())


def token(llave=LLAVE, sujeto="1019087654", roles=("ABOGADO",), cliente=None, temporal=False, vence=600):
    claims = {"iss": "gestion-juridica", "sub": sujeto, "roles": list(roles), "iat": int(time.time()),
              "exp": int(time.time()) + vence, "jti": str(uuid.uuid4())}
    if cliente is not None:
        claims["idCliente"] = cliente
    if temporal:
        claims["clave_temporal"] = True
    return jwt.encode(claims, llave, algorithm="RS256")


def cliente_http() -> TestClient:
    app = FastAPI()

    @app.get("/salud")
    async def salud():
        return {"estado": "ok"}

    @app.api_route("/eco", methods=["GET", "POST"])
    async def eco(request: Request):
        h = request.headers
        return {"id": h.get("x-usuario-id"), "roles": h.get("x-usuario-roles"), "cliente": h.get("x-cliente-id")}

    app.add_middleware(MiddlewareToken, verificador=VerificadorTokens("http://no-se-usa", llaves=LlavesFijas()))
    return TestClient(app)


def test_sin_token_con_firma_ajena_o_vencido_es_401():
    http = cliente_http()
    falsas = {"x-usuario-id": "79123456", "x-usuario-roles": "ADMIN"}
    assert http.get("/eco", headers=falsas).status_code == 401
    for t in ("basura", token(llave=OTRA), token(vence=-60)):
        r = http.get("/eco", headers={**falsas, "Authorization": f"Bearer {t}"})
        assert r.status_code == 401
        assert r.json() == {"error": "Inicie sesión para continuar"}


def test_la_identidad_sale_del_token():
    r = cliente_http().get("/eco", headers={"Authorization": f"Bearer {token()}", "x-usuario-id": "79123456",
                                            "x-usuario-roles": "ADMIN", "x-cliente-id": "1"})
    assert r.json() == {"id": "1019087654", "roles": "ABOGADO", "cliente": None}


def test_cliente_y_servicio():
    http = cliente_http()
    r = http.get("/eco", headers={"Authorization": f"Bearer {token(sujeto='1032456781', roles=('CLIENTE',), cliente=6)}"})
    assert r.json() == {"id": "1032456781", "roles": "CLIENTE", "cliente": "6"}

    servicio = token(sujeto="servicio:procesos", roles=("SERVICIO",))
    assert http.get("/eco", headers={"Authorization": f"Bearer {servicio}"}).json()["id"] is None
    assert http.post("/eco", headers={"Authorization": f"Bearer {servicio}"}).status_code == 403


def test_clave_temporal_y_salud():
    http = cliente_http()
    assert http.get("/eco", headers={"Authorization": f"Bearer {token(temporal=True)}"}).status_code == 403
    assert http.get("/salud").status_code == 200
