-- 30 procesos ficticios de la firma, repartidos en las 7 ramas.
-- Los radicados tienen 23 dígitos y sus 12 primeros corresponden a un despacho del catálogo.
-- Las fechas de los autos recientes y de los términos son relativas a CURRENT_DATE, para que
-- siempre haya términos vencidos, por vencer y al día sin importar cuándo se cargue la base.
SET client_encoding = 'UTF8';

-- ============================ Procesos ============================
INSERT INTO procesos.procesos
    (id_proceso, radicado, id_tipo, id_despacho_actual, id_cliente, rol_cliente, asunto, cuantia, clase_cuantia,
     tipo_servicio, estado, etapa, fecha_radicacion, vigilado)
VALUES
    -- Civil y comercial
    ( 1, '11001400301520250012300',  3, NULL,  1, 'DEMANDANTE', 'Cobro de pagarés por suministro de mercancía impago',            85000000, 'MENOR',  'REPRESENTACION', 'ACTIVO',     'PRUEBAS',      '2025-03-14', true),
    ( 2, '11001310303220240045800',  1, NULL,  2, 'DEMANDADO',  'Responsabilidad civil contractual por presuntos defectos de obra', 620000000, 'MAYOR', 'REPRESENTACION', 'ACTIVO',     'AUDIENCIA',    '2024-07-22', true),
    ( 3, '11001400301520260021100',  7, NULL,  3, 'DEMANDANTE', 'Restitución de apartamento por mora en cánones de arrendamiento',  28800000, 'MINIMA', 'REPRESENTACION', 'ACTIVO',     'NOTIFICACION', '2026-05-04', true),
    ( 4, '05001400302020260008700',  4, NULL,  4, 'DEMANDANTE', 'Cobro de facturas de materiales sin título ejecutivo',            18500000, 'MINIMA', 'REPRESENTACION', 'ACTIVO',     'CONTESTACION', '2026-08-27', true),
    ( 5, '11001310303220230097700',  5, NULL,  5, 'DEMANDANTE', 'División de inmueble heredado entre hermanos (primera instancia)', 410000000, 'MAYOR', 'REPRESENTACION', 'TERMINADO',  'CERRADO',      '2023-11-07', false),
    ( 6, '11001310303220230097701',  5,   14,  5, 'DEMANDANTE', 'División de inmueble: apelación de la contraparte (segunda instancia)', 410000000, 'MAYOR', 'REPRESENTACION', 'ACTIVO', 'RECURSO',  '2025-10-21', true),
    -- Familia
    ( 7, '11001311001220250034100',  9, NULL,  6, 'DEMANDANTE', 'Divorcio contencioso por separación de cuerpos de más de dos años', NULL,    'SIN_CUANTIA', 'REPRESENTACION', 'ACTIVO',  'AUDIENCIA',    '2025-06-25', true),
    ( 8, '11001311001220260010200', 11, NULL,  7, 'DEMANDANTE', 'Fijación de cuota alimentaria para hijo menor de edad',           14400000, 'MINIMA', 'REPRESENTACION', 'ACTIVO',     'NOTIFICACION', '2026-08-27', true),
    ( 9, '05001311000520250061500', 12, NULL,  8, 'DEMANDADO',  'Custodia y cuidado personal de dos hijos menores',                 NULL,     'SIN_CUANTIA', 'REPRESENTACION', 'ACTIVO',  'PRUEBAS',      '2025-10-08', true),
    (10, '11001311001220240089000', 14, NULL,  9, 'DEMANDANTE', 'Liquidación de sociedad conyugal con inmuebles y vehículo',       350000000, 'MAYOR',  'REPRESENTACION', 'SUSPENDIDO', 'PRUEBAS',      '2024-11-20', false),
    (11, '11001311001220260015500', 10, NULL, 10, 'DEMANDANTE', 'Divorcio de mutuo acuerdo con acuerdo de alimentos',               NULL,     'SIN_CUANTIA', 'REPRESENTACION', 'TERMINADO', 'CERRADO',      '2026-02-25', false),
    -- Laboral
    (12, '11001310500820250051200', 16, NULL, 11, 'DEMANDANTE', 'Pago de horas extras, recargos e indemnización por despido sin justa causa', 96000000, 'MENOR', 'REPRESENTACION', 'ACTIVO', 'PRUEBAS', '2025-05-08', true),
    (13, '05001310502120250023300', 16, NULL, 12, 'DEMANDADO',  'Defensa del empleador: reclamación de contrato realidad',         140000000, 'MENOR',  'REPRESENTACION', 'ACTIVO',     'CONTESTACION', '2025-12-15', true),
    (14, '05001410500420260004100', 17, NULL, 13, 'DEMANDANTE', 'Pago de prestaciones sociales adeudadas',                          12000000, 'MINIMA', 'VIRTUAL',        'ACTIVO',     'AUDIENCIA',    '2026-08-04', true),
    (15, '11001310500820240019800', 18, NULL, 14, 'DEMANDANTE', 'Ejecución de condena laboral por salarios y prestaciones',         45000000, 'MINIMA', 'REPRESENTACION', 'ACTIVO',     'SENTENCIA',    '2024-07-02', true),
    (16, '11001310500820230040101', 16,   11, 15, 'DEMANDANTE', 'Reintegro por fuero de salud: apelación de la sentencia (segunda instancia)', 75000000, 'MENOR', 'REPRESENTACION', 'ACTIVO', 'RECURSO', '2025-12-16', true),
    -- Pensional
    (17, '11001310500820250077700', 20, NULL, 16, 'DEMANDANTE', 'Ineficacia del traslado de régimen pensional',                     180000000, 'MENOR',  'REPRESENTACION', 'ACTIVO',     'PRUEBAS',      '2025-05-27', true),
    (18, '05001310502120260011800', 21, NULL, 17, 'DEMANDANTE', 'Reconocimiento de pensión de sobrevivientes',                      95000000, 'MENOR',  'REPRESENTACION', 'ACTIVO',     'CONTESTACION', '2026-07-28', true),
    (19, '05001310502120240065000', 22, NULL, 18, 'DEMANDANTE', 'Reliquidación de pensión de vejez con todos los factores salariales', 60000000, 'MINIMA', 'REPRESENTACION', 'TERMINADO', 'CERRADO',   '2024-08-28', false),
    (20, '11001310500820260026500', 21, NULL, 19, 'DEMANDANTE', 'Reconocimiento de pensión de vejez negada por semanas',            120000000, 'MENOR',  'REPRESENTACION', 'ACTIVO',     'ADMISION',     '2026-09-08', true),
    -- Penal
    (21, '05001400400620260032000', 23, NULL, 20, 'VICTIMA',    'Denuncia por estafa con facturas falsas a la empresa',             NULL,     'SIN_CUANTIA', 'PRESENCIAL',   'ACTIVO',     'INVESTIGACION','2026-06-25', true),
    (22, '05001310902720250014400', 25, NULL, 21, 'PROCESADO',  'Defensa por hurto calificado y agravado',                          NULL,     'SIN_CUANTIA', 'REPRESENTACION', 'ACTIVO',  'JUICIO',       '2025-08-04', true),
    (23, '05001310902720250009800', 26, NULL, 22, 'PROCESADO',  'Defensa por lesiones personales culposas (preacuerdo)',            NULL,     'SIN_CUANTIA', 'REPRESENTACION', 'TERMINADO', 'CERRADO',     '2025-10-27', false),
    (24, '05001310902720240021100', 27, NULL, 23, 'VICTIMA',    'Reparación integral a la víctima tras condena por homicidio culposo', 80000000, 'MENOR', 'REPRESENTACION', 'ACTIVO',     'AUDIENCIA',    '2024-03-18', true),
    -- Contencioso administrativo
    (25, '11001333103520250040200', 28, NULL, 24, 'DEMANDANTE', 'Nulidad de la declaratoria de insubsistencia de un docente',      230000000, 'MENOR',  'REPRESENTACION', 'ACTIVO',     'PRUEBAS',      '2025-08-12', true),
    (26, '11001333103520260005600', 29, NULL, 25, 'DEMANDANTE', 'Reparación por accidente en vía nacional sin señalización',     1200000000, 'MAYOR',  'REPRESENTACION', 'ACTIVO',     'ADMISION',     '2026-09-22', true),
    (27, '25000234200020240118700', 28, NULL, 26, 'DEMANDANTE', 'Reliquidación de pensión docente con prima de servicios',         310000000, 'MAYOR',  'REPRESENTACION', 'ACTIVO',     'AUDIENCIA',    '2024-11-12', true),
    (28, '11001333103520250073300', 30, NULL, 27, 'DEMANDANTE', 'Desequilibrio económico de contrato de obra pública',           2400000000, 'MAYOR',  'REPRESENTACION', 'SUSPENDIDO', 'PRUEBAS',      '2025-09-30', false),
    -- Constitucional
    (29, '11001400301520260049800', 32, NULL, 28, 'ACCIONANTE', 'Tutela por negación de cirugía ordenada por el médico tratante', NULL,     'SIN_CUANTIA', 'VIRTUAL',        'ACTIVO',     'ADMISION',     '2026-10-09', true),
    (30, '05001400302020250091000', 33, NULL, 29, 'ACCIONANTE', 'Acción popular por ocupación del espacio público en el barrio',  NULL,     'SIN_CUANTIA', 'REPRESENTACION', 'ACTIVO',     'PRUEBAS',      '2025-11-20', true);

-- ============================ Partes ============================
-- La parte que representa la firma (el cliente)…
INSERT INTO procesos.partes (id_proceso, rol, tipo_documento, numero_documento, nombre, es_cliente)
SELECT p.id_proceso, p.rol_cliente, c.tipo_documento, c.numero_documento, c.nombre, true
FROM procesos.procesos p
JOIN gabinete.clientes c ON c.id_cliente = p.id_cliente;   -- solo en el seed se cruza con otro esquema

-- …y la contraparte.
INSERT INTO procesos.partes (id_proceso, rol, tipo_documento, numero_documento, nombre, apoderado) VALUES
    ( 1, 'DEMANDADO',  'NIT', '900111222',  'Comercializadora del Norte SAS',                       'Abg. Ricardo Cifuentes'),
    ( 2, 'DEMANDANTE', 'NIT', '900222333',  'Grupo Empresarial Sabana SAS',                         'Abg. Marcela Quiroga'),
    ( 3, 'DEMANDADO',  'CC',  '1015332211', 'Rodrigo Andrés Peña Valencia',                         NULL),
    ( 4, 'DEMANDADO',  'NIT', '901556677',  'Obras y Acabados JR SAS',                              NULL),
    ( 5, 'DEMANDADO',  'CC',  '19567890',   'Alberto Salazar Mora',                                 'Abg. Fabio Lemus'),
    ( 6, 'DEMANDADO',  'CC',  '19567890',   'Alberto Salazar Mora',                                 'Abg. Fabio Lemus'),
    ( 7, 'DEMANDADO',  'CC',  '80543219',   'Felipe Andrés Cuervo Lara',                            'Abg. Sandra Mejía'),
    ( 8, 'DEMANDADO',  'CC',  '1018456123', 'Javier Mauricio Torres León',                          NULL),
    ( 9, 'DEMANDANTE', 'CC',  '1036789012', 'Natalia Andrea Bedoya Ruiz',                           'Abg. Juan Pablo Arango'),
    (10, 'DEMANDADO',  'CC',  '79654123',   'Germán Darío Acosta Pineda',                           'Abg. Liliana Rozo'),
    (11, 'DEMANDANTE', 'CC',  '51987654',   'Marta Lucía Gaitán Pérez',                             NULL),
    (12, 'DEMANDADO',  'NIT', '900345678',  'Transportes Rápidos del Centro SAS',                   'Abg. Óscar Villamil'),
    (13, 'DEMANDANTE', 'CC',  '1037123456', 'Juan Camilo Arboleda Tobón',                           'Abg. Estefanía Ruiz'),
    (14, 'DEMANDADO',  'NIT', '901223344',  'Panadería La Espiga de Oro SAS',                       NULL),
    (15, 'DEMANDADO',  'NIT', '900567812',  'Servicios Integrales Pinar SAS',                       'Abg. Hugo Barrera'),
    (16, 'DEMANDADO',  'NIT', '900678123',  'Clínica Dental Sonrisa SAS',                           'Abg. Carolina Duque'),
    (17, 'DEMANDADO',  'NIT', '900000001',  'Administradora Colombiana de Pensiones (Colpensiones)', NULL),
    (17, 'DEMANDADO',  'NIT', '900000002',  'Fondo de Pensiones Privado Horizonte Futuro SA (ficticio)', 'Abg. Daniel Correa'),
    (18, 'DEMANDADO',  'NIT', '900000001',  'Administradora Colombiana de Pensiones (Colpensiones)', NULL),
    (19, 'DEMANDADO',  'NIT', '900000001',  'Administradora Colombiana de Pensiones (Colpensiones)', NULL),
    (20, 'DEMANDADO',  'NIT', '900000001',  'Administradora Colombiana de Pensiones (Colpensiones)', NULL),
    (21, 'PROCESADO',  NULL,  NULL,         'Indiciado por identificar',                            NULL),
    (22, 'VICTIMA',    'NIT', '901778899',  'Almacén Moda Total SAS',                               NULL),
    (23, 'VICTIMA',    'CC',  '1036998877', 'Daniela Ramírez Cano',                                 NULL),
    (24, 'PROCESADO',  'CC',  '98765432',   'Mauricio Ochoa Tamayo',                                'Defensor público'),
    (25, 'DEMANDADO',  'NIT', '899999061',  'Bogotá D.C. — Secretaría de Educación Distrital',     NULL),
    (26, 'DEMANDADO',  'NIT', '800215807',  'Instituto Nacional de Vías (INVÍAS)',                 NULL),
    (27, 'DEMANDADO',  NULL,  NULL,         'Nación — Ministerio de Educación — FOMAG',             NULL),
    (28, 'DEMANDADO',  'NIT', '800094755',  'Municipio de Soacha',                                  NULL),
    (29, 'ACCIONADO',  'NIT', '900000003',  'EPS Salud Vital SA (ficticia)',                        NULL),
    (30, 'ACCIONADO',  'NIT', '890905211',  'Municipio de Medellín — Secretaría de Infraestructura', NULL);

-- ============================ Asignaciones ============================
INSERT INTO procesos.asignaciones (id_proceso, id_persona, rol, desde)
SELECT a.id_proceso, a.id_persona, a.rol, p.fecha_radicacion - 10
FROM (VALUES
    ( 1, 1019087654, 'RESPONSABLE'), ( 1, 79123456, 'APOYO'),
    ( 2, 79123456,   'RESPONSABLE'), ( 2, 1019087654, 'APOYO'), ( 2, 1001234567, 'DEPENDIENTE'),
    ( 3, 1019087654, 'RESPONSABLE'), ( 3, 1001234567, 'DEPENDIENTE'),
    ( 4, 52345678,   'RESPONSABLE'), ( 4, 1152443210, 'APOYO'), ( 4, 1036654321, 'DEPENDIENTE'),
    ( 5, 79123456,   'RESPONSABLE'),
    ( 6, 79123456,   'RESPONSABLE'), ( 6, 1019087654, 'APOYO'),
    ( 7, 80234567,   'RESPONSABLE'),
    ( 8, 80234567,   'RESPONSABLE'), ( 8, 1023987456, 'APOYO'),
    ( 9, 52345678,   'RESPONSABLE'), ( 9, 1036654321, 'DEPENDIENTE'),
    (10, 80234567,   'RESPONSABLE'), (10, 1018765432, 'APOYO'),
    (11, 80234567,   'RESPONSABLE'),
    (12, 52345678,   'RESPONSABLE'), (12, 1001234567, 'DEPENDIENTE'),
    (13, 1152443210, 'RESPONSABLE'), (13, 52345678, 'APOYO'),
    (14, 1152443210, 'RESPONSABLE'),
    (15, 52345678,   'RESPONSABLE'), (15, 1001234567, 'DEPENDIENTE'),
    (16, 52345678,   'RESPONSABLE'), (16, 1152443210, 'APOYO'),
    (17, 43567890,   'RESPONSABLE'), (17, 1001234567, 'DEPENDIENTE'),
    (18, 43567890,   'RESPONSABLE'), (18, 1152443210, 'APOYO'),
    (19, 43567890,   'RESPONSABLE'),
    (20, 43567890,   'RESPONSABLE'), (20, 1001234567, 'DEPENDIENTE'),
    (21, 1037612345, 'RESPONSABLE'),
    (22, 1037612345, 'RESPONSABLE'), (22, 1036654321, 'DEPENDIENTE'),
    (23, 1037612345, 'RESPONSABLE'),
    (24, 1037612345, 'RESPONSABLE'), (24, 1036654321, 'DEPENDIENTE'),
    (25, 1020456789, 'RESPONSABLE'),
    (26, 1020456789, 'RESPONSABLE'), (26, 1018765432, 'APOYO'),
    (27, 1020456789, 'RESPONSABLE'), (27, 79123456, 'APOYO'),
    (28, 79123456,   'RESPONSABLE'), (28, 1020456789, 'APOYO'),
    (29, 1023987456, 'RESPONSABLE'),
    (30, 1023987456, 'RESPONSABLE'), (30, 1036654321, 'DEPENDIENTE')
) AS a(id_proceso, id_persona, rol)
JOIN procesos.procesos p ON p.id_proceso = a.id_proceso;

-- ============================ Actuaciones ============================
-- Poder y actuación inicial de todos los procesos, según el rol del cliente.
INSERT INTO procesos.actuaciones (id_proceso, fecha, tipo, descripcion, id_persona)
SELECT p.id_proceso, p.fecha_radicacion - 10, 'PODER', 'Cliente otorga poder especial', s.id_persona
FROM procesos.procesos p
JOIN procesos.asignaciones s ON s.id_proceso = p.id_proceso AND s.rol = 'RESPONSABLE';

INSERT INTO procesos.actuaciones (id_proceso, fecha, tipo, descripcion, id_persona)
SELECT p.id_proceso, p.fecha_radicacion,
       CASE WHEN p.rol_cliente IN ('DEMANDADO', 'PROCESADO') THEN 'NOTIFICACION' ELSE 'DEMANDA' END,
       CASE p.rol_cliente
            WHEN 'VICTIMA'    THEN 'Radicación de la denuncia ante la Fiscalía'
            WHEN 'ACCIONANTE' THEN 'Radicación de la acción constitucional'
            WHEN 'DEMANDADO'  THEN 'El cliente es notificado de la demanda en su contra'
            WHEN 'PROCESADO'  THEN 'El cliente es vinculado formalmente al proceso penal'
            ELSE 'Radicación de la demanda' END,
       s.id_persona
FROM procesos.procesos p
JOIN procesos.asignaciones s ON s.id_proceso = p.id_proceso AND s.rol = 'RESPONSABLE';

-- Actuaciones puntuales (contestaciones, recursos y audiencias programadas a futuro).
INSERT INTO procesos.actuaciones (id_proceso, fecha, tipo, descripcion, id_persona) VALUES
    ( 1, '2025-06-24',      'MEMORIAL',     'Pronunciamiento sobre las excepciones de mérito',            1019087654),
    ( 2, '2024-10-30',      'CONTESTACION', 'Contestación de la demanda con excepciones y llamamiento',  79123456),
    ( 2, CURRENT_DATE + 12, 'AUDIENCIA',    'Audiencia de instrucción y juzgamiento',                     79123456),
    ( 6, '2025-11-12',      'ALEGATOS',     'Alegatos como no recurrente en segunda instancia',           79123456),
    ( 7, CURRENT_DATE + 9,  'AUDIENCIA',    'Audiencia inicial (art. 372 CGP)',                           80234567),
    ( 9, '2025-11-18',      'CONTESTACION', 'Contestación de la demanda de custodia',                     52345678),
    ( 9, CURRENT_DATE + 4,  'AUDIENCIA',    'Audiencia de pruebas: valoración psicológica',               52345678),
    (12, '2025-09-15',      'MEMORIAL',     'Solicitud de pruebas adicionales',                           52345678),
    (14, CURRENT_DATE + 6,  'AUDIENCIA',    'Audiencia única de trámite y juzgamiento',                   1152443210),
    (16, '2025-12-12',      'RECURSO',      'Recurso de apelación contra la sentencia de primera instancia', 52345678),
    (22, CURRENT_DATE + 7,  'AUDIENCIA',    'Audiencia de juicio oral',                                   1037612345),
    (23, '2025-11-24',      'AUDIENCIA',    'Audiencia de verificación de preacuerdo',                    1037612345),
    (24, CURRENT_DATE + 15, 'AUDIENCIA',    'Audiencia de pruebas del incidente de reparación',           1037612345),
    (27, CURRENT_DATE + 20, 'AUDIENCIA',    'Audiencia de pruebas (art. 181 CPACA)',                      1020456789),
    (28, '2026-06-20',      'MEMORIAL',     'Solicitud de reanudación del proceso',                       79123456);

-- ============================ Providencias ============================
INSERT INTO procesos.providencias (id_proceso, tipo, clase, fecha_providencia, fecha_estado, decision, sentido) VALUES
    ( 1, 'AUTO', 'Libra mandamiento de pago',                         '2025-03-28', '2025-03-31', 'Ordena al deudor pagar el capital e intereses de los pagarés', NULL),
    ( 1, 'AUTO', 'Corre traslado de excepciones',                     '2025-06-10', '2025-06-11', 'Traslado de las excepciones de mérito propuestas', NULL),
    ( 1, 'AUTO', 'Decreta pruebas',                                   CURRENT_DATE - 30, CURRENT_DATE - 29, 'Decreta pruebas documentales e interrogatorio de parte', NULL),
    ( 2, 'AUTO', 'Admite demanda',                                    '2024-08-02', '2024-08-05', 'Admite la demanda verbal de mayor cuantía', NULL),
    ( 2, 'AUTO', 'Tiene por contestada la demanda',                   '2024-11-14', '2024-11-15', 'Tiene por contestada y admite el llamamiento en garantía', NULL),
    ( 2, 'AUTO', 'Decreta pruebas y fija audiencia de instrucción',   CURRENT_DATE - 24, CURRENT_DATE - 23, 'Decreta dictamen pericial de ingeniería y fija audiencia', NULL),
    ( 2, 'AUTO', 'Corre traslado para alegatos',                      CURRENT_DATE - 21, CURRENT_DATE - 20, 'Corre traslado a las partes para alegar por escrito', NULL),
    ( 3, 'AUTO', 'Admite demanda de restitución',                     '2026-05-12', '2026-05-13', 'Admite la demanda de restitución de inmueble arrendado', NULL),
    ( 3, 'AUTO', 'Ordena notificar al demandado',                     CURRENT_DATE - 13, CURRENT_DATE - 12, 'Ordena notificación personal en la dirección del inmueble', NULL),
    ( 4, 'AUTO', 'Inadmite demanda',                                  CURRENT_DATE - 20, CURRENT_DATE - 19, 'Pide aportar las facturas en original', NULL),
    ( 4, 'AUTO', 'Admite demanda y requiere al deudor',               CURRENT_DATE - 7,  CURRENT_DATE - 6,  'Requiere al deudor para que pague o justifique su negativa', NULL),
    ( 5, 'AUTO', 'Admite demanda',                                    '2023-11-20', '2023-11-21', 'Admite la demanda divisoria', NULL),
    ( 5, 'AUTO', 'Decreta división ad valorem y avalúo',              '2024-06-04', '2024-06-05', 'Ordena la venta del inmueble y su avalúo pericial', NULL),
    ( 5, 'SENTENCIA', 'Sentencia de primera instancia',               '2025-09-18', '2025-09-19', 'Decreta la venta en pública subasta y la distribución del producto', 'FAVORABLE'),
    ( 6, 'AUTO', 'Admite recurso de apelación',                       '2025-11-03', '2025-11-04', 'El Tribunal admite la apelación de la contraparte', NULL),
    ( 6, 'AUTO', 'Fija fecha para fallo de segunda instancia',        CURRENT_DATE - 9,  CURRENT_DATE - 8,  'Anuncia que la decisión se proferirá por escrito', NULL),
    ( 7, 'AUTO', 'Admite demanda',                                    '2025-07-08', '2025-07-09', 'Admite la demanda de divorcio contencioso', NULL),
    ( 7, 'AUTO', 'Requiere aportar pruebas documentales',             CURRENT_DATE - 26, CURRENT_DATE - 25, 'Requiere aportar registros civiles y certificados', NULL),
    ( 7, 'AUTO', 'Fija fecha de audiencia inicial',                   CURRENT_DATE - 6,  CURRENT_DATE - 5,  'Fija fecha para la audiencia inicial', NULL),
    ( 8, 'AUTO', 'Inadmite demanda',                                  CURRENT_DATE - 48, CURRENT_DATE - 47, 'Pide aportar el registro civil del menor', NULL),
    ( 8, 'AUTO', 'Admite demanda y fija alimentos provisionales',     CURRENT_DATE - 42, CURRENT_DATE - 41, 'Fija alimentos provisionales y ordena notificar al demandado', NULL),
    ( 9, 'AUTO', 'Admite demanda',                                    '2025-10-20', '2025-10-21', 'Admite la demanda de custodia', NULL),
    ( 9, 'AUTO', 'Decreta valoración psicológica',                    '2026-03-17', '2026-03-18', 'Ordena valoración por psicólogo forense', NULL),
    ( 9, 'AUTO', 'Fija audiencia de pruebas',                         CURRENT_DATE - 15, CURRENT_DATE - 14, 'Fija fecha para practicar pruebas', NULL),
    (10, 'AUTO', 'Admite demanda',                                    '2024-12-02', '2024-12-03', 'Admite la liquidación de la sociedad conyugal', NULL),
    (10, 'AUTO', 'Decreta inventario y avalúo',                       '2025-04-21', '2025-04-22', 'Ordena presentar inventario y avalúo de bienes', NULL),
    (10, 'AUTO', 'Suspende el proceso por solicitud conjunta',        '2026-08-28', '2026-08-31', 'Suspende el proceso mientras las partes negocian un acuerdo', NULL),
    (11, 'AUTO', 'Admite solicitud',                                  '2026-03-05', '2026-03-06', 'Admite la solicitud de divorcio de mutuo acuerdo', NULL),
    (11, 'SENTENCIA', 'Decreta el divorcio',                          '2026-04-22', '2026-04-23', 'Decreta el divorcio y aprueba el acuerdo de alimentos', 'FAVORABLE'),
    (12, 'AUTO', 'Admite demanda',                                    '2025-05-19', '2025-05-20', 'Admite la demanda ordinaria laboral', NULL),
    (12, 'AUTO', 'Tiene por contestada y fija audiencia art. 77',     '2025-09-02', '2025-09-03', 'Fija audiencia obligatoria de conciliación', NULL),
    (12, 'AUTO', 'Niega prueba testimonial',                          CURRENT_DATE - 3,  CURRENT_DATE - 2,  'Niega el testimonio solicitado por impertinente', NULL),
    (13, 'AUTO', 'Admite demanda',                                    '2026-01-20', '2026-01-21', 'Admite la demanda contra el cliente', NULL),
    (13, 'AUTO', 'Tiene por notificado al demandado',                 CURRENT_DATE - 9,  CURRENT_DATE - 8,  'Tiene por notificada a la empresa demandada', NULL),
    (14, 'AUTO', 'Requiere subsanar la demanda',                      CURRENT_DATE - 30, CURRENT_DATE - 29, 'Pide precisar los valores reclamados', NULL),
    (14, 'AUTO', 'Admite demanda y fija audiencia única',             CURRENT_DATE - 18, CURRENT_DATE - 17, 'Admite y fija audiencia de única instancia', NULL),
    (15, 'AUTO', 'Libra mandamiento de pago',                         '2024-07-15', '2024-07-16', 'Libra mandamiento de pago por la condena laboral', NULL),
    (15, 'AUTO', 'Ordena seguir adelante la ejecución',               '2025-02-10', '2025-02-11', 'Ordena seguir adelante con la ejecución', NULL),
    (15, 'AUTO', 'Aprueba liquidación del crédito',                   '2025-08-25', '2025-08-26', 'Aprueba la liquidación del crédito y las costas', NULL),
    (16, 'AUTO', 'Concede recurso de apelación',                      '2025-12-12', '2025-12-15', 'Concede la apelación en efecto suspensivo', NULL),
    (16, 'AUTO', 'Admite recurso y corre traslado para alegar',       CURRENT_DATE - 2,  CURRENT_DATE - 1,  'El Tribunal admite el recurso y corre traslado', NULL),
    (17, 'AUTO', 'Admite demanda',                                    '2025-06-03', '2025-06-04', 'Admite la demanda contra Colpensiones y el fondo privado', NULL),
    (17, 'AUTO', 'Tiene por contestada la demanda',                   '2025-10-14', '2025-10-15', 'Tiene por contestada por ambas demandadas', NULL),
    (17, 'AUTO', 'Decreta pruebas (cálculo actuarial)',               CURRENT_DATE - 35, CURRENT_DATE - 34, 'Ordena aportar el cálculo actuarial', NULL),
    (18, 'AUTO', 'Admite demanda',                                    '2026-08-10', '2026-08-11', 'Admite la demanda de pensión de sobrevivientes', NULL),
    (18, 'AUTO', 'Corre traslado de la contestación',                 CURRENT_DATE - 4,  CURRENT_DATE - 3,  'Corre traslado de la contestación con excepciones', NULL),
    (19, 'AUTO', 'Admite demanda',                                    '2024-09-09', '2024-09-10', 'Admite la demanda de reliquidación', NULL),
    (19, 'SENTENCIA', 'Ordena reliquidar la mesada',                  '2025-10-30', '2025-10-31', 'Condena a reliquidar la pensión y pagar el retroactivo', 'FAVORABLE'),
    (20, 'AUTO', 'Inadmite demanda',                                  CURRENT_DATE - 30, CURRENT_DATE - 29, 'Pide aportar la historia laboral actualizada', NULL),
    (20, 'AUTO', 'Admite demanda',                                    CURRENT_DATE - 20, CURRENT_DATE - 19, 'Admite la demanda y ordena notificar a Colpensiones', NULL),
    (21, 'AUTO', 'Asigna fiscal y ordena programa metodológico',      '2026-07-01', '2026-07-01', 'La denuncia se asigna a la Fiscalía Seccional', NULL),
    (21, 'AUTO', 'Ordena inspección y peritaje contable',             CURRENT_DATE - 16, CURRENT_DATE - 15, 'Ordena peritaje contable a las facturas', NULL),
    (22, 'AUTO', 'Aprueba escrito de acusación',                      '2025-08-19', '2025-08-20', 'Se radica y verbaliza el escrito de acusación', NULL),
    (22, 'AUTO', 'Fija audiencia de juicio oral',                     CURRENT_DATE - 3,  CURRENT_DATE - 2,  'Fija la fecha de inicio del juicio oral', NULL),
    (23, 'AUTO', 'Aprueba preacuerdo',                                '2025-11-24', '2025-11-25', 'Aprueba el preacuerdo entre la Fiscalía y la defensa', NULL),
    (23, 'SENTENCIA', 'Sentencia condenatoria por preacuerdo',        '2026-01-28', '2026-01-29', 'Condena con la rebaja de pena pactada', 'PARCIAL'),
    (24, 'SENTENCIA', 'Sentencia condenatoria',                       '2025-05-14', '2025-05-15', 'Condena al procesado por homicidio culposo', 'FAVORABLE'),
    (24, 'AUTO', 'Abre incidente de reparación integral',             '2025-07-22', '2025-07-23', 'Abre el incidente a solicitud de la víctima', NULL),
    (24, 'AUTO', 'Fija audiencia de pruebas del incidente',           CURRENT_DATE - 11, CURRENT_DATE - 10, 'Fija fecha para pruebas del incidente', NULL),
    (25, 'AUTO', 'Admite demanda',                                    '2025-08-25', '2025-08-26', 'Admite el medio de control de nulidad y restablecimiento', NULL),
    (25, 'AUTO', 'Fija audiencia inicial (art. 180 CPACA)',           '2026-02-16', '2026-02-17', 'Fija la audiencia inicial', NULL),
    (25, 'AUTO', 'Corre traslado para alegatos de conclusión',        CURRENT_DATE - 6,  CURRENT_DATE - 5,  'Corre traslado para alegar de conclusión', NULL),
    (26, 'AUTO', 'Reconoce personería a la apoderada',                CURRENT_DATE - 10, CURRENT_DATE - 9,  'Reconoce personería a la abogada de la firma', NULL),
    (26, 'AUTO', 'Inadmite demanda y concede término para subsanar',  CURRENT_DATE - 10, CURRENT_DATE - 9,  'Inadmite por falta de agotamiento de conciliación', NULL),
    (27, 'AUTO', 'Admite demanda',                                    '2024-11-25', '2024-11-26', 'Admite la demanda ante el Tribunal', NULL),
    (27, 'AUTO', 'Corre traslado de excepciones',                     CURRENT_DATE - 32, CURRENT_DATE - 31, 'Corre traslado de las excepciones previas', NULL),
    (27, 'AUTO', 'Fija audiencia de pruebas',                         CURRENT_DATE - 12, CURRENT_DATE - 11, 'Fija fecha para la audiencia de pruebas', NULL),
    (28, 'AUTO', 'Admite demanda',                                    '2025-10-06', '2025-10-07', 'Admite la demanda de controversias contractuales', NULL),
    (28, 'AUTO', 'Suspende el proceso por prejudicialidad',           '2026-06-09', '2026-06-10', 'Suspende hasta que se resuelva un proceso penal relacionado', NULL),
    (29, 'AUTO', 'Admite tutela y ordena informe a la EPS',           CURRENT_DATE - 4,  CURRENT_DATE - 4,  'Admite la tutela y pide informe en 48 horas', NULL),
    (29, 'AUTO', 'Vincula al Ministerio de Salud',                    CURRENT_DATE - 2,  CURRENT_DATE - 2,  'Vincula al Ministerio de Salud como tercero', NULL),
    (30, 'AUTO', 'Admite acción popular',                             '2025-12-01', '2025-12-02', 'Admite la acción popular y ordena publicar aviso', NULL),
    (30, 'AUTO', 'Decreta dictamen pericial urbanístico',             CURRENT_DATE - 11, CURRENT_DATE - 10, 'Decreta dictamen pericial sobre el espacio público', NULL);

-- ============================ Pruebas ============================
INSERT INTO procesos.pruebas (id_proceso, tipo, descripcion, id_perito, estado) VALUES
    ( 1, 'DOCUMENTAL',  'Pagarés, facturas y remisiones de mercancía',          NULL, 'PRACTICADA'),
    ( 2, 'PERICIAL',    'Dictamen de ingeniería sobre los presuntos defectos de obra', 2, 'PRACTICADA'),
    ( 2, 'TESTIMONIAL', 'Testimonio del interventor de la obra',                 NULL, 'DECRETADA'),
    ( 5, 'PERICIAL',    'Avalúo comercial del inmueble a dividir',               1,    'PRACTICADA'),
    ( 7, 'DOCUMENTAL',  'Registro civil de matrimonio y de nacimiento',          NULL, 'PRACTICADA'),
    ( 7, 'TESTIMONIAL', 'Declaración de dos testigos sobre la separación',       NULL, 'DECRETADA'),
    ( 9, 'PERICIAL',    'Valoración psicológica de los menores y los padres',    3,    'DECRETADA'),
    (10, 'PERICIAL',    'Avalúo de los bienes de la sociedad conyugal',          1,    'DECRETADA'),
    (12, 'DOCUMENTAL',  'Contrato de trabajo, desprendibles de nómina y planillas', NULL, 'PRACTICADA'),
    (12, 'TESTIMONIAL', 'Testimonio de un compañero de trabajo',                 NULL, 'NEGADA'),
    (17, 'PERICIAL',    'Cálculo actuarial de la pensión en ambos regímenes',    5,    'DECRETADA'),
    (21, 'DOCUMENTAL',  'Facturas presuntamente falsas y extractos bancarios',   NULL, 'PRACTICADA'),
    (21, 'PERICIAL',    'Peritaje contable de las facturas',                     4,    'DECRETADA'),
    (21, 'PERICIAL',    'Análisis grafológico de las firmas',                    7,    'SOLICITADA'),
    (22, 'VIDEO',       'Video de las cámaras de seguridad del almacén',         NULL, 'DECRETADA'),
    (22, 'TESTIMONIAL', 'Testimonio del agente de policía que hizo la captura',  NULL, 'DECRETADA'),
    (26, 'PERICIAL',    'Dictamen de medicina legal sobre las lesiones',         6,    'SOLICITADA'),
    (26, 'PERICIAL',    'Reconstrucción del accidente de tránsito',              8,    'SOLICITADA'),
    (26, 'ANEXO',       'Registro fotográfico de la vía y del vehículo',         NULL, 'SOLICITADA'),
    (28, 'PERICIAL',    'Dictamen financiero del desequilibrio del contrato',    4,    'DECRETADA'),
    (29, 'DOCUMENTAL',  'Historia clínica y orden médica de la cirugía',         NULL, 'PRACTICADA'),
    (30, 'PERICIAL',    'Dictamen urbanístico sobre la ocupación del espacio público', 2, 'DECRETADA'),
    (30, 'DOCUMENTAL',  'Registro fotográfico y quejas de los vecinos',          NULL, 'PRACTICADA');

-- ============================ Términos ============================
-- El vencimiento se calcula en días hábiles con procesos.sumar_dias_habiles (descuenta fines de semana,
-- festivos y vacancia judicial). El responsable es el abogado responsable del proceso.
INSERT INTO procesos.terminos (id_proceso, id_providencia, descripcion, fecha_inicio, dias_habiles, fecha_vencimiento, estado, id_responsable)
SELECT pr.id_proceso, pr.id_providencia, t.descripcion, pr.fecha_estado, t.dias,
       procesos.sumar_dias_habiles(pr.fecha_estado, t.dias), t.estado, s.id_persona
FROM (VALUES
    ( 2, 'Corre traslado para alegatos',                      'Presentar alegatos de conclusión',             5, 'PENDIENTE'),
    ( 2, 'Decreta pruebas y fija audiencia de instrucción',   'Aportar el dictamen pericial de ingeniería',  10, 'CUMPLIDO'),
    ( 3, 'Ordena notificar al demandado',                     'Notificar personalmente al demandado',        30, 'PENDIENTE'),
    ( 4, 'Admite demanda y requiere al deudor',               'Plazo del deudor para pagar o justificar',    10, 'PENDIENTE'),
    ( 7, 'Requiere aportar pruebas documentales',             'Aportar las pruebas documentales',            10, 'CUMPLIDO'),
    ( 8, 'Admite demanda y fija alimentos provisionales',     'Notificar al demandado',                      30, 'PENDIENTE'),
    (12, 'Niega prueba testimonial',                          'Interponer recurso de reposición',             3, 'PENDIENTE'),
    (13, 'Tiene por notificado al demandado',                 'Contestar la demanda',                        10, 'PENDIENTE'),
    (16, 'Admite recurso y corre traslado para alegar',       'Sustentar el recurso de apelación por escrito', 5, 'PENDIENTE'),
    (17, 'Decreta pruebas (cálculo actuarial)',               'Aportar el cálculo actuarial',                20, 'PENDIENTE'),
    (18, 'Corre traslado de la contestación',                 'Pronunciarse sobre la contestación',           5, 'PENDIENTE'),
    (21, 'Ordena inspección y peritaje contable',             'Entregar los soportes contables al perito',   10, 'PENDIENTE'),
    (25, 'Corre traslado para alegatos de conclusión',        'Presentar alegatos de conclusión',            10, 'PENDIENTE'),
    (26, 'Inadmite demanda y concede término para subsanar',  'Subsanar la demanda',                         10, 'PENDIENTE'),
    (27, 'Corre traslado de excepciones',                     'Pronunciarse sobre las excepciones',           3, 'CUMPLIDO'),
    (29, 'Admite tutela y ordena informe a la EPS',           'Plazo del juez para fallar la tutela',        10, 'PENDIENTE'),
    (30, 'Decreta dictamen pericial urbanístico',             'Aportar el dictamen pericial',                15, 'PENDIENTE')
) AS t(id_proceso, clase, descripcion, dias, estado)
JOIN procesos.providencias pr ON pr.id_proceso = t.id_proceso AND pr.clase = t.clase
JOIN procesos.asignaciones s  ON s.id_proceso = t.id_proceso AND s.rol = 'RESPONSABLE';
