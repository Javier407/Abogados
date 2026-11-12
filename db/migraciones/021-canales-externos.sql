-- Toga por WhatsApp e Instagram, a través de Talki (docs/16-integracion-talki.md). Idempotente.
-- Base existente (desde infra/):
--   docker compose exec -T postgres psql -U gabinete_admin -d gabinete -v ON_ERROR_STOP=1 < ../db/migraciones/021-canales-externos.sql
--
-- 1) vinculos_canal: códigos de un solo uso (24 horas) que la persona genera en el portal → Avisos y escribe en el
--    chat («vincular K7P2M9QX»).
-- 2) canales_externos: a quién pertenece cada chat externo. Un chat es de una sola persona y cada persona tiene un
--    solo chat por canal. Va aparte de notificaciones.preferencias: por estos canales no salen avisos, solo
--    responde Toga cuando la persona pregunta.
SET client_encoding = 'UTF8';

BEGIN;

CREATE TABLE IF NOT EXISTS notificaciones.vinculos_canal (
    codigo      varchar(12)  PRIMARY KEY,
    id_persona  bigint       NOT NULL,
    canal       varchar(20)  NOT NULL CHECK (canal IN ('WHATSAPP', 'INSTAGRAM')),
    creado      timestamptz  NOT NULL DEFAULT now(),
    usado       timestamptz
);

CREATE TABLE IF NOT EXISTS notificaciones.canales_externos (
    canal       varchar(20)  NOT NULL CHECK (canal IN ('WHATSAPP', 'INSTAGRAM')),
    id_externo  varchar(64)  NOT NULL,                  -- wa_id en WhatsApp, IGSID en Instagram
    id_persona  bigint       NOT NULL,
    vinculado   timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (canal, id_externo),
    CONSTRAINT canales_externos_uno_por_persona UNIQUE (id_persona, canal)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON notificaciones.vinculos_canal, notificaciones.canales_externos
    TO svc_notificaciones;

COMMIT;
