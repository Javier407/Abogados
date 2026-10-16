"""Procesar eventos con dobles en memoria: sin base de datos, SMTP ni RabbitMQ."""
from datetime import date

import pytest

from app.aplicacion import ServicioNotificaciones
from app.dominio import Canal, EventoInvalido, NotificacionNueva, Preferencia

ABOGADO = 79123456


class RepoMemoria:
    def __init__(self, preferencias=None):
        self.prefs = preferencias or {}
        self.filas: dict[int, dict] = {}

    async def preferencias(self, id_persona):
        return self.prefs.get(id_persona, [])

    async def guardar(self, n: NotificacionNueva):
        if any(f["clave"] == n.clave for f in self.filas.values()):
            return None
        i = len(self.filas) + 1
        self.filas[i] = {"id": i, "clave": n.clave, "canal": n.canal, "persona": n.id_persona,
                         "estado": "PENDIENTE", "titulo": n.titulo}
        return i

    async def marcar(self, i, estado):
        self.filas[i]["estado"] = estado

    async def listar(self, id_persona, solo_no_leidas, limite):
        return [f for f in self.filas.values() if f["persona"] == id_persona and f["canal"] == Canal.APP][:limite]

    async def contar_no_leidas(self, id_persona):
        return sum(1 for f in self.filas.values()
                   if f["persona"] == id_persona and f["canal"] == Canal.APP and f["estado"] == "PENDIENTE")

    async def marcar_leida(self, i, id_persona):
        f = self.filas.get(i)
        if not f or f["persona"] != id_persona:
            return False
        f["estado"] = "LEIDA"
        return True

    async def marcar_todas_leidas(self, id_persona):
        n = 0
        for f in self.filas.values():
            if f["persona"] == id_persona and f["estado"] == "PENDIENTE" and f["canal"] == Canal.APP:
                f["estado"] = "LEIDA"
                n += 1
        return n

    def por_canal(self, canal):
        return [f for f in self.filas.values() if f["canal"] == canal]


class CorreoFalso:
    def __init__(self, falla=False):
        self.falla = falla
        self.enviados = []

    async def enviar(self, destino, asunto, texto, html):
        if self.falla:
            raise ConnectionError("SMTP caído")
        self.enviados.append((destino, asunto))


class DirectorioFalso:
    def __init__(self, correo=None, caido=False):
        self.correo, self.caido = correo, caido

    async def correo_abogado(self, id_persona):
        if self.caido:
            raise ConnectionError("gabinete caído")
        return self.correo


class Registro:
    def __init__(self):
        self.datos = []

    async def publicar(self, id_persona, dato):
        self.datos.append((id_persona, dato))

    async def notificacion_enviada(self, id_notificacion, n):
        self.datos.append((id_notificacion, n.canal))


EVENTO = {"id_termino": 7, "id_proceso": 2, "radicado": "11001310303220240045800",
          "descripcion": "Presentar alegatos", "vence": "2026-10-20", "dias_habiles_restantes": 3,
          "id_abogado": ABOGADO, "ocurrido": "2026-10-15T12:00:00Z"}


def servicio(repo, correo=None, directorio=None):
    en_vivo, publicador = Registro(), Registro()
    s = ServicioNotificaciones(repo, correo or CorreoFalso(), directorio or DirectorioFalso(), en_vivo, publicador,
                               url_app="http://app", hoy=lambda: date(2026, 10, 15))
    return s, en_vivo, publicador


async def test_un_termino_por_vencer_genera_aviso_en_la_app_y_correo():
    repo = RepoMemoria({ABOGADO: [Preferencia(Canal.APP, None), Preferencia(Canal.EMAIL, "arodriguez@firma.co")]})
    correo = CorreoFalso()
    s, en_vivo, publicador = servicio(repo, correo)

    assert await s.procesar("termino.por_vencer", EVENTO) == 2

    assert correo.enviados == [("arodriguez@firma.co", "[Gestión Jurídica] Término por vencer")]
    assert repo.por_canal(Canal.EMAIL)[0]["estado"] == "ENVIADA"
    assert repo.por_canal(Canal.APP)[0]["estado"] == "PENDIENTE"          # no leída
    assert en_vivo.datos[0][0] == ABOGADO                                  # llegó a la pestaña abierta
    assert len(publicador.datos) == 2                                      # notificacion.enviada x2


async def test_el_mismo_evento_dos_veces_el_mismo_dia_no_repite_avisos():
    repo = RepoMemoria({ABOGADO: [Preferencia(Canal.EMAIL, "a@firma.co")]})
    correo = CorreoFalso()
    s, _, _ = servicio(repo, correo)

    await s.procesar("termino.por_vencer", EVENTO)
    assert await s.procesar("termino.por_vencer", EVENTO) == 0
    assert len(correo.enviados) == 1
    assert len(repo.filas) == 2


async def test_si_el_correo_falla_queda_fallida_pero_el_aviso_de_la_app_se_guarda():
    repo = RepoMemoria({ABOGADO: [Preferencia(Canal.EMAIL, "a@firma.co")]})
    s, _, publicador = servicio(repo, CorreoFalso(falla=True))

    assert await s.procesar("termino.vencido", {**EVENTO, "dias_habiles_restantes": -1}) == 2
    assert repo.por_canal(Canal.EMAIL)[0]["estado"] == "FALLIDA"
    assert repo.por_canal(Canal.APP)[0]["titulo"] == "Término vencido"
    assert len(publicador.datos) == 1                                      # solo el de la app


async def test_sin_preferencias_busca_el_correo_en_gabinete():
    repo = RepoMemoria()
    correo = CorreoFalso()
    s, _, _ = servicio(repo, correo, DirectorioFalso("nuevo@firma.co"))

    await s.procesar("termino.por_vencer", EVENTO)
    assert correo.enviados[0][0] == "nuevo@firma.co"


async def test_si_gabinete_no_responde_igual_avisa_en_la_app():
    repo = RepoMemoria()
    s, _, _ = servicio(repo, CorreoFalso(), DirectorioFalso(caido=True))

    assert await s.procesar("termino.por_vencer", EVENTO) == 1
    assert repo.por_canal(Canal.APP)


async def test_un_evento_mal_formado_se_rechaza():
    s, _, _ = servicio(RepoMemoria())
    with pytest.raises(EventoInvalido):
        await s.procesar("termino.por_vencer", {"id_termino": 7})


async def test_marcar_leidas():
    repo = RepoMemoria()
    s, _, _ = servicio(repo)
    await s.procesar("termino.por_vencer", EVENTO)
    await s.procesar("termino.por_vencer", {**EVENTO, "id_termino": 8})

    assert await s.no_leidas(ABOGADO) == 2
    assert await s.marcar_leida(1, ABOGADO)
    assert not await s.marcar_leida(1, 999)                               # el aviso es de otra persona
    assert await s.no_leidas(ABOGADO) == 1
    assert await s.marcar_todas_leidas(ABOGADO) == 1
    assert await s.no_leidas(ABOGADO) == 0
