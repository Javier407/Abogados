-- F6: expediente (partes, actuaciones, recursos y providencias manuales). Idempotente.
-- Base existente: docker compose exec -T postgres psql -U gabinete_admin -d gabinete < ../db/migraciones/004-f6-expediente.sql
SET client_encoding = 'UTF8';

-- Un recurso (apelación, reposición, impugnación) se interpone contra una providencia concreta.
ALTER TABLE procesos.actuaciones ADD COLUMN IF NOT EXISTS id_providencia integer
    REFERENCES procesos.providencias ON DELETE SET NULL;
-- Quién la registró en el sistema (documento del usuario del token) y cuándo; id_persona sigue siendo
-- quién la hizo o la tiene a cargo.
ALTER TABLE procesos.actuaciones ADD COLUMN IF NOT EXISTS registrado_por bigint;
ALTER TABLE procesos.actuaciones ADD COLUMN IF NOT EXISTS registrado     timestamptz NOT NULL DEFAULT now();

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_actuaciones_recurso') THEN
        ALTER TABLE procesos.actuaciones ADD CONSTRAINT ck_actuaciones_recurso
            CHECK (tipo <> 'RECURSO' OR id_providencia IS NOT NULL) NOT VALID;
    END IF;
END $$;

-- Las providencias manuales también guardan quién las registró.
ALTER TABLE procesos.providencias ADD COLUMN IF NOT EXISTS registrado_por bigint;

-- Búsqueda de conflictos de intereses por documento de la contraparte.
CREATE INDEX IF NOT EXISTS ix_partes_documento ON procesos.partes (numero_documento) WHERE numero_documento IS NOT NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA procesos TO svc_procesos;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA procesos TO svc_procesos;
