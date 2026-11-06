"""F15: avisos de seguridad de la cuenta (evento cuenta.seguridad que publica identidad)."""
import pytest

from app.aplicacion import ServicioNotificaciones
from app.dominio import (EVENTO_SEGURIDAD, TEXTOS_SEGURIDAD, AvisoSeguridad, Canal, EventoInvalido, NotificacionNueva,
                         contenido_seguridad, enlace_de)
from app.infraestructura import AvisosEnVivoMemoria, Mensajeria
from tests.test_aplicacion import CorreoFalso, DirectorioFalso, Registro, RepoMemoria

EVENTO = {"tipo": "MFA_ACTIVADA", "idUsuario": 1019087654, "usuario": "crojas", "nombre": "Carolina Rojas",
          "email": "c@firma.co", "momento": "2026-10-22T20:05:00Z"}


def test_el_aviso_dice_que_paso_cuando_en_hora_de_colombia_y_que_hacer():
    c = contenido_seguridad(AvisoSeguridad.desde_mensaje(EVENTO))
    assert c.evento == EVENTO_SEGURIDAD and c.id_persona == 1019087654
    assert c.titulo == "Se activó la verificación en dos pasos"
    assert "22/10/2026 a las 15:05" in c.mensaje and "crojas" in c.mensaje
    assert c.mensaje.endswith("avise de inmediato al administrador de la firma.")
    n = NotificacionNueva(c.id_persona, Canal.EMAIL, c.evento, c.titulo, c.mensaje, "k", referencia=c.referencia)
    assert enlace_de(n, "https://app") == ("https://app/cuenta", "Revisar mi cuenta")


def test_todos_los_tipos_tienen_texto():
    for tipo in TEXTOS_SEGURIDAD:
        c = contenido_seguridad(AvisoSeguridad.desde_mensaje({**EVENTO, "tipo": tipo}))
        assert c.titulo and "crojas" in c.mensaje and "{" not in c.mensaje


def test_un_evento_mal_formado_se_descarta():
    with pytest.raises(EventoInvalido):
        AvisoSeguridad.desde_mensaje({**EVENTO, "tipo": "BORRADO_TOTAL"})
    with pytest.raises(EventoInvalido):
        AvisoSeguridad.desde_mensaje({"tipo": "MFA_ACTIVADA"})


async def test_llega_por_la_app_y_al_correo_de_la_cuenta_una_sola_vez():
    repo, correo = RepoMemoria(), CorreoFalso()
    s = ServicioNotificaciones(repo, correo, DirectorioFalso(), AvisosEnVivoMemoria(), Registro())

    assert await s.procesar(EVENTO_SEGURIDAD, EVENTO) == 2
    assert await s.procesar(EVENTO_SEGURIDAD, EVENTO) == 0           # el mismo evento repetido no se avisa dos veces
    assert {f["canal"] for f in repo.filas.values()} == {Canal.APP, Canal.EMAIL}
    assert correo.enviados[0][0] == "c@firma.co"
    otro = {**EVENTO, "tipo": "CLAVE_CAMBIADA", "momento": "2026-10-22T20:06:00Z"}
    assert await s.procesar(EVENTO_SEGURIDAD, otro) == 2


def test_notificaciones_escucha_el_evento():
    assert "cuenta.seguridad" in Mensajeria.CLAVES
