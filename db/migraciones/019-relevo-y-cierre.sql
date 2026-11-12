-- Relevo del abogado responsable (con historial y motivo) y cierre del proceso (con motivo y fecha). Idempotente.
-- Base existente (desde infra/):
--   docker compose exec -T postgres psql -U gabinete_admin -d gabinete -v ON_ERROR_STOP=1 < ../db/migraciones/019-relevo-y-cierre.sql
--
-- 1) Historial del equipo de cada proceso. procesos.asignaciones es una foto de hoy (una fila por persona); el
--    historial guarda cada periodo: quién, con qué papel, desde y hasta cuándo, y por qué salió. Lo llena un
--    disparador, así que queda registrado se cambie por donde se cambie (relevo, editar el proceso, equipo).
--    El motivo, la nota y quién lo hizo llegan por variables de la transacción (set_config(..., true)).
-- 2) Cierre del proceso: fecha, motivo y nota, y la etapa que tenía (para poder reabrirlo).
-- 3) Aviso «proceso.reasignado» para el abogado que recibe el proceso y para el que lo entrega.
SET client_encoding = 'UTF8';

BEGIN;

-- ------------------------------------------------------------------ 1) historial del equipo
CREATE TABLE IF NOT EXISTS procesos.asignaciones_historial (
    id_cambio       bigint       PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    id_proceso      integer      NOT NULL REFERENCES procesos.procesos ON DELETE CASCADE,
    id_persona      bigint       NOT NULL,             -- gabinete.personal.id_persona (sin llave: otro servicio)
    rol             varchar(12)  NOT NULL CHECK (rol IN ('RESPONSABLE', 'APOYO', 'DEPENDIENTE')),
    desde           date         NOT NULL,
    hasta           date,                              -- NULL = sigue en ese papel
    -- Por qué dejó ese papel. NULL si se cambió sin indicarlo (por ejemplo, al editar el proceso).
    motivo          varchar(20)  CHECK (motivo IN ('RENUNCIA', 'REVOCATORIA', 'SUSTITUCION', 'SUCESION',
                                                  'RETIRO_DE_LA_FIRMA', 'REORGANIZACION')),
    nota            varchar(300),
    registrado_por  bigint,                            -- quién lo puso en ese papel
    cerrado_por     bigint,                            -- quién lo sacó de ese papel
    CHECK (hasta IS NULL OR hasta >= desde)
);
CREATE INDEX IF NOT EXISTS asignaciones_historial_proceso ON procesos.asignaciones_historial (id_proceso, desde, id_cambio);
CREATE INDEX IF NOT EXISTS asignaciones_historial_persona ON procesos.asignaciones_historial (id_persona, desde);

CREATE OR REPLACE FUNCTION procesos.anotar_historial_equipo() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_motivo text   := nullif(current_setting('procesos.motivo_relevo', true), '');
    v_nota   text   := nullif(current_setting('procesos.nota_relevo', true), '');
    v_autor  bigint := nullif(current_setting('procesos.autor_relevo', true), '')::bigint;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.rol = OLD.rol THEN
        RETURN NULL;                                    -- mismo papel: no es un cambio
    END IF;
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        UPDATE procesos.asignaciones_historial h
           SET hasta = CURRENT_DATE, motivo = v_motivo, nota = v_nota, cerrado_por = v_autor
         WHERE h.id_proceso = OLD.id_proceso AND h.id_persona = OLD.id_persona AND h.hasta IS NULL;
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        INSERT INTO procesos.asignaciones_historial (id_proceso, id_persona, rol, desde, registrado_por)
        VALUES (NEW.id_proceso, NEW.id_persona, NEW.rol,
                CASE WHEN TG_OP = 'INSERT' THEN NEW.desde ELSE CURRENT_DATE END, v_autor);
    END IF;
    RETURN NULL;
END $$;

DROP TRIGGER IF EXISTS historial_equipo ON procesos.asignaciones;
CREATE TRIGGER historial_equipo
    AFTER INSERT OR DELETE OR UPDATE OF rol ON procesos.asignaciones
    FOR EACH ROW EXECUTE FUNCTION procesos.anotar_historial_equipo();

-- Lo que ya existe entra al historial como periodo abierto (una sola vez).
INSERT INTO procesos.asignaciones_historial (id_proceso, id_persona, rol, desde)
SELECT a.id_proceso, a.id_persona, a.rol, a.desde
FROM procesos.asignaciones a
WHERE NOT EXISTS (SELECT 1 FROM procesos.asignaciones_historial h
                  WHERE h.id_proceso = a.id_proceso AND h.id_persona = a.id_persona AND h.hasta IS NULL);

-- El servicio agrega periodos y los cierra (por el disparador); no borra historia.
GRANT SELECT, INSERT, UPDATE ON procesos.asignaciones_historial TO svc_procesos;

-- ------------------------------------------------------------------ 2) cierre del proceso
ALTER TABLE procesos.procesos
    ADD COLUMN IF NOT EXISTS fecha_cierre        date,
    ADD COLUMN IF NOT EXISTS motivo_cierre       varchar(25),
    ADD COLUMN IF NOT EXISTS nota_cierre         varchar(300),
    ADD COLUMN IF NOT EXISTS cerrado_por         bigint,
    ADD COLUMN IF NOT EXISTS etapa_antes_cierre  varchar(15);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'procesos_motivo_cierre_valido') THEN
        ALTER TABLE procesos.procesos ADD CONSTRAINT procesos_motivo_cierre_valido CHECK (motivo_cierre IN
            ('SENTENCIA', 'SENTENCIA_ANTICIPADA', 'CONCILIACION', 'TRANSACCION', 'DESISTIMIENTO',
             'RETIRO_DE_LA_DEMANDA', 'PAGO', 'LIQUIDACION', 'RENUNCIA_AL_PODER', 'ARCHIVO', 'OTRO'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'procesos_cierre_completo') THEN
        -- Con motivo hay fecha, y solo un proceso cerrado los lleva.
        ALTER TABLE procesos.procesos ADD CONSTRAINT procesos_cierre_completo CHECK (
            (motivo_cierre IS NULL AND fecha_cierre IS NULL)
            OR (motivo_cierre IS NOT NULL AND fecha_cierre IS NOT NULL AND estado IN ('TERMINADO', 'ARCHIVADO')));
    END IF;
END $$;

COMMENT ON COLUMN procesos.procesos.motivo_cierre      IS 'Por qué se cerró. NULL en los abiertos y en los cerrados antes de esta migración';
COMMENT ON COLUMN procesos.procesos.etapa_antes_cierre IS 'La etapa que tenía al cerrarse: se restaura si se reabre';

-- ------------------------------------------------------------------ 3) aviso de relevo
ALTER TABLE notificaciones.notificaciones DROP CONSTRAINT IF EXISTS notificaciones_evento_check;
ALTER TABLE notificaciones.notificaciones ADD CONSTRAINT notificaciones_evento_check CHECK (evento IN
    ('actuacion.detectada', 'termino.por_vencer', 'termino.vencido', 'proceso.creado', 'audiencia.proxima',
     'portal.resumen_publicado', 'cuenta.seguridad', 'proceso.reasignado'));

COMMIT;
