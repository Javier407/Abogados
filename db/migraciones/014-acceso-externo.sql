-- Entrar con Google, Microsoft o GitHub: solo cuentas ya existentes, enlazadas por la propia persona. Idempotente.
-- Base existente: docker compose exec -T postgres psql -U gabinete_admin -d gabinete < ../db/migraciones/014-acceso-externo.sql
SET client_encoding = 'UTF8';

-- Una cuenta externa enlazada. La identidad es el identificador estable del proveedor (sujeto), nunca el correo:
-- el correo puede cambiar o repetirse entre proveedores. Una persona tiene a lo sumo una cuenta por proveedor, y una
-- cuenta externa pertenece a una sola persona.
CREATE TABLE IF NOT EXISTS identidad.cuentas_externas (
    proveedor   varchar(20)  NOT NULL CHECK (proveedor IN ('GOOGLE', 'MICROSOFT', 'GITHUB')),
    sujeto      varchar(255) NOT NULL,
    id_usuario  bigint       NOT NULL REFERENCES identidad.usuarios ON DELETE CASCADE,
    correo      varchar(255),
    creada      timestamptz  NOT NULL DEFAULT now(),
    ultimo_uso  timestamptz,
    PRIMARY KEY (proveedor, sujeto),
    UNIQUE (id_usuario, proveedor)
);

-- Ida y vuelta al proveedor (OAuth): de un solo uso y de corta vida. Solo se guardan hashes SHA-256: el estado
-- que viaja en la URL y el comprobante de la cookie del navegador que inició el flujo (evita que alguien haga
-- entrar a otra persona con su propia cuenta). verificador: el secreto PKCE, que nunca sale del servidor.
CREATE TABLE IF NOT EXISTS identidad.oauth_estados (
    hash_estado       char(64)     PRIMARY KEY,
    hash_comprobante  char(64)     NOT NULL,
    proveedor         varchar(20)  NOT NULL,
    modo              varchar(10)  NOT NULL CHECK (modo IN ('ENTRAR', 'ENLAZAR')),
    id_usuario        bigint       REFERENCES identidad.usuarios ON DELETE CASCADE,
    verificador       varchar(128) NOT NULL,
    vence             timestamptz  NOT NULL
);
CREATE INDEX IF NOT EXISTS oauth_estados_vence ON identidad.oauth_estados (vence);

GRANT SELECT, INSERT, UPDATE, DELETE ON identidad.cuentas_externas, identidad.oauth_estados TO svc_identidad;
