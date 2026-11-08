-- Prueba de la bitácora encadenada en la base real (migración 015). No deja rastro: todo va en una transacción
-- que se deshace al final, y como el número lo pone el disparador (último + 1), tampoco quedan huecos.
--   docker compose exec -T postgres psql -U gabinete_admin -d gabinete -v ON_ERROR_STOP=1 < ../db/pruebas/015-auditoria.sql
-- Debe terminar con "PRUEBA OK". Cualquier falla detiene el script con el motivo.
SET client_encoding = 'UTF8';
BEGIN;

-- 1. El hash de la base es el mismo que calcula el gateway (CadenaTest, calculado aparte con Python)
DO $$
BEGIN
    ASSERT auditoria.huella(1, '2026-11-08T15:04:05.123456Z', '1020304050', 'ABOGADO', 'GET', '/api/procesos/34',
        200::smallint, 12, '100.64.0.7', repeat('0', 64))
        = 'b52537578c6831f6967363dc2b2ba34f971f72f2029a9222cb7abeaa14d455a6', 'hash 1 distinto al del gateway';
    ASSERT auditoria.huella(2, '2026-11-08T15:04:06.000001Z', 'anonimo', '', 'POST', '/api/auth/login',
        NULL, 3, NULL, 'b52537578c6831f6967363dc2b2ba34f971f72f2029a9222cb7abeaa14d455a6')
        = 'c7edd8c64f006bdb15489b2005f18368f34881bca96084a96cebefd11e37b610', 'hash 2 distinto al del gateway';
    RAISE NOTICE '1. el hash de la base coincide con el del gateway';
END $$;

-- 2. El gateway agrega: la base pone número, hash anterior y hash, encadenados con el último que había.
-- Primero el mismo candado del disparador: mientras dure la prueba, el gateway espera para escribir.
SELECT pg_advisory_xact_lock(hashtext('auditoria.registros')) \gset
CREATE TEMP TABLE antes ON COMMIT DROP AS
    SELECT coalesce(max(id), 0) AS id,
           coalesce((SELECT hash FROM auditoria.registros ORDER BY id DESC LIMIT 1), repeat('0', 64)) AS hash
    FROM auditoria.registros;
GRANT SELECT ON antes TO svc_gateway;
SET LOCAL ROLE svc_gateway;
INSERT INTO auditoria.registros (id, momento, usuario, roles, metodo, ruta, estado, duracion_ms, ip, hash_anterior, hash)
VALUES (424242, now(), 'prueba', 'ADMIN', 'GET', '/api/prueba/1', 200, 5, '127.0.0.1', repeat('f', 64), repeat('f', 64)),
       (424243, now(), 'prueba', 'ADMIN', 'GET', '/api/prueba/2', NULL, 6, NULL, repeat('f', 64), repeat('e', 64));
DO $$
DECLARE a antes%ROWTYPE; r1 auditoria.registros%ROWTYPE; r2 auditoria.registros%ROWTYPE;
BEGIN
    SELECT * INTO a FROM antes;
    SELECT * INTO r1 FROM auditoria.registros WHERE ruta = '/api/prueba/1';
    SELECT * INTO r2 FROM auditoria.registros WHERE ruta = '/api/prueba/2';
    ASSERT r1.id = a.id + 1 AND r2.id = a.id + 2, 'los números no siguen al último (o se aceptó el que mandó el cliente)';
    ASSERT r1.hash_anterior = a.hash, 'el primero nuevo no encadena con el último que había';
    ASSERT r2.hash_anterior = r1.hash, 'el segundo no encadena con el primero';
    ASSERT r1.hash = auditoria.huella(r1.id, r1.momento, r1.usuario, r1.roles, r1.metodo, r1.ruta, r1.estado,
        r1.duracion_ms, r1.ip, r1.hash_anterior), 'se aceptó un hash que mandó el cliente';
    RAISE NOTICE '2. svc_gateway agrega y la base encadena (registros % y %)', r1.id, r2.id;
END $$;

-- 3. Nadie cambia ni borra: ni svc_gateway (sin permiso) ni el dueño de la tabla (disparador)
DO $$
BEGIN
    BEGIN
        UPDATE auditoria.registros SET ruta = '/api/otra' WHERE ruta = '/api/prueba/1';
        RAISE EXCEPTION 'svc_gateway pudo cambiar un registro';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    BEGIN
        DELETE FROM auditoria.registros WHERE ruta = '/api/prueba/1';
        RAISE EXCEPTION 'svc_gateway pudo borrar un registro';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    BEGIN
        INSERT INTO auditoria.registros (momento, usuario, roles, metodo, ruta, duracion_ms)
        VALUES (now(), 'prueba', '', 'GET', E'/api/x\nGET', 1);
        RAISE EXCEPTION 'se aceptó un salto de línea en la ruta';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    RAISE NOTICE '3. svc_gateway no puede cambiar ni borrar, ni meter saltos de línea';
END $$;
RESET ROLE;
DO $$
BEGIN
    BEGIN
        UPDATE auditoria.registros SET ruta = '/api/otra' WHERE ruta = '/api/prueba/1';
        RAISE EXCEPTION 'el dueño pudo cambiar un registro';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    BEGIN
        DELETE FROM auditoria.registros WHERE ruta = '/api/prueba/2';
        RAISE EXCEPTION 'el dueño pudo borrar un registro';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    BEGIN
        TRUNCATE auditoria.registros;
        RAISE EXCEPTION 'el dueño pudo vaciar la bitácora';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    RAISE NOTICE '4. el dueño de la tabla tampoco puede cambiar, borrar ni vaciar';
END $$;

-- 5. La revisión cuadra desde el último sello, y si alguien salta los disparadores para cambiar un registro, se nota
DO $$
DECLARE a antes%ROWTYPE; v record;
BEGIN
    SELECT * INTO a FROM antes;
    SELECT * INTO v FROM auditoria.verificar(a.id, CASE WHEN a.id = 0 THEN NULL ELSE a.hash END);
    ASSERT v.rotura_en IS NULL AND v.revisados = 2, format('la cadena nueva no cuadra: %s', v);
    SET LOCAL session_replication_role = replica;      -- salta los disparadores (solo un superusuario)
    UPDATE auditoria.registros SET ruta = '/api/alterada' WHERE ruta = '/api/prueba/1';
    SET LOCAL session_replication_role = origin;
    SELECT * INTO v FROM auditoria.verificar(a.id, CASE WHEN a.id = 0 THEN NULL ELSE a.hash END);
    ASSERT v.rotura_en = a.id + 1, format('no se detectó el registro alterado: %s', v);
    RAISE NOTICE '5. la revisión cuadra y detecta el registro alterado (rotura en %)', v.rotura_en;
END $$;

ROLLBACK;
\echo PRUEBA OK
