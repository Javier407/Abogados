-- Prueba del relevo del abogado a cargo y del cierre del proceso en la base real (migración 019). No deja rastro:
-- todo va en una transacción que se deshace al final.
--   docker compose exec -T postgres psql -U gabinete_admin -d gabinete -v ON_ERROR_STOP=1 < ../db/pruebas/019-relevo-y-cierre.sql
-- Debe terminar con "PRUEBA OK". Cualquier falla detiene el script con el motivo.
SET client_encoding = 'UTF8';
BEGIN;

-- Un proceso abierto con responsable y alguien de la firma que no esté en su equipo.
CREATE TEMP TABLE caso ON COMMIT DROP AS
    SELECT a.id_proceso, a.id_persona AS anterior,
           (SELECT p.id_persona FROM gabinete.personal p
            WHERE NOT EXISTS (SELECT 1 FROM procesos.asignaciones x
                              WHERE x.id_proceso = a.id_proceso AND x.id_persona = p.id_persona)
            ORDER BY p.id_persona LIMIT 1) AS nuevo
    FROM procesos.asignaciones a JOIN procesos.procesos pr ON pr.id_proceso = a.id_proceso
    WHERE a.rol = 'RESPONSABLE' AND pr.estado = 'ACTIVO'
    ORDER BY a.id_proceso LIMIT 1;
GRANT SELECT ON caso TO svc_procesos;

-- 1. Lo que ya existía entró al historial como periodo abierto.
DO $$
BEGIN
    ASSERT NOT EXISTS (SELECT 1 FROM procesos.asignaciones a
                       WHERE NOT EXISTS (SELECT 1 FROM procesos.asignaciones_historial h
                                         WHERE h.id_proceso = a.id_proceso AND h.id_persona = a.id_persona
                                           AND h.rol = a.rol AND h.hasta IS NULL)),
        'hay asignaciones sin su periodo abierto en el historial';
    RAISE NOTICE '1. cada asignación tiene su periodo abierto en el historial';
END $$;

-- 2. Relevo como lo hace el servicio (svc_procesos): motivo por variables de la transacción, sale el anterior,
--    entra el nuevo y le pasan los términos pendientes.
SET LOCAL ROLE svc_procesos;
SELECT set_config('procesos.motivo_relevo', 'RENUNCIA', true), set_config('procesos.nota_relevo', 'Renunció al poder', true),
       set_config('procesos.autor_relevo', '19000111', true) \gset
DELETE FROM procesos.asignaciones a USING caso c WHERE a.id_proceso = c.id_proceso AND a.id_persona = c.anterior;
INSERT INTO procesos.asignaciones (id_proceso, id_persona, rol, desde)
SELECT id_proceso, nuevo, 'RESPONSABLE', CURRENT_DATE FROM caso;
UPDATE procesos.terminos t SET id_responsable = c.nuevo FROM caso c
WHERE t.id_proceso = c.id_proceso AND t.estado = 'PENDIENTE' AND t.id_responsable = c.anterior;
RESET ROLE;

DO $$
DECLARE c caso%ROWTYPE; h_ant procesos.asignaciones_historial%ROWTYPE; h_nue procesos.asignaciones_historial%ROWTYPE;
BEGIN
    SELECT * INTO c FROM caso;
    ASSERT c.nuevo IS NOT NULL, 'no hay a quién pasarle el proceso (datos de prueba incompletos)';
    SELECT * INTO h_ant FROM procesos.asignaciones_historial
     WHERE id_proceso = c.id_proceso AND id_persona = c.anterior ORDER BY id_cambio DESC LIMIT 1;
    SELECT * INTO h_nue FROM procesos.asignaciones_historial
     WHERE id_proceso = c.id_proceso AND id_persona = c.nuevo ORDER BY id_cambio DESC LIMIT 1;
    ASSERT h_ant.hasta = CURRENT_DATE AND h_ant.motivo = 'RENUNCIA' AND h_ant.nota = 'Renunció al poder'
        AND h_ant.cerrado_por = 19000111, format('el periodo del anterior no quedó cerrado con su motivo: %s', h_ant);
    ASSERT h_nue.rol = 'RESPONSABLE' AND h_nue.hasta IS NULL AND h_nue.registrado_por = 19000111,
        format('el nuevo responsable no quedó en el historial: %s', h_nue);
    ASSERT (SELECT count(*) FROM procesos.asignaciones WHERE id_proceso = c.id_proceso AND rol = 'RESPONSABLE') = 1,
        'el proceso no quedó con un solo responsable';
    ASSERT NOT EXISTS (SELECT 1 FROM procesos.terminos WHERE id_proceso = c.id_proceso AND estado = 'PENDIENTE'
                       AND id_responsable = c.anterior), 'quedaron términos pendientes a nombre del anterior';
    RAISE NOTICE '2. el relevo cierra el periodo del anterior con su motivo y abre el del nuevo';
END $$;

-- 3. Pasar a alguien de responsable a apoyo (como al editar el proceso) también queda registrado, sin motivo.
SELECT set_config('procesos.motivo_relevo', '', true) \gset
UPDATE procesos.asignaciones a SET rol = 'APOYO' FROM caso c WHERE a.id_proceso = c.id_proceso AND a.id_persona = c.nuevo;
DO $$
DECLARE c caso%ROWTYPE;
BEGIN
    SELECT * INTO c FROM caso;
    ASSERT (SELECT count(*) FROM procesos.asignaciones_historial
            WHERE id_proceso = c.id_proceso AND id_persona = c.nuevo) = 2, 'el cambio de papel no abrió un periodo nuevo';
    ASSERT EXISTS (SELECT 1 FROM procesos.asignaciones_historial WHERE id_proceso = c.id_proceso AND id_persona = c.nuevo
                   AND rol = 'RESPONSABLE' AND hasta = CURRENT_DATE AND motivo IS NULL),
        'el periodo como responsable no se cerró (o quedó con un motivo que nadie dio)';
    ASSERT EXISTS (SELECT 1 FROM procesos.asignaciones_historial WHERE id_proceso = c.id_proceso AND id_persona = c.nuevo
                   AND rol = 'APOYO' AND hasta IS NULL), 'no se abrió el periodo como apoyo';
    RAISE NOTICE '3. un cambio de papel sin relevo también queda en el historial';
END $$;

-- 4. Cierre: solo un proceso cerrado lleva motivo y fecha, y el motivo debe ser uno de los conocidos.
DO $$
DECLARE c caso%ROWTYPE;
BEGIN
    SELECT * INTO c FROM caso;
    BEGIN
        UPDATE procesos.procesos SET motivo_cierre = 'SENTENCIA', fecha_cierre = CURRENT_DATE WHERE id_proceso = c.id_proceso;
        RAISE EXCEPTION 'un proceso ACTIVO aceptó datos de cierre';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE procesos.procesos SET estado = 'TERMINADO', etapa = 'CERRADO', motivo_cierre = 'PORQUE_SI',
               fecha_cierre = CURRENT_DATE WHERE id_proceso = c.id_proceso;
        RAISE EXCEPTION 'se aceptó un motivo de cierre desconocido';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    UPDATE procesos.procesos SET etapa_antes_cierre = etapa, estado = 'TERMINADO', etapa = 'CERRADO',
           motivo_cierre = 'SENTENCIA_ANTICIPADA', fecha_cierre = CURRENT_DATE, nota_cierre = 'Prescripción probada',
           cerrado_por = 19000111, vigilado = false
     WHERE id_proceso = c.id_proceso;
    ASSERT (SELECT motivo_cierre FROM procesos.procesos WHERE id_proceso = c.id_proceso) = 'SENTENCIA_ANTICIPADA',
        'el cierre no se guardó';
    RAISE NOTICE '4. el cierre exige motivo conocido y solo lo lleva un proceso cerrado';
END $$;

-- 5. Notificaciones acepta el aviso nuevo.
DO $$
BEGIN
    ASSERT pg_get_constraintdef((SELECT oid FROM pg_constraint WHERE conname = 'notificaciones_evento_check'))
        LIKE '%proceso.reasignado%', 'notificaciones no acepta el evento proceso.reasignado';
    RAISE NOTICE '5. notificaciones acepta proceso.reasignado';
END $$;

-- 6. El servicio no puede borrar historia.
SET LOCAL ROLE svc_procesos;
DO $$
BEGIN
    BEGIN
        DELETE FROM procesos.asignaciones_historial;
        RAISE EXCEPTION 'svc_procesos pudo borrar el historial';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    RAISE NOTICE '6. el servicio no puede borrar el historial del equipo';
END $$;
RESET ROLE;

ROLLBACK;
\echo PRUEBA OK
