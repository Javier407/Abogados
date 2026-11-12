-- Términos que se entienden y plazos distintos por tipo de proceso. Idempotente.
-- Base existente (desde infra/):
--   docker compose exec -T postgres psql -U gabinete_admin -d gabinete -v ON_ERROR_STOP=1 < ../db/migraciones/018-terminos-claros.sql
--
-- 1) Cada término guarda la norma que lo fija (se copia de la regla al aceptarlo, o la escribe el abogado).
-- 2) Cada regla dice en palabras CUÁNDO corre el plazo (momento) y QUÉ acto procesal es (acto). Con el acto, la
--    regla más específica gana: la del tipo de proceso sobre la de la rama, y la de la rama sobre la general.
--    Así «subsanar la demanda» son 5 días en civil y 10 en lo contencioso administrativo, sin sugerir las dos.
-- 3) Reglas de referencia para las ramas y tipos que no tenían ninguna (laboral, pensional, penal, administrativo,
--    ejecutivo y monitorio). Quedan SIN VALIDAR: el expediente las muestra como «de referencia» hasta que un socio
--    las revise (UPDATE procesos.reglas_termino SET validada = true, validada_por = …, validada_en = now()).
-- 4) Sentencia anticipada (CGP art. 278): una marca en la sentencia.
SET client_encoding = 'UTF8';

BEGIN;

-- ------------------------------------------------------------------ 1) la norma del término
ALTER TABLE procesos.terminos ADD COLUMN IF NOT EXISTS norma varchar(120);

-- REPLACE solo admite columnas nuevas al final: norma va después de situacion.
CREATE OR REPLACE VIEW procesos.v_terminos AS
SELECT t.id_termino, t.id_proceso, t.id_providencia, t.descripcion, t.fecha_inicio, t.dias_habiles,
       t.fecha_vencimiento, t.estado, t.id_responsable,
       r.dias AS dias_habiles_restantes,
       CASE WHEN t.estado = 'CUMPLIDO'              THEN 'CUMPLIDO'
            WHEN t.fecha_vencimiento < CURRENT_DATE THEN 'VENCIDO'
            WHEN r.dias <= 5                        THEN 'POR_VENCER'
            ELSE 'AL_DIA'
       END AS situacion,
       t.norma
FROM procesos.terminos t
CROSS JOIN LATERAL (
    SELECT CASE WHEN t.fecha_vencimiento >= CURRENT_DATE
                THEN  procesos.dias_habiles_entre(CURRENT_DATE, t.fecha_vencimiento)
                ELSE -procesos.dias_habiles_entre(t.fecha_vencimiento, CURRENT_DATE)
           END AS dias
) r;

-- ------------------------------------------------------------------ 2) reglas legibles y con precedencia
ALTER TABLE procesos.reglas_termino ADD COLUMN IF NOT EXISTS momento varchar(120);
ALTER TABLE procesos.reglas_termino ADD COLUMN IF NOT EXISTS acto    varchar(30);

COMMENT ON COLUMN procesos.reglas_termino.momento IS 'Cuándo empieza a correr el plazo, en palabras (para mostrarlo)';
COMMENT ON COLUMN procesos.reglas_termino.acto    IS 'Acto procesal (CONTESTAR, SUBSANAR…). Entre reglas del mismo acto gana la más específica: tipo > rama > general';

UPDATE procesos.reglas_termino SET acto = 'CONTESTAR', momento = 'Cuando le notifican el auto que admite la demanda'
 WHERE id_regla = 1 AND acto IS NULL;
UPDATE procesos.reglas_termino SET acto = 'SUBSANAR', momento = 'Cuando el juez inadmite la demanda'
 WHERE id_regla = 2 AND acto IS NULL;
UPDATE procesos.reglas_termino SET acto = 'EXCEPCIONES', momento = 'Cuando corren traslado de las excepciones de mérito'
 WHERE id_regla = 3 AND acto IS NULL;
UPDATE procesos.reglas_termino SET acto = 'REPOSICION', momento = 'Desde la notificación de cualquier auto'
 WHERE id_regla IN (4, 5) AND acto IS NULL;
UPDATE procesos.reglas_termino SET acto = 'APELAR_SENTENCIA',
       momento = 'Desde la notificación de la sentencia escrita (la dictada en audiencia se apela ahí mismo)'
 WHERE id_regla IN (6, 7, 9) AND acto IS NULL;
UPDATE procesos.reglas_termino SET acto = 'IMPUGNAR', momento = 'Desde la notificación del fallo de tutela'
 WHERE id_regla = 8 AND acto IS NULL;

-- ------------------------------------------------------------------ 3) reglas por rama y por tipo de proceso
-- El tipo se busca por nombre (no por número). Una regla no se repite: se reconoce por rama, tipo, acto y providencia.
WITH nuevas (tipo_providencia, codigo_rama, tipo_nombre, patron, lado, descripcion, dias, norma, prioridad, acto, momento) AS (
    VALUES
    -- Civil: ejecutivo y monitorio
    ('AUTO', 'CIVIL', 'Ejecutivo', 'mandamiento', 'DEMANDADO',
        'Pagar la obligación', 5, 'CGP art. 431', 1, 'PAGAR', 'Cuando le notifican el mandamiento de pago'),
    ('AUTO', 'CIVIL', 'Ejecutivo', 'mandamiento', 'DEMANDADO',
        'Proponer excepciones de mérito', 10, 'CGP art. 442', 1, 'EXCEPCIONES_MERITO', 'Cuando le notifican el mandamiento de pago'),
    ('AUTO', 'CIVIL', 'Ejecutivo', 'traslado.*excepciones', 'DEMANDANTE',
        'Pronunciarse sobre las excepciones de mérito', 10, 'CGP art. 443', 1, 'EXCEPCIONES', 'Cuando corren traslado de las excepciones de mérito'),
    ('AUTO', 'CIVIL', 'Monitorio', 'requerimiento|requiere', 'DEMANDADO',
        'Pagar o explicar por qué no debe', 10, 'CGP art. 421', 1, 'PAGAR', 'Cuando le notifican el requerimiento de pago'),
    -- Civil y familia: apelación de autos
    ('AUTO', 'CIVIL', NULL, '.', NULL,
        'Recurso de apelación contra el auto (si procede)', 3, 'CGP art. 322', 2, 'APELAR_AUTO', 'Desde la notificación de un auto apelable'),
    ('AUTO', 'FAMILIA', NULL, '.', NULL,
        'Recurso de apelación contra el auto (si procede)', 3, 'CGP art. 322', 2, 'APELAR_AUTO', 'Desde la notificación de un auto apelable'),
    -- Laboral y pensional (Código Procesal del Trabajo y de la Seguridad Social)
    ('AUTO', 'LABORAL', NULL, 'inadmite|devuelve', 'DEMANDANTE',
        'Subsanar la demanda', 5, 'CPTSS art. 28', 1, 'SUBSANAR', 'Cuando el juez devuelve la demanda para corregirla'),
    ('AUTO', 'LABORAL', NULL, '.', NULL,
        'Recurso de reposición contra el auto (si procede)', 2, 'CPTSS art. 63', 2, 'REPOSICION', 'Desde la notificación del auto por estado'),
    ('AUTO', 'LABORAL', NULL, '.', NULL,
        'Recurso de apelación contra el auto (si procede)', 5, 'CPTSS art. 65', 2, 'APELAR_AUTO', 'Desde la notificación de un auto apelable por estado'),
    ('AUTO', 'LABORAL', 'Ejecutivo laboral', 'mandamiento', 'DEMANDADO',
        'Pagar la obligación', 5, 'CGP art. 431 (CPTSS art. 145)', 1, 'PAGAR', 'Cuando le notifican el mandamiento de pago'),
    ('AUTO', 'LABORAL', 'Ejecutivo laboral', 'mandamiento', 'DEMANDADO',
        'Proponer excepciones de mérito', 10, 'CGP art. 442 (CPTSS art. 145)', 1, 'EXCEPCIONES_MERITO', 'Cuando le notifican el mandamiento de pago'),
    ('AUTO', 'PENSIONAL', NULL, 'inadmite|devuelve', 'DEMANDANTE',
        'Subsanar la demanda', 5, 'CPTSS art. 28', 1, 'SUBSANAR', 'Cuando el juez devuelve la demanda para corregirla'),
    ('AUTO', 'PENSIONAL', NULL, '.', NULL,
        'Recurso de reposición contra el auto (si procede)', 2, 'CPTSS art. 63', 2, 'REPOSICION', 'Desde la notificación del auto por estado'),
    ('AUTO', 'PENSIONAL', NULL, '.', NULL,
        'Recurso de apelación contra el auto (si procede)', 5, 'CPTSS art. 65', 2, 'APELAR_AUTO', 'Desde la notificación de un auto apelable por estado'),
    -- Contencioso administrativo (CPACA)
    ('AUTO', 'ADMINISTRATIVO', NULL, 'inadmite', 'DEMANDANTE',
        'Subsanar la demanda', 10, 'CPACA art. 170', 1, 'SUBSANAR', 'Cuando el juez inadmite la demanda'),
    ('AUTO', 'ADMINISTRATIVO', NULL, '.', NULL,
        'Recurso de reposición contra el auto (si procede)', 3, 'CPACA art. 242', 2, 'REPOSICION', 'Desde la notificación de cualquier auto'),
    ('AUTO', 'ADMINISTRATIVO', NULL, '.', NULL,
        'Recurso de apelación contra el auto (si procede)', 3, 'CPACA art. 244', 2, 'APELAR_AUTO', 'Desde la notificación de un auto apelable'),
    -- Penal (Ley 906 de 2004)
    ('SENTENCIA', 'PENAL', NULL, '.', NULL,
        'Apelar la sentencia (si es desfavorable)', 5, 'Ley 906 de 2004 art. 179', 1, 'APELAR_SENTENCIA', 'Desde la lectura o la notificación de la sentencia')
)
INSERT INTO procesos.reglas_termino
       (id_regla, tipo_providencia, codigo_rama, id_tipo, patron, lado, descripcion, dias, norma, prioridad, acto, momento)
SELECT (SELECT coalesce(max(id_regla), 0) FROM procesos.reglas_termino) + row_number() OVER (),
       n.tipo_providencia, n.codigo_rama, t.id_tipo, n.patron, n.lado, n.descripcion, n.dias, n.norma, n.prioridad,
       n.acto, n.momento
FROM nuevas n
LEFT JOIN procesos.tipos_proceso t ON t.codigo_rama = n.codigo_rama AND t.nombre = n.tipo_nombre
WHERE (n.tipo_nombre IS NULL OR t.id_tipo IS NOT NULL)
  AND NOT EXISTS (
        SELECT 1 FROM procesos.reglas_termino r
        WHERE r.tipo_providencia = n.tipo_providencia
          AND r.codigo_rama = n.codigo_rama
          AND r.id_tipo IS NOT DISTINCT FROM t.id_tipo
          AND r.acto = n.acto);

-- ------------------------------------------------------------------ 4) sentencia anticipada
ALTER TABLE procesos.providencias ADD COLUMN IF NOT EXISTS anticipada boolean NOT NULL DEFAULT false;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'providencias_anticipada_es_sentencia') THEN
        ALTER TABLE procesos.providencias
            ADD CONSTRAINT providencias_anticipada_es_sentencia CHECK (NOT anticipada OR tipo = 'SENTENCIA');
    END IF;
END $$;
COMMENT ON COLUMN procesos.providencias.anticipada IS 'Sentencia anticipada (CGP art. 278): se dictó sin agotar las etapas';

COMMIT;
