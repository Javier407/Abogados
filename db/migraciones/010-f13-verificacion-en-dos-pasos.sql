-- F13: verificación en dos pasos (código TOTP de una app autenticadora) y códigos de recuperación. Idempotente.
-- Base existente: docker compose exec -T postgres psql -U gabinete_admin -d gabinete < ../db/migraciones/010-f13-verificacion-en-dos-pasos.sql
SET client_encoding = 'UTF8';

-- El secreto se guarda cifrado con AES-GCM (llave MFA_CLAVE_CIFRADO de infra/.env, fuera de la base): quien lea la
-- base o una copia de respaldo no puede generar códigos. activo = false mientras la persona no confirme un código.
-- ultimo_paso: el último intervalo de 30 s ya usado; un código no sirve dos veces.
CREATE TABLE IF NOT EXISTS identidad.mfa (
    id_usuario   bigint       PRIMARY KEY REFERENCES identidad.usuarios ON DELETE CASCADE,
    secreto      text         NOT NULL,
    activo       boolean      NOT NULL DEFAULT false,
    desde        timestamptz,
    ultimo_paso  bigint
);

-- Diez códigos de un solo uso por si se pierde el teléfono. Solo se guarda su SHA-256.
CREATE TABLE IF NOT EXISTS identidad.mfa_codigos (
    id_usuario  bigint       NOT NULL REFERENCES identidad.usuarios ON DELETE CASCADE,
    hash        char(64)     NOT NULL,
    usado_en    timestamptz,
    PRIMARY KEY (id_usuario, hash)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA identidad TO svc_identidad;
