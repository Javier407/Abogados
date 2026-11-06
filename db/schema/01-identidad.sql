-- Esquema del servicio identidad: cuentas para entrar al sistema y sus roles.
SET client_encoding = 'UTF8';

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- crypt()/gen_salt('bf') para contraseñas con BCrypt

CREATE SCHEMA identidad AUTHORIZATION svc_identidad;

CREATE TABLE identidad.roles (
    id_rol  smallint    PRIMARY KEY,
    codigo  varchar(20) NOT NULL UNIQUE,
    nombre  varchar(60) NOT NULL
);

-- id_usuario = número de documento de la persona (como en el proyecto original).
CREATE TABLE identidad.usuarios (
    id_usuario     bigint       PRIMARY KEY CHECK (id_usuario > 0),
    usuario        varchar(30)  NOT NULL UNIQUE,
    nombre         varchar(120) NOT NULL,
    email          varchar(120) NOT NULL UNIQUE,
    password_hash  varchar(100) NOT NULL,        -- BCrypt ($2a$...), compatible con Spring Security
    activo         boolean      NOT NULL DEFAULT true,
    creado         timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE identidad.usuario_rol (
    id_usuario bigint   NOT NULL REFERENCES identidad.usuarios ON DELETE CASCADE,
    id_rol     smallint NOT NULL REFERENCES identidad.roles,
    PRIMARY KEY (id_usuario, id_rol)
);

GRANT USAGE ON SCHEMA identidad TO svc_identidad;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA identidad TO svc_identidad;
