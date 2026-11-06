-- F17: días hábiles sin recorrer día por día. Idempotente.
-- Base existente: docker compose exec -T postgres psql -U gabinete_admin -d gabinete < ../db/migraciones/013-f17-dias-habiles-rapidos.sql
--
-- Antes, dias_habiles_entre generaba cada día del intervalo y consultaba festivos por cada uno, y v_terminos la
-- llamaba dos veces por término: la lista de términos era la pantalla más lenta con carga (p95 457 ms, docs/09).
-- Ahora: 5 hábiles por semana completa + los días sueltos del final que caen de lunes a viernes − los festivos
-- de lunes a viernes dentro del intervalo (una búsqueda por rango en la llave primaria de festivos).
SET client_encoding = 'UTF8';

-- Días hábiles en el intervalo (desde, hasta]. Mismo resultado que la versión anterior; 0 si hasta <= desde.
CREATE OR REPLACE FUNCTION procesos.dias_habiles_entre(desde date, hasta date) RETURNS integer
LANGUAGE sql STABLE AS $$
    SELECT CASE WHEN hasta <= desde THEN 0 ELSE
        5 * ((hasta - desde) / 7)
        + (SELECT count(*)::integer
           FROM generate_series(1, (hasta - desde) % 7) AS i
           WHERE extract(isodow FROM desde + 7 * ((hasta - desde) / 7) + i) < 6)
        - (SELECT count(*)::integer
           FROM procesos.festivos f
           WHERE f.fecha > desde AND f.fecha <= hasta AND extract(isodow FROM f.fecha) < 6)
    END;
$$;

-- La situación usa los días restantes ya calculados (una llamada por término, no dos). Columnas explícitas: la
-- vista conserva las de la tabla al crearse (sin cumplido_en, que llegó después) y REPLACE no puede cambiarlas.
CREATE OR REPLACE VIEW procesos.v_terminos AS
SELECT t.id_termino, t.id_proceso, t.id_providencia, t.descripcion, t.fecha_inicio, t.dias_habiles,
       t.fecha_vencimiento, t.estado, t.id_responsable,
       r.dias AS dias_habiles_restantes,
       CASE WHEN t.estado = 'CUMPLIDO'              THEN 'CUMPLIDO'
            WHEN t.fecha_vencimiento < CURRENT_DATE THEN 'VENCIDO'
            WHEN r.dias <= 5                        THEN 'POR_VENCER'
            ELSE 'AL_DIA'
       END AS situacion
FROM procesos.terminos t
CROSS JOIN LATERAL (
    SELECT CASE WHEN t.fecha_vencimiento >= CURRENT_DATE
                THEN  procesos.dias_habiles_entre(CURRENT_DATE, t.fecha_vencimiento)
                ELSE -procesos.dias_habiles_entre(t.fecha_vencimiento, CURRENT_DATE)
           END AS dias
) r;
