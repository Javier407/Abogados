-- =====================================================================
--  Base de datos del taller: Ciclo 3 - Semana 3
--  Proyecto Java Web con SpringBoot (ciclo3back)
--  Universidad El Bosque - MisiónTIC 2022
-- ---------------------------------------------------------------------
--  Ejecutar este script en MySQL Workbench ANTES de correr la app.
--
--  Nota: como application.properties tiene
--        spring.jpa.hibernate.ddl-auto=update, Hibernate crea/actualiza
--        la tabla `usuarios` automáticamente al arrancar la aplicación.
--        Aun así, aquí se deja la definición completa por si se quiere
--        crear manualmente (como en la Semana 2).
-- =====================================================================

-- 1) Crear la base de datos
CREATE DATABASE IF NOT EXISTS tiendagenerica
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE tiendagenerica;

-- 2) Usuario de conexión que usa la aplicación (application.properties)
--    username = admin  /  password = admin123
CREATE USER IF NOT EXISTS 'admin'@'localhost' IDENTIFIED BY 'admin123';
GRANT ALL PRIVILEGES ON tiendagenerica.* TO 'admin'@'localhost';
FLUSH PRIVILEGES;

-- 3) Tabla usuarios (equivale a la entidad co.edu.unbosque.ciclo3back.model.Usuarios)
--    La PK cedula_usuario es 'long' en Java -> BIGINT en MySQL.
CREATE TABLE IF NOT EXISTS usuarios (
    cedula_usuario BIGINT       NOT NULL,
    nombre_usuario VARCHAR(255),
    email_usuario  VARCHAR(255),
    usuario        VARCHAR(255),
    password       VARCHAR(255),
    PRIMARY KEY (cedula_usuario)
);

INSERT INTO usuarios (cedula_usuario, nombre_usuario, email_usuario, usuario, password) VALUES
    (123456789,  'Javier Gutierrez',   'javier@correo.com',             'javierg',    'clave123'),
    (1020304050, 'Maria Rodriguez',    'maria.rodriguez@correo.com',    'mariar',     'Maria2024'),
    (1015987654, 'Carlos Martinez',    'carlos.martinez@correo.com',    'carlosm',    'Carlos88x'),
    (1032165498, 'Ana Gomez',          'ana.gomez@correo.com',          'anag',       'AnaG2024'),
    (1075342198, 'Luis Fernandez',     'luis.fernandez@correo.com',     'luisf',      'LuisF077'),
    (1098765432, 'Laura Torres',       'laura.torres@correo.com',       'laurat',     'Laura021x'),
    (1004567890, 'Andres Lopez',       'andres.lopez@correo.com',       'andresl',    'Andres09z'),
    (1067894321, 'Valentina Diaz',     'valentina.diaz@correo.com',     'valentinad', 'ValeD033'),
    (1051239876, 'Sebastian Ramirez',  'sebastian.ramirez@correo.com',  'sebastianr', 'SebasR045'),
    (1029384756, 'Camila Vargas',      'camila.vargas@correo.com',      'camilav',    'CamilaV12'),
    (1088776655, 'Diego Morales',      'diego.morales@correo.com',      'diegom',     'DiegoM66'),
    (1043210987, 'Paula Castro',       'paula.castro@correo.com',       'paulac',     'PaulaC90'),
    (1076543210, 'Julian Herrera',     'julian.herrera@correo.com',     'julianh',    'JulianH55'),
    (1090123456, 'Daniela Rojas',      'daniela.rojas@correo.com',      'danielar',   'DaniR2024'),
    (1012348765, 'Mateo Sanchez',      'mateo.sanchez@correo.com',      'mateos',     'MateoS33')
ON DUPLICATE KEY UPDATE
    nombre_usuario = VALUES(nombre_usuario),
    email_usuario  = VALUES(email_usuario),
    usuario        = VALUES(usuario),
    password       = VALUES(password);

-- 5) Verificar
SELECT * FROM usuarios;
