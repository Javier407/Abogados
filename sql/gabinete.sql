-- =====================================================================
--  Lab #1 - Segundo corte: Gabinete de abogados (Arquitectura 3 capas)
--  Proyecto Java Web con Spring Boot (ciclo3back)
-- ---------------------------------------------------------------------
--  Ejecutar este script en MySQL Workbench ANTES de correr la app con el
--  perfil mysql. Hibernate (ddl-auto=update) también crea las tablas, pero
--  aquí queda el diseño relacional completo con sus restricciones y datos
--  de ejemplo.
--
--  Modelo:
--    clientes 1 ──< asuntos >──< asunto_abogado >── 1 abogados 0..1 ── 1 usuarios
--    (un cliente tiene N asuntos; un asunto tiene 0..N abogados y un
--     abogado lleva 0..N asuntos; un abogado puede tener una cuenta de usuario)
--
--  El script se puede ejecutar varias veces: sobre una BD ya creada solo
--  agrega lo que falte (columna, índice y llave foránea de cedula_usuario).
-- =====================================================================

-- 1) Base de datos
CREATE DATABASE IF NOT EXISTS gabinete
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE gabinete;

-- 2) Usuario de conexión que usa la aplicación (application-mysql.properties)
CREATE USER IF NOT EXISTS 'admin'@'localhost' IDENTIFIED BY 'admin123';
GRANT ALL PRIVILEGES ON gabinete.* TO 'admin'@'localhost';
FLUSH PRIVILEGES;

-- 3) Tablas
CREATE TABLE IF NOT EXISTS clientes (
    id_cliente        BIGINT       NOT NULL,
    nombre_cliente    VARCHAR(100) NOT NULL,
    direccion_cliente VARCHAR(150) NOT NULL,
    telefono_cliente  VARCHAR(15)  NOT NULL,
    email_cliente     VARCHAR(120) NOT NULL,
    PRIMARY KEY (id_cliente)
);

-- Cuentas del sistema (entidad co.edu.unbosque.ciclo3back.model.Usuarios)
CREATE TABLE IF NOT EXISTS usuarios (
    cedula_usuario BIGINT       NOT NULL,
    email_usuario  VARCHAR(255),
    nombre_usuario VARCHAR(100),
    password       VARCHAR(100),
    usuario        VARCHAR(30),
    PRIMARY KEY (cedula_usuario)
);

CREATE TABLE IF NOT EXISTS abogados (
    id_abogado          BIGINT       NOT NULL,
    tarjeta_profesional VARCHAR(10)  NOT NULL,
    nombre_abogado      VARCHAR(100) NOT NULL,
    direccion_abogado   VARCHAR(150) NOT NULL,
    telefono_abogado    VARCHAR(15)  NOT NULL,
    email_abogado       VARCHAR(120) NOT NULL,
    PRIMARY KEY (id_abogado),
    CONSTRAINT uk_abogado_tarjeta UNIQUE (tarjeta_profesional)
);

-- Relación abogados 0..1 ── 1 usuarios: columna cedula_usuario (opcional y única) con FK.
-- Cada paso revisa information_schema, así no falla si la columna ya existe
-- (por ejemplo, si Hibernate la creó al arrancar la aplicación).
SET @hay := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'abogados' AND COLUMN_NAME = 'cedula_usuario');
SET @sql := IF(@hay = 0, 'ALTER TABLE abogados ADD COLUMN cedula_usuario BIGINT NULL', 'DO 0');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @hay := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'abogados' AND COLUMN_NAME = 'cedula_usuario' AND NON_UNIQUE = 0);
SET @sql := IF(@hay = 0, 'ALTER TABLE abogados ADD CONSTRAINT uk_abogado_usuario UNIQUE (cedula_usuario)', 'DO 0');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @hay := (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'abogados' AND COLUMN_NAME = 'cedula_usuario'
               AND REFERENCED_TABLE_NAME = 'usuarios');
SET @sql := IF(@hay = 0, 'ALTER TABLE abogados ADD CONSTRAINT fk_abogado_usuario FOREIGN KEY (cedula_usuario) REFERENCES usuarios (cedula_usuario)', 'DO 0');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

CREATE TABLE IF NOT EXISTS asuntos (
    numero_expediente VARCHAR(20) NOT NULL,
    fecha_inicio      DATE        NOT NULL,
    fecha_archivo     DATE        NULL,
    estado            VARCHAR(20) NOT NULL,
    id_cliente        BIGINT      NOT NULL,
    PRIMARY KEY (numero_expediente),
    CONSTRAINT fk_asunto_cliente FOREIGN KEY (id_cliente) REFERENCES clientes (id_cliente),
    CONSTRAINT ck_asunto_estado  CHECK (estado IN ('EN_TRAMITE', 'SUSPENDIDO', 'ARCHIVADO', 'FINALIZADO')),
    CONSTRAINT ck_asunto_fechas  CHECK (fecha_archivo IS NULL OR fecha_archivo >= fecha_inicio)
);

CREATE TABLE IF NOT EXISTS asunto_abogado (
    numero_expediente VARCHAR(20) NOT NULL,
    id_abogado        BIGINT      NOT NULL,
    PRIMARY KEY (numero_expediente, id_abogado),
    CONSTRAINT fk_aa_asunto  FOREIGN KEY (numero_expediente) REFERENCES asuntos (numero_expediente) ON DELETE CASCADE,
    CONSTRAINT fk_aa_abogado FOREIGN KEY (id_abogado)        REFERENCES abogados (id_abogado)
);

-- 4) Datos de ejemplo
INSERT IGNORE INTO clientes (id_cliente, nombre_cliente, direccion_cliente, telefono_cliente, email_cliente) VALUES
    (1020304050, 'Maria Rodriguez',  'Cra 7 # 45-10, Bogotá',       '3001234567', 'maria.rodriguez@correo.com'),
    (1015987654, 'Carlos Martinez',  'Cll 100 # 15-20, Bogotá',     '3109876543', 'carlos.martinez@correo.com'),
    (1032165498, 'Ana Gomez',        'Av 68 # 22-31, Bogotá',       '3155551234', 'ana.gomez@correo.com'),
    (900123456,  'Constructora Andina SAS', 'Cll 26 # 69-76, Bogotá', '6017654321', 'juridica@andina.com.co');

INSERT IGNORE INTO abogados (id_abogado, tarjeta_profesional, nombre_abogado, direccion_abogado, telefono_abogado, email_abogado) VALUES
    (79555111, '145236', 'Luis Fernandez',   'Cll 72 # 10-07, Bogotá', '3201112233', 'lfernandez@gabinete.com'),
    (52444222, '198745', 'Laura Torres',     'Cra 11 # 93-50, Bogotá', '3187778899', 'ltorres@gabinete.com'),
    (80333444, '210987', 'Andres Lopez',     'Cll 85 # 19-32, Bogotá', '3014445566', 'alopez@gabinete.com');

-- Cuentas de Luis Fernandez y Laura Torres. Andres Lopez queda sin cuenta (la relación es opcional).
INSERT IGNORE INTO usuarios (cedula_usuario, nombre_usuario, email_usuario, usuario, password) VALUES
    (79555111, 'Luis Fernandez', 'lfernandez@gabinete.com', 'lfernandez', 'Luis2026x'),
    (52444222, 'Laura Torres',   'ltorres@gabinete.com',    'ltorres',    'Laura2026x');

UPDATE abogados SET cedula_usuario = 79555111 WHERE id_abogado = 79555111 AND cedula_usuario IS NULL;
UPDATE abogados SET cedula_usuario = 52444222 WHERE id_abogado = 52444222 AND cedula_usuario IS NULL;

INSERT IGNORE INTO asuntos (numero_expediente, fecha_inicio, fecha_archivo, estado, id_cliente) VALUES
    ('EXP-2025-001', '2025-02-10', NULL,         'EN_TRAMITE', 1020304050),
    ('EXP-2025-002', '2025-04-22', '2026-01-15', 'ARCHIVADO',  1015987654),
    ('EXP-2026-001', '2026-03-03', NULL,         'EN_TRAMITE', 900123456),
    ('EXP-2026-002', '2026-05-18', NULL,         'SUSPENDIDO', 1020304050),
    ('EXP-2026-003', '2026-07-01', NULL,         'EN_TRAMITE', 1032165498);

INSERT IGNORE INTO asunto_abogado (numero_expediente, id_abogado) VALUES
    ('EXP-2025-001', 79555111),
    ('EXP-2025-002', 52444222),
    ('EXP-2026-001', 79555111),
    ('EXP-2026-001', 52444222),
    ('EXP-2026-001', 80333444),
    ('EXP-2026-002', 80333444);
    -- EXP-2026-003 aún no tiene abogado asignado ("algunos asuntos" son llevados por abogados)

-- 5) Consultas de verificación
SELECT * FROM clientes;
SELECT * FROM abogados;

-- Asuntos con su cliente y los abogados que los llevan
SELECT a.numero_expediente, a.estado, a.fecha_inicio, a.fecha_archivo,
       c.nombre_cliente,
       GROUP_CONCAT(ab.nombre_abogado SEPARATOR ', ') AS abogados
FROM asuntos a
JOIN clientes c            ON c.id_cliente = a.id_cliente
LEFT JOIN asunto_abogado x ON x.numero_expediente = a.numero_expediente
LEFT JOIN abogados ab      ON ab.id_abogado = x.id_abogado
GROUP BY a.numero_expediente, a.estado, a.fecha_inicio, a.fecha_archivo, c.nombre_cliente
ORDER BY a.numero_expediente;

-- Abogados con su cuenta de usuario (NULL = sin cuenta)
SELECT ab.id_abogado, ab.nombre_abogado, u.cedula_usuario, u.usuario
FROM abogados ab
LEFT JOIN usuarios u ON u.cedula_usuario = ab.cedula_usuario
ORDER BY ab.nombre_abogado;
