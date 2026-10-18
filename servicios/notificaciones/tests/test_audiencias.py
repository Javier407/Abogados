"""F7: aviso de audiencias el día hábil anterior (evento audiencia.proxima)."""
from datetime import date

import pytest

from app.dominio import AvisoAudiencia, EventoInvalido, NotificacionNueva, Canal, contenido_audiencia, enlace_de
from tests.test_actuaciones_y_telegram import TelegramFalso, servicio
from tests.test_aplicacion import ABOGADO, RepoMemoria

AUDIENCIA = {"id_actuacion": 81, "id_proceso": 9, "radicado": "05001311000520250061500",
             "descripcion": "Audiencia de pruebas: valoración psicológica", "fecha": "2026-10-16", "hora": "09:30:00",
             "lugar": "Sala 4, Palacio de Justicia", "id_abogado": ABOGADO, "ocurrido": "2026-10-15T12:00:00Z"}


def test_redacta_la_audiencia_de_manana_con_hora_y_lugar():
    c = contenido_audiencia(AvisoAudiencia.desde_mensaje(AUDIENCIA), date(2026, 10, 15))
    assert c.titulo == "Audiencia mañana"
    assert c.mensaje == ("Audiencia de pruebas: valoración psicológica — proceso 05001-31-10-005-2025-00615-00 — "
                         "16/10/2026 a las 09:30 — Sala 4, Palacio de Justicia")
    assert c.id_persona == ABOGADO


def test_un_viernes_dice_el_dia_de_la_audiencia_del_lunes():
    lunes = AvisoAudiencia.desde_mensaje({**AUDIENCIA, "fecha": "2026-10-19", "hora": None, "lugar": None})
    c = contenido_audiencia(lunes, date(2026, 10, 16))
    assert c.titulo == "Audiencia el lunes 19/10/2026"
    assert c.mensaje.endswith("— 19/10/2026")


def test_la_clave_lleva_la_fecha_para_avisar_de_nuevo_si_la_reprograman():
    hoy = date(2026, 10, 15)
    a = contenido_audiencia(AvisoAudiencia.desde_mensaje(AUDIENCIA), hoy).plantilla_clave
    b = contenido_audiencia(AvisoAudiencia.desde_mensaje({**AUDIENCIA, "fecha": "2026-10-20"}), hoy).plantilla_clave
    assert a != b and "2026-10-16" in a


def test_audiencia_mal_formada():
    with pytest.raises(EventoInvalido):
        AvisoAudiencia.desde_mensaje({"id_actuacion": 1})


def test_el_aviso_lleva_al_expediente():
    n = NotificacionNueva(ABOGADO, Canal.EMAIL, "audiencia.proxima", "Audiencia mañana", "m", "k",
                          referencia={"id_proceso": 9})
    assert enlace_de(n, "http://app/") == ("http://app/procesos/9", "Ver el expediente")


async def test_se_avisa_por_la_app_y_no_se_repite():
    repo = RepoMemoria()
    s = servicio(repo, telegram=TelegramFalso())
    assert await s.procesar("audiencia.proxima", AUDIENCIA) >= 1
    assert await s.procesar("audiencia.proxima", AUDIENCIA) == 0
    assert any(f["canal"] == Canal.APP and f["titulo"] == "Audiencia mañana" for f in repo.filas.values())
