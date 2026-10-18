-- F7: audiencias con hora y lugar, pruebas con fecha de entrega, y reglas de términos sugeridos. Idempotente.
-- Base existente: docker compose exec -T postgres psql -U gabinete_admin -d gabinete < ../db/migraciones/005-f7-agenda-y-pruebas.sql
SET client_encoding = 'UTF8';

-- Audiencias (y cualquier actuación programada): hora y lugar (sala o enlace de la audiencia virtual).
ALTER TABLE procesos.actuaciones ADD COLUMN IF NOT EXISTS hora  time;
ALTER TABLE procesos.actuaciones ADD COLUMN IF NOT EXISTS lugar varchar(200);
CREATE INDEX IF NOT EXISTS ix_actuaciones_agenda ON procesos.actuaciones (fecha) WHERE tipo = 'AUDIENCIA';

-- Pruebas: cuándo debe entregarse (dictamen, documentos) y el término que lo controla.
ALTER TABLE procesos.pruebas ADD COLUMN IF NOT EXISTS fecha_entrega date;
ALTER TABLE procesos.pruebas ADD COLUMN IF NOT EXISTS id_termino integer REFERENCES procesos.terminos ON DELETE SET NULL;
ALTER TABLE procesos.pruebas ADD COLUMN IF NOT EXISTS creada date NOT NULL DEFAULT CURRENT_DATE;

-- Reglas para sugerir términos cuando llega una providencia. Son de REFERENCIA: validada = false hasta que un
-- abogado las revise (docs/01-dominio.md §1.6). El sistema solo sugiere; el abogado confirma cada término.
--   patron: expresión regular sobre la clase de la providencia SIN tildes y en minúsculas.
--   lado:   DEMANDANTE (demandante, accionante, víctima), DEMANDADO (demandado, accionado, procesado) o NULL (ambos).
--   dias:   NULL = los días de contestación del tipo de proceso (tipos_proceso.termino_contestacion_dias).
CREATE TABLE IF NOT EXISTS procesos.reglas_termino (
    id_regla          smallint     PRIMARY KEY,
    tipo_providencia  varchar(10)  NOT NULL CHECK (tipo_providencia IN ('AUTO', 'SENTENCIA')),
    codigo_rama       varchar(20)  REFERENCES procesos.ramas,          -- NULL = todas las ramas
    id_tipo           smallint     REFERENCES procesos.tipos_proceso,  -- NULL = todos los tipos de la rama
    patron            varchar(200) NOT NULL,
    lado              varchar(12)  CHECK (lado IN ('DEMANDANTE', 'DEMANDADO')),
    descripcion       varchar(150) NOT NULL,
    dias              smallint     CHECK (dias BETWEEN 1 AND 365),
    norma             varchar(120) NOT NULL,
    prioridad         smallint     NOT NULL DEFAULT 1,                 -- 1 principal, 2 opcional ("si procede")
    validada          boolean      NOT NULL DEFAULT false
);

INSERT INTO procesos.reglas_termino (id_regla, tipo_providencia, codigo_rama, id_tipo, patron, lado, descripcion, dias, norma, prioridad) VALUES
    ( 1, 'AUTO',      NULL,             NULL, '\badmite|admisorio', 'DEMANDADO',
         'Contestar la demanda', NULL, 'Término de contestación del tipo de proceso', 1),
    ( 2, 'AUTO',      NULL,             NULL, 'inadmite', 'DEMANDANTE',
         'Subsanar la demanda', 5, 'CGP art. 90', 1),
    ( 3, 'AUTO',      'CIVIL',          NULL, 'traslado.*excepciones', 'DEMANDANTE',
         'Pronunciarse sobre las excepciones de mérito', 5, 'CGP art. 370', 1),
    ( 4, 'AUTO',      'CIVIL',          NULL, '.', NULL,
         'Recurso de reposición contra el auto (si procede)', 3, 'CGP art. 318', 2),
    ( 5, 'AUTO',      'FAMILIA',        NULL, '.', NULL,
         'Recurso de reposición contra el auto (si procede)', 3, 'CGP art. 318', 2),
    ( 6, 'SENTENCIA', 'CIVIL',          NULL, '.', NULL,
         'Apelar la sentencia (si es desfavorable)', 3, 'CGP art. 322', 1),
    ( 7, 'SENTENCIA', 'FAMILIA',        NULL, '.', NULL,
         'Apelar la sentencia (si es desfavorable)', 3, 'CGP art. 322', 1),
    ( 8, 'SENTENCIA', 'CONSTITUCIONAL', 32,   '.', NULL,
         'Impugnar el fallo de tutela (si es desfavorable)', 3, 'Decreto 2591 de 1991 art. 31', 1),
    ( 9, 'SENTENCIA', 'ADMINISTRATIVO', NULL, '.', NULL,
         'Apelar la sentencia (si es desfavorable)', 10, 'CPACA art. 247', 1)
ON CONFLICT (id_regla) DO NOTHING;

-- Nuevo evento que avisa el servicio notificaciones: la audiencia del próximo día hábil.
ALTER TABLE notificaciones.notificaciones DROP CONSTRAINT IF EXISTS notificaciones_evento_check;
ALTER TABLE notificaciones.notificaciones ADD CONSTRAINT notificaciones_evento_check CHECK (evento IN
    ('actuacion.detectada', 'termino.por_vencer', 'termino.vencido', 'proceso.creado', 'audiencia.proxima'));

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA procesos TO svc_procesos;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA procesos TO svc_procesos;
