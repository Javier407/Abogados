"""Configuración por variables de entorno (las claves vienen de infra/.env)."""
from __future__ import annotations

import os
from dataclasses import dataclass, field


def _bool(valor: str) -> bool:
    return valor.strip().lower() in ("1", "true", "si", "sí", "yes")


@dataclass(frozen=True)
class Config:
    db_url: str = field(repr=False)            # lleva la contraseña
    rabbitmq_url: str = field(repr=False)
    exchange: str
    gabinete_url: str
    url_app: str
    smtp_host: str
    smtp_puerto: int
    smtp_usuario: str
    smtp_clave: str = field(repr=False)
    smtp_starttls: bool
    correo_remitente: str
    telegram_token: str = field(repr=False)   # secretos: nunca en logs ni repr
    procesos_url: str

    @staticmethod
    def desde_entorno() -> "Config":
        e = os.environ.get
        return Config(
            db_url=e("DB_URL", "postgresql://svc_notificaciones@localhost:5432/gabinete"),
            rabbitmq_url=e("RABBITMQ_URL", "amqp://guest:guest@localhost/"),
            exchange=e("EVENTOS_EXCHANGE", "gabinete.eventos"),
            gabinete_url=e("GABINETE_URL", "http://localhost:8081"),
            url_app=e("URL_APP", ""),
            smtp_host=e("SMTP_HOST", "localhost"),
            smtp_puerto=int(e("SMTP_PUERTO", "1025")),
            smtp_usuario=e("SMTP_USUARIO", ""),
            smtp_clave=e("SMTP_CLAVE", ""),
            smtp_starttls=_bool(e("SMTP_STARTTLS", "false")),
            correo_remitente=e("CORREO_REMITENTE", "Gestion Juridica <avisos@gestion-juridica.local>"),
            telegram_token=e("TELEGRAM_TOKEN", "").strip(),
            procesos_url=e("PROCESOS_URL", "http://localhost:8082"),
        )
