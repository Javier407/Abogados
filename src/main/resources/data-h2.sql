-- Datos de ejemplo para el perfil h2 (BD en memoria). Se cargan al arrancar,
-- después de que Hibernate crea las tablas (spring.jpa.defer-datasource-initialization).
-- MERGE (insertar o actualizar por llave primaria) permite que varios contextos de prueba
-- compartan la misma BD en memoria sin chocar con registros ya cargados.
MERGE INTO clientes (id_cliente, nombre_cliente, direccion_cliente, telefono_cliente, email_cliente) VALUES
    (1020304050, 'Maria Rodriguez',  'Cra 7 # 45-10, Bogotá',       '3001234567', 'maria.rodriguez@correo.com'),
    (1015987654, 'Carlos Martinez',  'Cll 100 # 15-20, Bogotá',     '3109876543', 'carlos.martinez@correo.com'),
    (1032165498, 'Ana Gomez',        'Av 68 # 22-31, Bogotá',       '3155551234', 'ana.gomez@correo.com'),
    (900123456,  'Constructora Andina SAS', 'Cll 26 # 69-76, Bogotá', '6017654321', 'juridica@andina.com.co');

-- Cuentas del sistema. Dos pertenecen a abogados; la de Javier no está vinculada a ninguno.
MERGE INTO usuarios (cedula_usuario, nombre_usuario, email_usuario, usuario, password) VALUES
    (79555111,  'Luis Fernandez',   'lfernandez@gabinete.com', 'lfernandez', 'Luis2026x'),
    (52444222,  'Laura Torres',     'ltorres@gabinete.com',    'ltorres',    'Laura2026x'),
    (123456789, 'Javier Gutierrez', 'javier@correo.com',       'javierg',    'clave123');

-- cedula_usuario: cuenta del abogado (1 : 0..1). Andres Lopez no tiene cuenta.
MERGE INTO abogados (id_abogado, tarjeta_profesional, nombre_abogado, direccion_abogado, telefono_abogado, email_abogado, cedula_usuario) VALUES
    (79555111, '145236', 'Luis Fernandez',   'Cll 72 # 10-07, Bogotá', '3201112233', 'lfernandez@gabinete.com', 79555111),
    (52444222, '198745', 'Laura Torres',     'Cra 11 # 93-50, Bogotá', '3187778899', 'ltorres@gabinete.com',    52444222),
    (80333444, '210987', 'Andres Lopez',     'Cll 85 # 19-32, Bogotá', '3014445566', 'alopez@gabinete.com',     NULL);

MERGE INTO asuntos (numero_expediente, fecha_inicio, fecha_archivo, estado, id_cliente) VALUES
    ('EXP-2025-001', '2025-02-10', NULL,         'EN_TRAMITE', 1020304050),
    ('EXP-2025-002', '2025-04-22', '2026-01-15', 'ARCHIVADO',  1015987654),
    ('EXP-2026-001', '2026-03-03', NULL,         'EN_TRAMITE', 900123456),
    ('EXP-2026-002', '2026-05-18', NULL,         'SUSPENDIDO', 1020304050),
    ('EXP-2026-003', '2026-07-01', NULL,         'EN_TRAMITE', 1032165498);

MERGE INTO asunto_abogado (numero_expediente, id_abogado) VALUES
    ('EXP-2025-001', 79555111),
    ('EXP-2025-002', 52444222),
    ('EXP-2026-001', 79555111),
    ('EXP-2026-001', 52444222),
    ('EXP-2026-001', 80333444),
    ('EXP-2026-002', 80333444);
