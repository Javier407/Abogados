-- La firma deja de ofrecer la rama Constitucional (tutela, acción popular y acción de grupo). Se DESACTIVA, no se
-- borra: los procesos, términos, reglas, el área y las tarifas que ya existen quedan intactos y se siguen viendo;
-- solo dejan de aparecer en las listas para crear cosas nuevas. Idempotente.
-- Base existente (desde infra/):
--   docker compose exec -T postgres psql -U gabinete_admin -d gabinete -v ON_ERROR_STOP=1 < ../db/migraciones/017-desactivar-constitucional.sql
-- Para volver a ofrecer una rama: UPDATE ... SET activa = true (y activo = true en sus tipos) con el mismo WHERE.
SET client_encoding = 'UTF8';

BEGIN;

ALTER TABLE procesos.ramas          ADD COLUMN IF NOT EXISTS activa boolean NOT NULL DEFAULT true;
ALTER TABLE procesos.tipos_proceso  ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
ALTER TABLE gabinete.areas_practica ADD COLUMN IF NOT EXISTS activa boolean NOT NULL DEFAULT true;
ALTER TABLE gabinete.tarifas        ADD COLUMN IF NOT EXISTS activa boolean NOT NULL DEFAULT true;

COMMENT ON COLUMN procesos.ramas.activa         IS 'false = la firma ya no la ofrece: no sale en listas ni admite procesos nuevos';
COMMENT ON COLUMN procesos.tipos_proceso.activo IS 'false = no admite procesos nuevos; los existentes lo conservan';

UPDATE procesos.ramas          SET activa = false WHERE codigo      = 'CONSTITUCIONAL' AND activa;
UPDATE procesos.tipos_proceso  SET activo = false WHERE codigo_rama = 'CONSTITUCIONAL' AND activo;
UPDATE gabinete.areas_practica SET activa = false WHERE codigo_rama = 'CONSTITUCIONAL' AND activa;
UPDATE gabinete.tarifas        SET activa = false WHERE codigo_rama = 'CONSTITUCIONAL' AND activa;

COMMIT;
