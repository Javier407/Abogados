-- Consultas de ejemplo sobre la base de pruebas.
-- Ejecutar: docker compose exec -T postgres psql -U gabinete_admin -d gabinete < ../db/consultas/ejemplos.sql
SET client_encoding = 'UTF8';
\pset footer off

\echo '== 1. Términos vencidos o que vencen en los próximos 5 días hábiles (alertas de hoy) =='
SELECT t.situacion, t.dias_habiles_restantes AS dias, to_char(t.fecha_vencimiento, 'DD/MM/YYYY') AS vence,
       t.descripcion, v.radicado_legible, v.rama,
       pe.nombres || ' ' || pe.apellidos AS responsable
FROM procesos.v_terminos t
JOIN procesos.v_procesos v ON v.id_proceso = t.id_proceso
JOIN gabinete.personal pe  ON pe.id_persona = t.id_responsable
WHERE t.situacion IN ('VENCIDO', 'POR_VENCER')
ORDER BY t.fecha_vencimiento;

\echo '== 2. Carga de procesos activos por abogado responsable =='
SELECT pe.nombres || ' ' || pe.apellidos AS abogado, c.nombre AS cargo,
       count(*) FILTER (WHERE p.estado = 'ACTIVO') AS activos,
       count(*) AS total
FROM procesos.asignaciones a
JOIN procesos.procesos p  ON p.id_proceso = a.id_proceso
JOIN gabinete.personal pe ON pe.id_persona = a.id_persona
JOIN gabinete.cargos c    ON c.id_cargo = pe.id_cargo
WHERE a.rol = 'RESPONSABLE'
GROUP BY abogado, c.nombre
ORDER BY activos DESC, abogado;

\echo '== 3. Procesos por rama y estado =='
SELECT rama, estado, count(*) AS procesos
FROM procesos.v_procesos
GROUP BY rama, estado
ORDER BY rama, estado;

\echo '== 4. Honorarios con saldo pendiente (parte fija pactada menos pagos) =='
SELECT cl.nombre AS cliente, v.radicado_legible, h.forma_cobro,
       h.valor_fijo AS fijo_pactado,
       coalesce(sum(pg.valor), 0) AS pagado,
       h.valor_fijo - coalesce(sum(pg.valor), 0) AS saldo
FROM gabinete.honorarios_pactados h
JOIN gabinete.clientes cl  ON cl.id_cliente = h.id_cliente
JOIN procesos.v_procesos v ON v.id_proceso = h.id_proceso
LEFT JOIN gabinete.pagos pg ON pg.id_honorario = h.id_honorario
WHERE h.estado = 'VIGENTE' AND h.valor_fijo IS NOT NULL
GROUP BY cl.nombre, v.radicado_legible, h.forma_cobro, h.valor_fijo
HAVING h.valor_fijo - coalesce(sum(pg.valor), 0) > 0
ORDER BY saldo DESC;

\echo '== 5. Desglose de un radicado (segunda instancia) =='
SELECT radicado, cod_dane, cod_entidad, cod_especialidad, cod_despacho, anio, consecutivo, instancia
FROM procesos.procesos WHERE radicado = '11001310303220230097701';

\echo '== 6. Historial de un proceso: providencias y términos =='
SELECT pr.fecha_estado, pr.tipo, pr.clase, t.descripcion AS termino, t.fecha_vencimiento, t.estado
FROM procesos.providencias pr
LEFT JOIN procesos.terminos t ON t.id_providencia = pr.id_providencia
WHERE pr.id_proceso = 2
ORDER BY pr.fecha_estado;

\echo '== 7. Audiencias programadas en los próximos 30 días =='
SELECT a.fecha, a.descripcion, v.radicado_legible, v.despacho_actual
FROM procesos.actuaciones a
JOIN procesos.v_procesos v ON v.id_proceso = a.id_proceso
WHERE a.tipo = 'AUDIENCIA' AND a.fecha BETWEEN CURRENT_DATE AND CURRENT_DATE + 30
ORDER BY a.fecha;

\echo '== 8. Peritos y en cuántas pruebas participan =='
SELECT pe.nombre, pe.especialidad, count(pr.id_prueba) AS pruebas
FROM gabinete.peritos pe
LEFT JOIN procesos.pruebas pr ON pr.id_perito = pe.id_perito
GROUP BY pe.nombre, pe.especialidad
ORDER BY pruebas DESC, pe.nombre;

\echo '== 9. Tarifas de referencia en pesos 2026 (SMMLV × valor) =='
SELECT t.fuente, coalesce(t.codigo_rama, 'GENERAL') AS rama, t.servicio, t.forma_cobro,
       round(t.valor_smmlv * s.valor)     AS desde_pesos,
       round(t.valor_smmlv_max * s.valor) AS hasta_pesos,
       t.porcentaje
FROM gabinete.tarifas t
CROSS JOIN gabinete.smmlv s
WHERE s.anio = 2026
ORDER BY t.fuente, rama, t.servicio;

\echo '== 10. Radicados con fallas de vigilancia =='
SELECT radicado, fallos_consecutivos, ultima_consulta
FROM vigilancia.radicados_vigilados WHERE fallos_consecutivos > 0;

\echo '== 11. Notificaciones pendientes por persona =='
SELECT pe.nombres || ' ' || pe.apellidos AS persona, count(*) AS pendientes
FROM notificaciones.notificaciones n
JOIN gabinete.personal pe ON pe.id_persona = n.id_persona
WHERE n.estado = 'PENDIENTE'
GROUP BY persona ORDER BY pendientes DESC;

-- ============================ Verificaciones ============================
\echo '== V1. Conteos generales =='
SELECT (SELECT count(*) FROM procesos.procesos)                         AS procesos,
       (SELECT count(DISTINCT codigo_rama) FROM procesos.v_procesos)    AS ramas_con_procesos,
       (SELECT count(*) FROM gabinete.clientes)                         AS clientes,
       (SELECT count(*) FROM gabinete.personal)                         AS personal,
       (SELECT count(*) FROM procesos.providencias)                     AS providencias,
       (SELECT count(*) FROM procesos.terminos)                         AS terminos,
       (SELECT count(*) FROM gabinete.peritos)                          AS peritos;

\echo '== V2. Situación de los términos (debe haber VENCIDO, POR_VENCER y AL_DIA) =='
SELECT situacion, count(*) FROM procesos.v_terminos GROUP BY situacion ORDER BY situacion;

\echo '== V3. Procesos sin abogado responsable (debe salir vacío) =='
SELECT p.id_proceso, p.radicado FROM procesos.procesos p
WHERE NOT EXISTS (SELECT 1 FROM procesos.asignaciones a WHERE a.id_proceso = p.id_proceso AND a.rol = 'RESPONSABLE');

\echo '== V4. Procesos con menos de 2 providencias (debe salir vacío) =='
SELECT p.id_proceso, count(pr.*) AS providencias FROM procesos.procesos p
LEFT JOIN procesos.providencias pr ON pr.id_proceso = p.id_proceso
GROUP BY p.id_proceso HAVING count(pr.*) < 2;
