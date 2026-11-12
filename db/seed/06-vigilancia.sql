-- Radicados que el servicio vigilancia consulta: todos en MANUAL y sin consultar. El historial de ejemplo (fuente
-- simulada, línea base, actuaciones vistas y consultas) está en db/demo/, que corre después de las migraciones
-- porque usa columnas y tablas que agrega la migración 002.
SET client_encoding = 'UTF8';

-- Todos los procesos marcados como vigilados. Para la base de pruebas la fuente es MANUAL (ver ADR 0005).
INSERT INTO vigilancia.radicados_vigilados (radicado, id_proceso, fuente, frecuencia_horas)
SELECT p.radicado, p.id_proceso, 'MANUAL', 24
FROM procesos.procesos p
WHERE p.vigilado;
