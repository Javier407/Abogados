"""F9: avisos al cliente (resumen publicado y audiencia), en palabras simples y con enlace al portal."""
from datetime import date

from app.aplicacion import ServicioNotificaciones
from app.dominio import (AvisoAudiencia, AvisoResumen, Canal, CuentaCliente, NotificacionNueva,
                         contenido_audiencia_cliente, contenido_resumen_cliente, correo_de, enlace_de)
from tests.test_actuaciones_y_telegram import CorreoFalso, DirectorioFalso, Registro, TelegramFalso
from tests.test_aplicacion import ABOGADO, RepoMemoria
from tests.test_audiencias import AUDIENCIA

CAROLINA = CuentaCliente(1032456781, "Carolina Méndez Ortiz", "carolina.mendez@correo-ejemplo.co")
RESUMEN = {"id_resumen": 4, "id_proceso": 9, "radicado": "05001311000520250061500", "id_cliente": 6,
           "ocurrido": "2026-10-15T12:00:00Z"}


class ProcesosConCliente:
    async def responsable(self, id_proceso):
        return ABOGADO

    async def cliente(self, id_proceso):
        return 6


class CuentasFalsas:
    def __init__(self, cuentas):
        self.cuentas = cuentas
        self.pedidas = []

    async def de_cliente(self, id_cliente):
        self.pedidas.append(id_cliente)
        return self.cuentas if id_cliente == 6 else []


def servicio(repo, cuentas, correo=None):
    return ServicioNotificaciones(repo, correo or CorreoFalso(), DirectorioFalso(), Registro(), Registro(),
                                  url_app="https://app", hoy=lambda: date(2026, 10, 15), telegram=TelegramFalso(),
                                  procesos=ProcesosConCliente(), cuentas=cuentas)


def test_el_resumen_se_avisa_sin_detalles_y_lleva_al_portal():
    c = contenido_resumen_cliente(AvisoResumen.desde_mensaje(RESUMEN), CAROLINA)
    assert c.id_persona == CAROLINA.id_usuario
    assert c.titulo == "Novedad en su proceso"
    assert "05001-31-10-005-2025-00615-00" in c.mensaje and "portal" in c.mensaje
    n = NotificacionNueva(c.id_persona, Canal.EMAIL, c.evento, c.titulo, c.mensaje, "k", referencia=c.referencia)
    assert enlace_de(n, "https://app/") == ("https://app/portal/procesos/9", "Ver su proceso")
    assert "portal de clientes" in correo_de(n, "https://app")[1]


def test_la_audiencia_se_le_explica_al_cliente():
    c = contenido_audiencia_cliente(AvisoAudiencia.desde_mensaje(AUDIENCIA), CAROLINA, date(2026, 10, 15))
    assert c.titulo == "Audiencia mañana en su proceso"
    assert c.mensaje == ("Su proceso 05001-31-10-005-2025-00615-00 tiene audiencia mañana a las 09:30 en Sala 4, "
                         "Palacio de Justicia. Su abogado le indicará si debe asistir.")
    assert "cliente" in c.plantilla_clave


async def test_publicar_un_resumen_avisa_al_cliente_por_la_app_y_al_correo_de_su_cuenta():
    repo, correo = RepoMemoria(), CorreoFalso()
    s = servicio(repo, CuentasFalsas([CAROLINA]), correo)

    assert await s.procesar("portal.resumen_publicado", RESUMEN) == 2
    assert await s.procesar("portal.resumen_publicado", RESUMEN) == 0          # repetido: no se avisa dos veces
    canales = {f["canal"] for f in repo.filas.values() if f["persona"] == CAROLINA.id_usuario}
    assert canales == {Canal.APP, Canal.EMAIL}
    assert correo.enviados[0][0] == CAROLINA.email


async def test_la_audiencia_se_avisa_al_abogado_y_tambien_al_cliente():
    repo = RepoMemoria()
    s = servicio(repo, CuentasFalsas([CAROLINA]))

    await s.procesar("audiencia.proxima", AUDIENCIA)

    personas = {f["persona"] for f in repo.filas.values()}
    assert {ABOGADO, CAROLINA.id_usuario} <= personas


async def test_un_cliente_sin_cuenta_en_el_portal_no_recibe_avisos():
    repo = RepoMemoria()
    assert await servicio(repo, CuentasFalsas([])).procesar("portal.resumen_publicado", RESUMEN) == 0
