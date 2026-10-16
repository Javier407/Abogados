-- F2: festivos de 2027 y clave para no repetir avisos. Idempotente: se puede ejecutar varias veces.
-- Base nueva: 00-init.sh la aplica después de los datos de prueba.
-- Base existente: docker compose exec -T postgres psql -U gabinete_admin -d gabinete < ../db/migraciones/001-f2-terminos-y-avisos.sql
SET client_encoding = 'UTF8';

-- Festivos de Colombia 2027 (Ley 51 de 1983). Pascua: 28 de marzo de 2027.
INSERT INTO procesos.festivos (fecha, descripcion, tipo) VALUES
    ('2027-03-22', 'Día de San José',                    'FESTIVO'),
    ('2027-03-23', 'Semana Santa',                       'SEMANA_SANTA'),
    ('2027-03-24', 'Semana Santa',                       'SEMANA_SANTA'),
    ('2027-03-25', 'Jueves Santo',                       'FESTIVO'),
    ('2027-03-26', 'Viernes Santo',                      'FESTIVO'),
    ('2027-05-01', 'Día del Trabajo',                    'FESTIVO'),
    ('2027-05-10', 'Ascensión del Señor',                'FESTIVO'),
    ('2027-05-31', 'Corpus Christi',                     'FESTIVO'),
    ('2027-06-07', 'Sagrado Corazón',                    'FESTIVO'),
    ('2027-07-05', 'San Pedro y San Pablo',              'FESTIVO'),
    ('2027-07-20', 'Día de la Independencia',            'FESTIVO'),
    ('2027-08-07', 'Batalla de Boyacá',                  'FESTIVO'),
    ('2027-08-16', 'Asunción de la Virgen',              'FESTIVO'),
    ('2027-10-18', 'Día de la Raza',                     'FESTIVO'),
    ('2027-11-01', 'Todos los Santos',                   'FESTIVO'),
    ('2027-11-15', 'Independencia de Cartagena',         'FESTIVO'),
    ('2027-12-08', 'Inmaculada Concepción',              'FESTIVO'),
    ('2027-12-25', 'Navidad',                            'FESTIVO')
ON CONFLICT (fecha) DO NOTHING;

INSERT INTO procesos.festivos (fecha, descripcion, tipo)
SELECT d::date, 'Vacancia judicial', 'VACANCIA'
FROM generate_series(DATE '2027-12-20', DATE '2028-01-10', interval '1 day') AS v(d)
ON CONFLICT (fecha) DO NOTHING;

-- Cada aviso lleva una clave (evento, término, persona, canal y día). Si el mismo evento llega dos veces
-- el mismo día (reintentos, revisión manual y automática), no se repite el aviso.
ALTER TABLE notificaciones.notificaciones ADD COLUMN IF NOT EXISTS clave varchar(150);
CREATE UNIQUE INDEX IF NOT EXISTS uk_notif_clave ON notificaciones.notificaciones (clave);
