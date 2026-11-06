from datetime import date

import pytest

from app.dominio import (AvisoTermino, Canal, EventoInvalido, Preferencia, avisos_para, correo_de,
                         radicado_legible, redactar)

HOY = date(2026, 10, 15)


def mensaje(**cambios) -> dict:
    base = {"id_termino": 7, "id_proceso": 2, "radicado": "11001310303220240045800",
            "descripcion": "Presentar alegatos de conclusión", "vence": "2026-10-20",
            "dias_habiles_restantes": 3, "id_abogado": 79123456, "ocurrido": "2026-10-15T12:00:00Z"}
    base.update(cambios)
    return base


def aviso(evento="termino.por_vencer", **cambios) -> AvisoTermino:
    return AvisoTermino.desde_mensaje(evento, mensaje(**cambios))


def test_lee_el_evento_segun_el_contrato():
    a = aviso()
    assert a.vence == date(2026, 10, 20)
    assert a.id_abogado == 79123456
    assert not a.vencido


@pytest.mark.parametrize("evento,cuerpo", [
    ("proceso.creado", mensaje()),
    ("termino.por_vencer", {k: v for k, v in mensaje().items() if k != "vence"}),
    ("termino.por_vencer", mensaje(vence="20/10/2026")),
])
def test_rechaza_eventos_que_no_cumplen_el_contrato(evento, cuerpo):
    with pytest.raises(EventoInvalido):
        AvisoTermino.desde_mensaje(evento, cuerpo)


def test_radicado_legible():
    assert radicado_legible("11001310303220240045800") == "11001-31-03-032-2024-00458-00"
    assert radicado_legible("123") == "123"


def test_redacta_segun_lo_que_falta():
    titulo, texto = redactar(aviso())
    assert titulo == "Término por vencer"
    assert "11001-31-03-032-2024-00458-00" in texto
    assert "20/10/2026" in texto and "faltan 3 días hábiles" in texto

    assert redactar(aviso(dias_habiles_restantes=1))[1].endswith("(falta 1 día hábil)")
    assert redactar(aviso(dias_habiles_restantes=0))[0] == "El término vence hoy"
    assert redactar(aviso("termino.vencido", dias_habiles_restantes=-2, vence="2026-10-13"))[0] == "Término vencido"


def test_siempre_avisa_en_la_app_y_por_correo_si_lo_tiene_configurado():
    prefs = [Preferencia(Canal.APP, None), Preferencia(Canal.EMAIL, "agomez@firma.co")]
    avisos = avisos_para(aviso(), prefs, None, HOY)
    assert [(n.canal, n.destino) for n in avisos] == [(Canal.APP, None), (Canal.EMAIL, "agomez@firma.co")]
    assert avisos[0].referencia == {"id_proceso": 2, "id_termino": 7}


def test_sin_preferencia_de_correo_usa_el_de_la_ficha():
    avisos = avisos_para(aviso(), [], "nuevo@firma.co", HOY)
    assert [(n.canal, n.destino) for n in avisos] == [(Canal.APP, None), (Canal.EMAIL, "nuevo@firma.co")]


def test_respeta_al_que_desactivo_el_correo():
    prefs = [Preferencia(Canal.EMAIL, "agomez@firma.co", activo=False)]
    avisos = avisos_para(aviso(), prefs, "otro@firma.co", HOY)
    assert [n.canal for n in avisos] == [Canal.APP]


def test_la_clave_cambia_por_dia_y_canal_para_no_repetir_avisos():
    prefs = [Preferencia(Canal.EMAIL, "a@firma.co")]
    hoy = avisos_para(aviso(), prefs, None, HOY)
    otra_vez = avisos_para(aviso(), prefs, None, HOY)
    manana = avisos_para(aviso(), prefs, None, date(2026, 10, 16))
    assert [n.clave for n in hoy] == [n.clave for n in otra_vez]
    assert hoy[0].clave != hoy[1].clave
    assert hoy[0].clave != manana[0].clave
    assert hoy[0].clave == "termino.por_vencer:7:79123456:APP:2026-10-15"


def test_el_correo_escapa_html_y_trae_el_enlace():
    n = avisos_para(aviso(descripcion="Recurso <urgente>"), [Preferencia(Canal.EMAIL, "a@firma.co")], None, HOY)[1]
    asunto, texto, html = correo_de(n, "http://100.65.152.112:8090/")
    assert asunto == "[Gestión Jurídica] Término por vencer"
    assert "&lt;urgente&gt;" in html and "<urgente>" not in html
    assert "http://100.65.152.112:8090/terminos" in texto
