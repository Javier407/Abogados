-- La firma ficticia "Rodríguez & Asociados Abogados SAS": sedes, personal, áreas, clientes, peritos y tarifas.
-- Todos los nombres, documentos y datos de contacto son FICTICIOS.
SET client_encoding = 'UTF8';

INSERT INTO gabinete.firma (id_firma, nit, razon_social, email, telefono) VALUES
    (1, '901234567-8', 'Rodríguez & Asociados Abogados SAS', 'contacto@rodriguezasociados.co', '6013216540');

INSERT INTO gabinete.sedes (id_sede, id_firma, nombre, ciudad, direccion, telefono, es_principal) VALUES
    (1, 1, 'Sede Bogotá',   'Bogotá',   'Carrera 7 # 71-21, oficina 801', '6013216540', true),
    (2, 1, 'Sede Medellín', 'Medellín', 'Carrera 43A # 1-50, oficina 1204', '6044448210', false);

INSERT INTO gabinete.cargos (id_cargo, codigo, nombre, es_abogado) VALUES
    (1, 'SOCIO',       'Socio',                 true),
    (2, 'ASOCIADO',    'Abogado asociado',      true),
    (3, 'JUNIOR',      'Abogado junior',        true),
    (4, 'DEPENDIENTE', 'Dependiente judicial',  false),
    (5, 'PARALEGAL',   'Paralegal',             false),
    (6, 'SECRETARIA',  'Secretaria',            false),
    (7, 'CONTADOR',    'Contador',              false);

-- Personal (id_persona = cédula). id_area se completa después de crear las áreas.
INSERT INTO gabinete.personal (id_persona, nombres, apellidos, email, telefono, id_cargo, id_sede, tarjeta_profesional, tarifa_hora, fecha_ingreso, id_usuario) VALUES
    (79123456,   'Andrés Felipe',    'Rodríguez Mejía',  'arodriguez@rodriguezasociados.co', '3101234567', 1, 1, '98765',  450000, '2012-02-01', 79123456),
    (52345678,   'Catalina',         'Gómez Restrepo',   'cgomez@rodriguezasociados.co',     '3112345678', 1, 2, '112233', 420000, '2013-06-15', 52345678),
    (80234567,   'Julián Andrés',    'Torres Vargas',    'jtorres@rodriguezasociados.co',    '3123456789', 2, 1, '187452', 280000, '2017-03-01', 80234567),
    (1020456789, 'Laura Marcela',    'Pineda Ruiz',      'lpineda@rodriguezasociados.co',    '3134567890', 2, 1, '201334', 260000, '2018-08-13', 1020456789),
    (1037612345, 'Santiago',         'Henao Gil',        'shenao@rodriguezasociados.co',     '3145678901', 2, 2, '214578', 300000, '2016-11-21', 1037612345),
    (43567890,   'Diana Patricia',   'Ochoa Arango',     'dochoa@rodriguezasociados.co',     '3156789012', 2, 2, '176890', 250000, '2015-01-19', 43567890),
    (1019087654, 'Camila Andrea',    'Rojas Peña',       'crojas@rodriguezasociados.co',     '3167890123', 3, 1, '356712', 150000, '2023-02-06', 1019087654),
    (1152443210, 'Mateo',            'Cardona Ríos',     'mcardona@rodriguezasociados.co',   '3178901234', 3, 2, '361245', 140000, '2023-07-03', 1152443210),
    (1023987456, 'Valentina',        'Suárez Mora',      'vsuarez@rodriguezasociados.co',    '3189012345', 3, 1, '358901', 140000, '2024-01-15', 1023987456),
    (1001234567, 'Sebastián',        'Castro León',      'scastro@rodriguezasociados.co',    '3190123456', 4, 1, NULL,      60000, '2024-08-01', 1001234567),
    (1036654321, 'Isabela',          'Muñoz Zapata',     'imunoz@rodriguezasociados.co',     '3201234567', 4, 2, NULL,      60000, '2025-02-03', 1036654321),
    (1018765432, 'Paula Andrea',     'Niño Díaz',        'pnino@rodriguezasociados.co',      '3212345678', 5, 1, NULL,      55000, '2022-04-18', 1018765432),
    (52987654,   'Gloria Esperanza', 'Pardo Rincón',     'gpardo@rodriguezasociados.co',     '3223456789', 6, 1, NULL,       NULL, '2014-09-01', 52987654),
    (79876543,   'Hernán Darío',     'Quintero Silva',   'hquintero@rodriguezasociados.co',  '3234567890', 7, 1, NULL,       NULL, '2016-05-02', 79876543);

-- Áreas de práctica: una por rama, dirigida por uno de los dos socios.
INSERT INTO gabinete.areas_practica (id_area, codigo_rama, nombre, id_director) VALUES
    (1, 'CIVIL',          'Civil y comercial',          79123456),
    (2, 'FAMILIA',        'Familia',                    79123456),
    (3, 'LABORAL',        'Laboral',                    52345678),
    (4, 'PENSIONAL',      'Seguridad social y pensiones', 52345678),
    (5, 'PENAL',          'Penal',                      52345678),
    (6, 'ADMINISTRATIVO', 'Derecho administrativo',     79123456),
    (7, 'CONSTITUCIONAL', 'Constitucional y acciones públicas', 79123456);

UPDATE gabinete.personal p SET id_area = a.id_area
FROM (VALUES (79123456, 1), (52345678, 3), (80234567, 2), (1020456789, 6), (1037612345, 5),
             (43567890, 4), (1019087654, 1), (1152443210, 3), (1023987456, 7)) AS a(id_persona, id_area)
WHERE p.id_persona = a.id_persona;

-- Clientes (id explícito para que los procesos los referencien).
INSERT INTO gabinete.clientes (id_cliente, tipo_persona, tipo_documento, numero_documento, nombre, email, telefono, direccion, ciudad, id_sede, creado) VALUES
    ( 1, 'JURIDICA', 'NIT', '900456789',  'Inversiones Andina SAS',                  'juridico@andina-ejemplo.co',     '6017004512', 'Calle 93 # 11-26',        'Bogotá',   1, '2025-02-20'),
    ( 2, 'JURIDICA', 'NIT', '830112233',  'Constructora Altamira SAS',               'legal@altamira-ejemplo.co',      '6016123344', 'Avenida 19 # 104-37',     'Bogotá',   1, '2024-07-10'),
    ( 3, 'NATURAL',  'CC',  '52678123',   'María Fernanda López Castro',             'mflopez@correo-ejemplo.co',      '3005551201', 'Carrera 15 # 82-40',      'Bogotá',   1, '2026-04-28'),
    ( 4, 'JURIDICA', 'NIT', '901334455',  'Ferretería El Tornillo SAS',              'gerencia@eltornillo-ejemplo.co', '6043519087', 'Carrera 52 # 45-12',      'Medellín', 2, '2026-08-20'),
    ( 5, 'NATURAL',  'CC',  '19456789',   'Jorge Enrique Salazar Mora',              'jsalazar@correo-ejemplo.co',     '3105551202', 'Calle 127 # 7-45',        'Bogotá',   1, '2023-10-02'),
    ( 6, 'NATURAL',  'CC',  '1032456781', 'Carolina Méndez Ortiz',                   'carolina.mendez@correo-ejemplo.co', '3115551203', 'Carrera 9 # 60-18',     'Bogotá',   1, '2025-06-12'),
    ( 7, 'NATURAL',  'CC',  '1014223344', 'Ana Lucía Herrera Prieto',                'alherrera@correo-ejemplo.co',    '3125551204', 'Calle 45 Sur # 78-10',    'Bogotá',   1, '2026-08-25'),
    ( 8, 'NATURAL',  'CC',  '71654321',   'Ricardo Alzate Gómez',                    'ralzate@correo-ejemplo.co',      '3135551205', 'Circular 4 # 70-15',      'Medellín', 2, '2025-10-01'),
    ( 9, 'NATURAL',  'CC',  '39784512',   'Patricia Rincón Vélez',                   'princon@correo-ejemplo.co',      '3145551206', 'Calle 100 # 19-61',       'Bogotá',   1, '2024-11-05'),
    (10, 'NATURAL',  'CC',  '80456123',   'Luis Eduardo Pardo Silva',                'lepardo@correo-ejemplo.co',      '3155551207', 'Transversal 23 # 95-30',  'Bogotá',   1, '2026-02-18'),
    (11, 'NATURAL',  'CC',  '1023876541', 'Jhon Fredy Martínez Rojas',               'jfmartinez@correo-ejemplo.co',   '3165551208', 'Calle 3 # 68-22',         'Bogotá',   1, '2025-04-30'),
    (12, 'JURIDICA', 'NIT', '890901234',  'Textiles Antioquia SA',                   'juridica@textilesant-ejemplo.co','6042658800', 'Carrera 50 # 10 Sur-40',  'Medellín', 2, '2026-01-14'),
    (13, 'NATURAL',  'CC',  '1128456790', 'Yesenia Durango Cano',                    'ydurango@correo-ejemplo.co',     '3175551209', 'Calle 50 # 80-12',        'Medellín', 2, '2026-08-01'),
    (14, 'NATURAL',  'CC',  '79345612',   'Carlos Andrés Pinzón Ruiz',               'capinzon@correo-ejemplo.co',     '3185551210', 'Carrera 68 # 24-30',      'Bogotá',   1, '2024-06-20'),
    (15, 'NATURAL',  'CC',  '41678902',   'Martha Lucía Benítez Ramos',              'mlbenitez@correo-ejemplo.co',    '3195551211', 'Calle 72 # 20-45',        'Bogotá',   1, '2023-05-08'),
    (16, 'NATURAL',  'CC',  '41234567',   'Rosa Elena Garzón Díaz',                  'regarzon@correo-ejemplo.co',     '3205551212', 'Carrera 30 # 45-03',      'Bogotá',   1, '2025-05-15'),
    (17, 'NATURAL',  'CC',  '70123456',   'Gilberto Ospina Restrepo',                'gospina@correo-ejemplo.co',      '3215551213', 'Calle 10 # 43-28',        'Medellín', 2, '2026-07-22'),
    (18, 'NATURAL',  'CC',  '32567890',   'Martha Cecilia Vélez Arango',             'mcvelez@correo-ejemplo.co',      '3225551214', 'Carrera 80 # 33-15',      'Medellín', 2, '2024-08-12'),
    (19, 'NATURAL',  'CC',  '17345678',   'Hernando Beltrán Cruz',                   'hbeltran@correo-ejemplo.co',     '3235551215', 'Calle 26 # 32-11',        'Bogotá',   1, '2026-09-01'),
    (20, 'JURIDICA', 'NIT', '900778899',  'Distribuidora La Montaña SAS',            'admin@lamontana-ejemplo.co',     '6043112233', 'Carrera 65 # 8B-91',      'Medellín', 2, '2026-06-20'),
    (21, 'NATURAL',  'CC',  '1037890123', 'Esteban Montoya Henao',                   'emontoya@correo-ejemplo.co',     '3245551216', 'Calle 33 # 65-20',        'Medellín', 2, '2025-08-05'),
    (22, 'NATURAL',  'CC',  '1040567234', 'Wilmer Ríos Agudelo',                     'wrios@correo-ejemplo.co',        '3255551217', 'Carrera 45 # 98-12',      'Medellín', 2, '2025-10-20'),
    (23, 'NATURAL',  'CC',  '43890123',   'Gloria Inés Restrepo Lopera',             'girestrepo@correo-ejemplo.co',   '3265551218', 'Calle 30A # 77-50',       'Medellín', 2, '2024-03-11'),
    (24, 'NATURAL',  'CC',  '79567234',   'Óscar Iván Peña Jiménez',                 'oipena@correo-ejemplo.co',       '3275551219', 'Carrera 11 # 118-60',     'Bogotá',   1, '2025-07-28'),
    (25, 'NATURAL',  'CC',  '80987123',   'Nelson Cárdenas Barrera',                 'ncardenas@correo-ejemplo.co',    '3285551220', 'Calle 152 # 58-30',       'Bogotá',   1, '2026-08-18'),
    (26, 'NATURAL',  'CC',  '51234890',   'Edilma Castañeda Torres',                 'ecastaneda@correo-ejemplo.co',   '3295551221', 'Carrera 24 # 63-12',      'Bogotá',   1, '2024-10-30'),
    (27, 'JURIDICA', 'NIT', '900334112',  'Ingeniería y Vías del Sur SAS',           'contratos@ivsur-ejemplo.co',     '6017459900', 'Autopista Sur # 66-78',   'Bogotá',   1, '2025-09-15'),
    (28, 'NATURAL',  'CC',  '52890456',   'Luz Dary Moreno Quintero',                'ldmoreno@correo-ejemplo.co',     '3305551222', 'Calle 48 Sur # 5-20',     'Bogotá',   1, '2026-10-05'),
    (29, 'JURIDICA', 'NIT', '811045678',  'Junta de Acción Comunal Barrio Laureles', 'jac.laureles@correo-ejemplo.co', '6044121314', 'Circular 1 # 74-12',      'Medellín', 2, '2025-11-10');

INSERT INTO gabinete.peritos (id_perito, nombre, especialidad, profesion, registro, telefono, email, ciudad, tarifa_dictamen, calificacion) VALUES
    (1, 'Mauricio Lozano Prada',        'Avalúos inmobiliarios',           'Ingeniero catastral', 'RAA AVAL-1019',   '3016661001', 'mlozano@peritos-ejemplo.co',   'Bogotá',   3500000, 5),
    (2, 'Claudia Patricia Ruiz Salgado','Ingeniería civil y urbanismo',    'Ingeniera civil',     'MP 25202-11845',  '3016661002', 'cpruiz@peritos-ejemplo.co',    'Bogotá',   6000000, 4),
    (3, 'Adriana Lucía Gil Montes',     'Psicología forense',              'Psicóloga',           'TP 124578',       '3016661003', 'algil@peritos-ejemplo.co',     'Medellín', 2800000, 5),
    (4, 'Fernando Bustos Arias',        'Contabilidad forense',            'Contador público',    'TP 98741-T',      '3016661004', 'fbustos@peritos-ejemplo.co',   'Medellín', 4500000, 4),
    (5, 'Ricardo Esteban Mejía Soto',   'Cálculo actuarial pensional',     'Actuario',            'RA 0457',         '3016661005', 'remejia@peritos-ejemplo.co',   'Bogotá',   3200000, 5),
    (6, 'Gustavo Adolfo Pérez Ramírez', 'Medicina legal y pérdida de capacidad', 'Médico',        'RM 11045',        '3016661006', 'gaperez@peritos-ejemplo.co',   'Bogotá',   4000000, 4),
    (7, 'Teresa Villamizar Ortega',     'Grafología y documentología',     'Grafóloga',           'RG 0312',         '3016661007', 'tvillamizar@peritos-ejemplo.co','Medellín', 2500000, 3),
    (8, 'Andrés Camilo Ruiz Bermúdez',  'Reconstrucción de accidentes de tránsito', 'Ingeniero de tránsito', 'MP 25228-30412', '3016661008', 'acruiz@peritos-ejemplo.co', 'Bogotá', 5500000, 4);

-- Salario mínimo. 2026 según las fuentes de tarifas consultadas (VERIFICAR con el decreto oficial).
INSERT INTO gabinete.smmlv (anio, valor) VALUES
    (2025, 1423500),
    (2026, 1750905);

-- Tarifas: referencia Conalbos 2026 (orientativas) y tarifas propias de la firma.
INSERT INTO gabinete.tarifas (id_tarifa, fuente, codigo_rama, servicio, tipo_servicio, forma_cobro, valor_smmlv, valor_smmlv_max, porcentaje, nota, vigente_desde) VALUES
    ( 1, 'CONALBOS', NULL,             'Consulta verbal',                                 'CUALQUIERA',     'FIJO',               0.50, NULL, NULL, NULL, '2026-01-01'),
    ( 2, 'CONALBOS', NULL,             'Consulta escrita',                                'CUALQUIERA',     'FIJO',               1.00, NULL, NULL, NULL, '2026-01-01'),
    ( 3, 'CONALBOS', NULL,             'Concepto jurídico elaborado',                     'CUALQUIERA',     'FIJO',               2.00, NULL, NULL, 'Base; puede aumentar según la cuantía', '2026-01-01'),
    ( 4, 'CONALBOS', NULL,             'Proceso de mínima cuantía',                       'REPRESENTACION', 'PORCENTAJE_CUANTIA', NULL, NULL, 20.00, 'Sobre el valor del proceso', '2026-01-01'),
    ( 5, 'CONALBOS', NULL,             'Proceso de menor cuantía',                        'REPRESENTACION', 'PORCENTAJE_CUANTIA', NULL, NULL, 15.00, 'Sobre el valor del proceso', '2026-01-01'),
    ( 6, 'CONALBOS', NULL,             'Proceso de mayor cuantía',                        'REPRESENTACION', 'PORCENTAJE_CUANTIA', NULL, NULL, 10.00, 'Sobre el valor del proceso', '2026-01-01'),
    ( 7, 'CONALBOS', 'CIVIL',          'Restitución de inmueble arrendado',               'REPRESENTACION', 'MIXTO',              2.00, NULL, 15.00, '2 SMMLV + 15 % de los cánones de un año', '2026-01-01'),
    ( 8, 'CONALBOS', 'CIVIL',          'Sucesión ante juzgado',                           'REPRESENTACION', 'PORCENTAJE_CUANTIA', NULL, NULL, 15.00, 'Escalonado: 15 % sobre el primer tramo, luego decrece', '2026-01-01'),
    ( 9, 'CONALBOS', 'CIVIL',          'Sucesión ante notaría',                           'REPRESENTACION', 'PORCENTAJE_CUANTIA', NULL, NULL,  7.50, '50 % de la tarifa ante juzgado', '2026-01-01'),
    (10, 'CONALBOS', 'FAMILIA',        'Divorcio contencioso',                            'REPRESENTACION', 'FIJO',               5.00, NULL, NULL, NULL, '2026-01-01'),
    (11, 'CONALBOS', 'FAMILIA',        'Divorcio de mutuo acuerdo',                       'REPRESENTACION', 'FIJO',               3.00, NULL, NULL, NULL, '2026-01-01'),
    (12, 'CONALBOS', 'FAMILIA',        'Liquidación de sociedad conyugal',                'REPRESENTACION', 'MIXTO',              2.00, NULL,  5.00, '2 SMMLV + porcentaje según patrimonio (porcentaje propio de la firma)', '2026-01-01'),
    (13, 'CONALBOS', 'FAMILIA',        'Alimentos',                                       'REPRESENTACION', 'PORCENTAJE_CUANTIA', NULL, NULL, 20.00, '20 % de la cuota alimentaria de un año', '2026-01-01'),
    (14, 'CONALBOS', 'LABORAL',        'Representación del trabajador (ordinario)',       'REPRESENTACION', 'CUOTA_LITIS',        NULL, NULL, 30.00, 'Hasta 30 % de lo obtenido', '2026-01-01'),
    (15, 'CONALBOS', 'LABORAL',        'Defensa del empleador en primera instancia',      'REPRESENTACION', 'FIJO',               3.00, NULL, NULL, 'Desde 3 SMMLV', '2026-01-01'),
    (16, 'CONALBOS', 'PENAL',          'Asistencia a indagatoria',                        'PRESENCIAL',     'FIJO',               1.00, 3.00, NULL, 'Según la instancia', '2026-01-01'),
    (17, 'CONALBOS', 'PENAL',          'Defensa en primera instancia',                    'REPRESENTACION', 'FIJO',               5.00, 10.00, NULL, 'Según complejidad', '2026-01-01'),
    (18, 'CONALBOS', 'PENAL',          'Actuación ante la Corte Suprema (casación)',      'REPRESENTACION', 'FIJO',              30.00, NULL, NULL, NULL, '2026-01-01'),
    (19, 'FIRMA',    NULL,             'Consulta virtual',                                'VIRTUAL',        'FIJO',               0.40, NULL, NULL, '20 % menos que la presencial', '2026-01-01'),
    (20, 'FIRMA',    NULL,             'Consulta presencial',                             'PRESENCIAL',     'FIJO',               0.50, NULL, NULL, NULL, '2026-01-01'),
    (21, 'FIRMA',    NULL,             'Seguimiento mensual de proceso',                  'REPRESENTACION', 'MENSUAL',            0.50, NULL, NULL, 'Vigilancia de estados y reporte al cliente', '2026-01-01'),
    (22, 'FIRMA',    'PENSIONAL',      'Proceso pensional',                               'REPRESENTACION', 'MIXTO',              1.00, NULL, 25.00, '1 SMMLV inicial + 25 % del retroactivo', '2026-01-01'),
    (23, 'FIRMA',    'ADMINISTRATIVO', 'Medio de control contencioso administrativo',     'REPRESENTACION', 'MIXTO',              3.00, NULL, 15.00, '3 SMMLV + 15 % de lo obtenido', '2026-01-01'),
    (24, 'FIRMA',    'CONSTITUCIONAL', 'Acción de tutela',                                'CUALQUIERA',     'FIJO',               1.00, NULL, NULL, NULL, '2026-01-01'),
    (25, 'FIRMA',    'CONSTITUCIONAL', 'Acción popular',                                  'REPRESENTACION', 'FIJO',               4.00, NULL, NULL, NULL, '2026-01-01');
