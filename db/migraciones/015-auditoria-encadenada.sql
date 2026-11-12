-- Bitácora de auditoría a prueba de manipulación: un registro por cada petición a la API, encadenado con SHA-256.
-- Idempotente. Base existente (desde infra/):
--   1) usuario de base del gateway, con la misma clave de los demás servicios (no se muestra):
--      docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v pass="$SVC_PASSWORD"' <<'SQL'
--      SELECT format('CREATE ROLE svc_gateway LOGIN PASSWORD %L', :'pass')
--      WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_gateway') \gexec
--      SQL
--   2) docker compose exec -T postgres psql -U gabinete_admin -d gabinete < ../db/migraciones/015-auditoria-encadenada.sql
-- En una base nueva, 00-init.sh ya crea svc_gateway antes de las migraciones.
--
-- Cómo se protege (docs/14-auditoria.md):
-- - Cadena: cada registro guarda el hash del anterior y su propio hash = SHA-256(campos + hash anterior). Cambiar,
--   borrar o intercalar un registro rompe la cadena desde ese punto. Los números van seguidos (1, 2, 3…): un hueco
--   también se nota.
-- - Solo anexar: el gateway (svc_gateway) solo puede agregar y leer; un disparador rechaza UPDATE, DELETE y TRUNCATE
--   a cualquiera, también al dueño de la tabla.
-- - Sellos fuera del servidor: el respaldo diario anota el último número y su hash, y la copia al PC los guarda.
--   Quien rehiciera toda la cadena desde cero no podría hacer coincidir los sellos ya guardados en el PC.
SET client_encoding = 'UTF8';

CREATE SCHEMA IF NOT EXISTS auditoria;

-- En una base nueva lo crea 00-init.sh con clave. Aquí solo por si falta (sin clave no puede entrar: ver arriba).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_gateway') THEN
        CREATE ROLE svc_gateway LOGIN;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS auditoria.registros (
    id             bigint       PRIMARY KEY,           -- consecutivo sin huecos, lo pone el disparador
    momento        timestamptz  NOT NULL,              -- cuándo llegó la petición
    usuario        varchar(64)  NOT NULL,              -- documento del token, o 'anonimo'
    roles          varchar(200) NOT NULL,
    metodo         varchar(10)  NOT NULL,
    ruta           varchar(500) NOT NULL,              -- sin parámetros: ahí podría ir un token
    estado         smallint,                           -- código HTTP; NULL si el cliente cortó antes de la respuesta
    duracion_ms    integer      NOT NULL,
    ip             varchar(45),
    hash_anterior  char(64)     NOT NULL,
    hash           char(64)     NOT NULL UNIQUE,
    -- Los campos se unen con saltos de línea para calcular el hash: ningún campo puede tenerlos.
    CONSTRAINT registros_sin_saltos CHECK (
        strpos(usuario || roles || metodo || ruta || coalesce(ip, ''), E'\n') = 0)
);
CREATE INDEX IF NOT EXISTS registros_usuario ON auditoria.registros (usuario, id DESC);
CREATE INDEX IF NOT EXISTS registros_momento ON auditoria.registros (momento);

-- Huella de un registro. El gateway la recalcula por su cuenta (Cadena.java) con el mismo formato:
--   id \n momento UTC con microsegundos \n usuario \n roles \n método \n ruta \n estado|- \n duración \n ip|- \n hash anterior
CREATE OR REPLACE FUNCTION auditoria.huella(p_id bigint, p_momento timestamptz, p_usuario text, p_roles text,
        p_metodo text, p_ruta text, p_estado smallint, p_duracion integer, p_ip text, p_anterior text)
    RETURNS char(64) LANGUAGE sql STABLE AS $$
    SELECT encode(sha256(convert_to(concat_ws(E'\n',
        p_id::text,
        to_char(p_momento AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
        p_usuario, p_roles, p_metodo, p_ruta,
        coalesce(p_estado::text, '-'),
        p_duracion::text,
        coalesce(p_ip, '-'),
        p_anterior), 'UTF8')), 'hex')
$$;

-- Al agregar: número siguiente, hash del anterior y hash propio. El candado serializa a quienes escriben a la vez,
-- así el orden de los números es el de la cadena. SECURITY DEFINER: lee el último registro con permisos del dueño.
CREATE OR REPLACE FUNCTION auditoria.encadenar() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = auditoria, pg_temp AS $$
DECLARE
    ultimo_id    bigint;
    ultimo_hash  char(64);
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('auditoria.registros'));
    SELECT id, hash INTO ultimo_id, ultimo_hash FROM auditoria.registros ORDER BY id DESC LIMIT 1;
    NEW.id := coalesce(ultimo_id, 0) + 1;
    NEW.hash_anterior := coalesce(ultimo_hash, repeat('0', 64));
    NEW.hash := auditoria.huella(NEW.id, NEW.momento, NEW.usuario, NEW.roles, NEW.metodo, NEW.ruta, NEW.estado,
        NEW.duracion_ms, NEW.ip, NEW.hash_anterior);
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION auditoria.solo_anexar() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La bitácora de auditoría solo admite agregar registros (% no permitido)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END $$;

DROP TRIGGER IF EXISTS encadenar ON auditoria.registros;
CREATE TRIGGER encadenar BEFORE INSERT ON auditoria.registros
    FOR EACH ROW EXECUTE FUNCTION auditoria.encadenar();
DROP TRIGGER IF EXISTS no_cambiar ON auditoria.registros;
CREATE TRIGGER no_cambiar BEFORE UPDATE OR DELETE ON auditoria.registros
    FOR EACH ROW EXECUTE FUNCTION auditoria.solo_anexar();
DROP TRIGGER IF EXISTS no_vaciar ON auditoria.registros;
CREATE TRIGGER no_vaciar BEFORE TRUNCATE ON auditoria.registros
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria.solo_anexar();

-- Revisa la cadena desde un sello (número y hash ya conocidos) o desde el principio. Devuelve cuántos registros
-- revisó, el último y, si no cuadra, el primer número que falla. La usa el vigía en cada vuelta desde el último
-- sello que revisó, y una vez al día desde el principio.
CREATE OR REPLACE FUNCTION auditoria.verificar(p_desde bigint DEFAULT 0, p_hash text DEFAULT NULL,
        OUT revisados bigint, OUT ultimo_id bigint, OUT ultimo_hash text, OUT rotura_en bigint)
    LANGUAGE plpgsql STABLE AS $$
DECLARE
    r auditoria.registros%ROWTYPE;
BEGIN
    revisados := 0;
    ultimo_id := 0;
    ultimo_hash := repeat('0', 64);
    IF p_desde > 0 THEN
        SELECT hash INTO ultimo_hash FROM auditoria.registros WHERE id = p_desde;
        IF ultimo_hash IS NULL OR ultimo_hash <> p_hash THEN
            rotura_en := p_desde;                       -- el sello ya no está o cambió
            RETURN;
        END IF;
        ultimo_id := p_desde;
    END IF;
    FOR r IN SELECT * FROM auditoria.registros WHERE id > p_desde ORDER BY id LOOP
        IF r.id <> ultimo_id + 1 OR r.hash_anterior <> ultimo_hash
           OR r.hash <> auditoria.huella(r.id, r.momento, r.usuario, r.roles, r.metodo, r.ruta, r.estado,
                                         r.duracion_ms, r.ip, r.hash_anterior) THEN
            rotura_en := r.id;
            RETURN;
        END IF;
        revisados := revisados + 1;
        ultimo_id := r.id;
        ultimo_hash := r.hash;
    END LOOP;
END $$;

-- El gateway solo agrega y lee. Nadie más que el dueño tiene permisos sobre el esquema.
REVOKE ALL ON SCHEMA auditoria FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA auditoria FROM PUBLIC;
GRANT USAGE ON SCHEMA auditoria TO svc_gateway;
REVOKE ALL ON auditoria.registros FROM svc_gateway;
GRANT SELECT, INSERT ON auditoria.registros TO svc_gateway;
