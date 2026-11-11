-- Se retira la facturación electrónica (F14). Se conservan honorarios, pagos, tarifas, gastos y cartera. Idempotente.
-- Base existente (desde infra/). Orden: 1) respaldo (infra/respaldo/respaldar.sh ahora), 2) desplegar el código que
-- ya no usa estas tablas, 3) esta migración:
--   docker compose exec -T postgres psql -U gabinete_admin -d gabinete -v ON_ERROR_STOP=1 < ../db/migraciones/016-quitar-facturacion.sql
--
-- Protección: una factura emitida no se borra (se anula con nota crédito y se conserva). Si hay facturas o notas
-- crédito guardadas, la migración se detiene sin cambiar nada: hay que decidir qué se hace con ellas primero.
SET client_encoding = 'UTF8';

BEGIN;

DO $$
DECLARE
    facturas bigint := 0;
    notas    bigint := 0;
BEGIN
    IF to_regclass('gabinete.facturas') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM gabinete.facturas' INTO facturas;
    END IF;
    IF to_regclass('gabinete.notas_credito') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM gabinete.notas_credito' INTO notas;
    END IF;
    IF facturas > 0 OR notas > 0 THEN
        RAISE EXCEPTION 'Hay % factura(s) y % nota(s) crédito guardadas: no se borra la facturación. '
            'Respáldelas y decida qué hacer con ellas antes de repetir esta migración.', facturas, notas;
    END IF;
END $$;

-- RESTRICT (el valor por defecto, escrito a propósito): si algo inesperado depende de estas tablas, falla en vez
-- de arrastrarlo. El orden sigue las llaves foráneas: gastos.id_factura -> facturas <- factura_lineas, notas_credito.
DROP TABLE IF EXISTS gabinete.notas_credito RESTRICT;
DROP TABLE IF EXISTS gabinete.factura_lineas RESTRICT;
ALTER TABLE gabinete.gastos DROP COLUMN IF EXISTS id_factura RESTRICT;
DROP TABLE IF EXISTS gabinete.facturas RESTRICT;
DROP TABLE IF EXISTS gabinete.facturacion_config RESTRICT;

COMMIT;
