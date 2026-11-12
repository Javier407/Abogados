-- Consultas al bot de Telegram (servicio notificaciones). Idempotente.
-- Base existente (desde infra/):
--   docker compose exec -T postgres psql -U gabinete_admin -d gabinete -v ON_ERROR_STOP=1 < ../db/migraciones/020-bot-consultas.sql
--
-- 1) Registro de cada consulta: quién, desde qué chat, qué comando y cómo terminó. NO guarda el texto que escribió
--    la persona ni la respuesta (pueden llevar datos del proceso). Estas consultas no pasan por el gateway, así que
--    no están en la bitácora encadenada (docs/14-auditoria.md): este es su registro.
-- 2) Un chat de Telegram pertenece a una sola persona. Si ya hay chats repetidos, no se crea el índice y se avisa:
--    el bot no responde a un chat que esté a nombre de dos personas.
SET client_encoding = 'UTF8';

BEGIN;

CREATE TABLE IF NOT EXISTS notificaciones.consultas_bot (
    id_consulta  bigint       PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    momento      timestamptz  NOT NULL DEFAULT now(),
    id_persona   bigint,                                -- NULL si el chat no está vinculado
    chat_id      varchar(40)  NOT NULL,
    comando      varchar(30)  NOT NULL,                 -- mis_procesos, terminos, proceso, audiencias, ayuda…
    resultado    varchar(20)  NOT NULL CHECK (resultado IN
                 ('RESPONDIDA', 'SIN_VINCULO', 'NO_AUTORIZADO', 'LIMITE', 'NO_ENCONTRADO', 'ERROR'))
);
CREATE INDEX IF NOT EXISTS consultas_bot_persona ON notificaciones.consultas_bot (id_persona, momento DESC);

-- El servicio agrega y lee; no cambia ni borra lo registrado.
GRANT SELECT, INSERT ON notificaciones.consultas_bot TO svc_notificaciones;

DO $$
BEGIN
    IF EXISTS (SELECT destino FROM notificaciones.preferencias
               WHERE canal = 'TELEGRAM' AND destino IS NOT NULL GROUP BY destino HAVING count(*) > 1) THEN
        RAISE NOTICE 'Hay chats de Telegram vinculados a más de una persona: no se crea el índice único. '
                     'Revise notificaciones.preferencias (canal TELEGRAM) y vuelva a correr esta migración.';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS preferencias_un_chat_por_persona
            ON notificaciones.preferencias (destino) WHERE canal = 'TELEGRAM' AND destino IS NOT NULL;
    END IF;
END $$;

COMMIT;
