"""Aviso de relevo del abogado a cargo (evento proceso.reasignado)."""
import pytest

from app.dominio import AvisoRelevo, Canal, EventoInvalido, NotificacionNueva, contenido_relevo, enlace_de
from tests.test_actuaciones_y_telegram import TelegramFalso, servicio
from tests.test_aplicacion import RepoMemoria

CATALINA = 52345678
PEDRO = 79123456
RELEVO = {"id_proceso": 34, "radicado": "11001310303220260050100", "asunto": "Incumplimiento de contrato",
          "id_abogado_anterior": CATALINA, "id_abogado_nuevo": PEDRO, "motivo": "RENUNCIA",
          "terminos_trasladados": 3, "ocurrido": "2026-11-11T15:00:00Z"}


def test_avisa_al_que_recibe_con_lo_que_le_pasaron_y_al_que_entrega():
    recibe, entrega = contenido_relevo(AvisoRelevo.desde_mensaje(RELEVO))

    assert recibe.id_persona == PEDRO
    assert recibe.titulo == "Recibió un proceso"
    assert recibe.mensaje == ("Ahora está a su cargo el proceso 11001-31-03-032-2026-00501-00 (Incumplimiento de "
                              "contrato). Motivo: renuncia al poder. Le pasaron 3 términos pendientes: revíselos.")
    assert entrega.id_persona == CATALINA
    assert entrega.mensaje.startswith("El proceso 11001-31-03-032-2026-00501-00")
    assert recibe.referencia == {"id_proceso": 34}


def test_sin_abogado_anterior_ni_terminos_solo_avisa_al_que_recibe():
    avisos = contenido_relevo(AvisoRelevo.desde_mensaje(
        {**RELEVO, "id_abogado_anterior": None, "terminos_trasladados": 0, "asunto": None, "motivo": "OTRO_MOTIVO"}))

    assert [a.id_persona for a in avisos] == [PEDRO]
    assert avisos[0].mensaje == ("Ahora está a su cargo el proceso 11001-31-03-032-2026-00501-00. "
                                 "Motivo: otro motivo.")


def test_un_termino_en_singular():
    recibe = contenido_relevo(AvisoRelevo.desde_mensaje({**RELEVO, "terminos_trasladados": 1}))[0]
    assert recibe.mensaje.endswith("Le pasaron 1 término pendiente: revíselos.")


def test_relevo_mal_formado():
    with pytest.raises(EventoInvalido):
        AvisoRelevo.desde_mensaje({"id_proceso": 34})


def test_el_aviso_lleva_al_expediente():
    n = NotificacionNueva(PEDRO, Canal.APP, "proceso.reasignado", "Recibió un proceso", "m", "k",
                          referencia={"id_proceso": 34})
    assert enlace_de(n, "http://app") == ("http://app/procesos/34", "Ver el expediente")


async def test_se_avisa_a_los_dos_y_no_se_repite_si_el_evento_llega_dos_veces():
    repo = RepoMemoria()
    s = servicio(repo, telegram=TelegramFalso())

    assert await s.procesar("proceso.reasignado", RELEVO) >= 2
    assert await s.procesar("proceso.reasignado", RELEVO) == 0
    personas = {f["persona"] for f in repo.filas.values() if f["canal"] == Canal.APP}
    assert personas == {PEDRO, CATALINA}

    # si el proceso vuelve a cambiar de manos (otro momento), se avisa de nuevo
    assert await s.procesar("proceso.reasignado", {**RELEVO, "ocurrido": "2026-12-01T15:00:00Z"}) >= 2
