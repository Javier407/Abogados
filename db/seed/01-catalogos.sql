-- Catálogos del servicio procesos.
SET client_encoding = 'UTF8';

INSERT INTO procesos.ramas (codigo, nombre, norma) VALUES
    ('CIVIL',          'Civil y comercial',            'Código General del Proceso (Ley 1564 de 2012)'),
    ('FAMILIA',        'Familia',                      'Código General del Proceso (Ley 1564 de 2012)'),
    ('LABORAL',        'Laboral',                      'Código Procesal del Trabajo y de la Seguridad Social'),
    ('PENSIONAL',      'Pensional (seguridad social)', 'Código Procesal del Trabajo y de la Seguridad Social'),
    ('PENAL',          'Penal',                        'Ley 906 de 2004 (sistema penal acusatorio)'),
    ('ADMINISTRATIVO', 'Contencioso administrativo',   'Ley 1437 de 2011 (CPACA)'),
    ('CONSTITUCIONAL', 'Constitucional',               'Decreto 2591 de 1991 y Ley 472 de 1998');

-- termino_contestacion_dias: término de referencia en días hábiles (A VALIDAR con un abogado).
INSERT INTO procesos.tipos_proceso (id_tipo, codigo_rama, nombre, termino_contestacion_dias) VALUES
    ( 1, 'CIVIL',          'Verbal',                                   20),
    ( 2, 'CIVIL',          'Verbal sumario',                           10),
    ( 3, 'CIVIL',          'Ejecutivo',                                10),
    ( 4, 'CIVIL',          'Monitorio',                                10),
    ( 5, 'CIVIL',          'Divisorio',                                10),
    ( 6, 'CIVIL',          'Deslinde y amojonamiento',                 10),
    ( 7, 'CIVIL',          'Restitución de inmueble arrendado',        20),
    ( 8, 'CIVIL',          'Sucesión',                                 NULL),
    ( 9, 'FAMILIA',        'Divorcio contencioso',                     20),
    (10, 'FAMILIA',        'Divorcio de mutuo acuerdo',                NULL),
    (11, 'FAMILIA',        'Alimentos',                                10),
    (12, 'FAMILIA',        'Custodia y cuidado personal',              10),
    (13, 'FAMILIA',        'Filiación',                                20),
    (14, 'FAMILIA',        'Liquidación de sociedad conyugal',         10),
    (15, 'FAMILIA',        'Sucesión',                                 NULL),
    (16, 'LABORAL',        'Ordinario laboral de primera instancia',   10),
    (17, 'LABORAL',        'Ordinario laboral de única instancia',     NULL),
    (18, 'LABORAL',        'Ejecutivo laboral',                        10),
    (19, 'LABORAL',        'Fuero sindical',                           NULL),
    (20, 'PENSIONAL',      'Ordinario contra Colpensiones o AFP',      10),
    (21, 'PENSIONAL',      'Reconocimiento de pensión',                10),
    (22, 'PENSIONAL',      'Reliquidación de pensión',                 10),
    (23, 'PENAL',          'Indagación',                               NULL),
    (24, 'PENAL',          'Imputación y acusación',                   NULL),
    (25, 'PENAL',          'Juicio oral',                              NULL),
    (26, 'PENAL',          'Procedimiento abreviado',                  NULL),
    (27, 'PENAL',          'Incidente de reparación integral',         NULL),
    (28, 'ADMINISTRATIVO', 'Nulidad y restablecimiento del derecho',   30),
    (29, 'ADMINISTRATIVO', 'Reparación directa',                       30),
    (30, 'ADMINISTRATIVO', 'Controversias contractuales',              30),
    (31, 'ADMINISTRATIVO', 'Nulidad simple',                           30),
    (32, 'CONSTITUCIONAL', 'Tutela',                                   NULL),
    (33, 'CONSTITUCIONAL', 'Acción popular',                           10),
    (34, 'CONSTITUCIONAL', 'Acción de grupo',                          10);

-- Despachos. DANE: 11001 Bogotá, 05001 Medellín, 25000 Cundinamarca (tribunal).
-- Los códigos de entidad y especialidad son DE EJEMPLO para la base de pruebas.
INSERT INTO procesos.despachos (id_despacho, nombre, ciudad, cod_dane, cod_entidad, cod_especialidad, cod_despacho, codigo_rama, email) VALUES
    ( 1, 'Juzgado 015 Civil Municipal de Bogotá',                           'Bogotá',   '11001', '40', '03', '015', 'CIVIL',          'cmpl15bt@ejemplo.gov.co'),
    ( 2, 'Juzgado 032 Civil del Circuito de Bogotá',                        'Bogotá',   '11001', '31', '03', '032', 'CIVIL',          'ccto32bt@ejemplo.gov.co'),
    ( 3, 'Juzgado 012 de Familia de Bogotá',                                'Bogotá',   '11001', '31', '10', '012', 'FAMILIA',        'flia12bt@ejemplo.gov.co'),
    ( 4, 'Juzgado 008 Laboral del Circuito de Bogotá',                      'Bogotá',   '11001', '31', '05', '008', 'LABORAL',        'lcto08bt@ejemplo.gov.co'),
    ( 5, 'Juzgado 021 Laboral del Circuito de Medellín',                    'Medellín', '05001', '31', '05', '021', 'LABORAL',        'lcto21med@ejemplo.gov.co'),
    ( 6, 'Juzgado 004 Municipal de Pequeñas Causas Laborales de Medellín',  'Medellín', '05001', '41', '05', '004', 'LABORAL',        'pclab04med@ejemplo.gov.co'),
    ( 7, 'Juzgado 027 Penal del Circuito con Función de Conocimiento de Medellín', 'Medellín', '05001', '31', '09', '027', 'PENAL', 'pcto27med@ejemplo.gov.co'),
    ( 8, 'Juzgado 006 Penal Municipal con Función de Control de Garantías de Medellín', 'Medellín', '05001', '40', '04', '006', 'PENAL', 'pmgar06med@ejemplo.gov.co'),
    ( 9, 'Juzgado 035 Administrativo de Bogotá',                            'Bogotá',   '11001', '33', '31', '035', 'ADMINISTRATIVO', 'adm35bt@ejemplo.gov.co'),
    (10, 'Tribunal Administrativo de Cundinamarca — Sección Segunda',       'Bogotá',   '25000', '23', '42', '000', 'ADMINISTRATIVO', 'tadmcun2@ejemplo.gov.co'),
    (11, 'Tribunal Superior de Bogotá — Sala Laboral',                      'Bogotá',   '11001', '22', '05', '000', 'LABORAL',        'tsblab@ejemplo.gov.co'),
    (12, 'Juzgado 020 Civil Municipal de Medellín',                         'Medellín', '05001', '40', '03', '020', 'CIVIL',          'cmpl20med@ejemplo.gov.co'),
    (13, 'Juzgado 005 de Familia de Medellín',                              'Medellín', '05001', '31', '10', '005', 'FAMILIA',        'flia05med@ejemplo.gov.co'),
    (14, 'Tribunal Superior de Bogotá — Sala Civil',                        'Bogotá',   '11001', '22', '03', '000', 'CIVIL',          'tsbciv@ejemplo.gov.co');

-- Festivos de Colombia (Ley 51 de 1983, "ley Emiliani") 2026 y comienzo de 2027.
INSERT INTO procesos.festivos (fecha, descripcion, tipo) VALUES
    ('2026-01-01', 'Año Nuevo',                          'FESTIVO'),
    ('2026-01-12', 'Día de los Reyes Magos',             'FESTIVO'),
    ('2026-03-23', 'Día de San José',                    'FESTIVO'),
    ('2026-04-02', 'Jueves Santo',                       'FESTIVO'),
    ('2026-04-03', 'Viernes Santo',                      'FESTIVO'),
    ('2026-05-01', 'Día del Trabajo',                    'FESTIVO'),
    ('2026-05-18', 'Ascensión del Señor',                'FESTIVO'),
    ('2026-06-08', 'Corpus Christi',                     'FESTIVO'),
    ('2026-06-15', 'Sagrado Corazón',                    'FESTIVO'),
    ('2026-06-29', 'San Pedro y San Pablo',              'FESTIVO'),
    ('2026-07-20', 'Día de la Independencia',            'FESTIVO'),
    ('2026-08-07', 'Batalla de Boyacá',                  'FESTIVO'),
    ('2026-08-17', 'Asunción de la Virgen',              'FESTIVO'),
    ('2026-10-12', 'Día de la Raza',                     'FESTIVO'),
    ('2026-11-02', 'Todos los Santos',                   'FESTIVO'),
    ('2026-11-16', 'Independencia de Cartagena',         'FESTIVO'),
    ('2026-12-08', 'Inmaculada Concepción',              'FESTIVO'),
    ('2026-12-25', 'Navidad',                            'FESTIVO'),
    ('2027-01-01', 'Año Nuevo',                          'FESTIVO'),
    ('2027-01-11', 'Día de los Reyes Magos',             'FESTIVO'),
    -- Semana Santa: lunes a miércoles sin servicio judicial (jueves y viernes ya son festivos)
    ('2026-03-30', 'Semana Santa',                       'SEMANA_SANTA'),
    ('2026-03-31', 'Semana Santa',                       'SEMANA_SANTA'),
    ('2026-04-01', 'Semana Santa',                       'SEMANA_SANTA');

-- Vacancia judicial colectiva: 20 de diciembre a 10 de enero (no corren términos).
INSERT INTO procesos.festivos (fecha, descripcion, tipo)
SELECT d::date, 'Vacancia judicial', 'VACANCIA'
FROM (SELECT generate_series(DATE '2025-12-20', DATE '2026-01-10', interval '1 day')
      UNION
      SELECT generate_series(DATE '2026-12-20', DATE '2027-01-10', interval '1 day')) AS v(d)
ON CONFLICT (fecha) DO NOTHING;
