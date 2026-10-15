-- Radicados que el servicio vigilancia consulta, y un historial de consultas de ejemplo.
SET client_encoding = 'UTF8';

-- Todos los procesos marcados como vigilados. Para la base de pruebas la fuente es MANUAL (ver ADR 0005).
INSERT INTO vigilancia.radicados_vigilados (radicado, id_proceso, fuente, frecuencia_horas, ultima_consulta, ultima_actuacion)
SELECT p.radicado, p.id_proceso, 'MANUAL', 24,
       now() - interval '6 hours',
       (SELECT max(pr.fecha_estado) FROM procesos.providencias pr WHERE pr.id_proceso = p.id_proceso)
FROM procesos.procesos p
WHERE p.vigilado;

-- Historial de ejemplo: consultas exitosas, una con actuación nueva y un radicado con fallos seguidos.
INSERT INTO vigilancia.consultas (radicado, fecha, exitosa, actuaciones_nuevas, detalle) VALUES
    ('11001310500820250051200', now() - interval '2 days',  true,  1, '{"actuacion": "Niega prueba testimonial", "despacho": "Juzgado 008 Laboral del Circuito de Bogotá"}'),
    ('11001310500820250051200', now() - interval '1 day',   true,  0, '{"mensaje": "Sin actuaciones nuevas"}'),
    ('11001310500820230040101', now() - interval '1 day',   true,  1, '{"actuacion": "Admite recurso y corre traslado para alegar", "despacho": "Tribunal Superior de Bogotá — Sala Laboral"}'),
    ('05001310502120260011800', now() - interval '3 days',  true,  1, '{"actuacion": "Corre traslado de la contestación"}'),
    ('11001400301520260049800', now() - interval '4 days',  true,  1, '{"actuacion": "Admite tutela y ordena informe a la EPS"}'),
    ('11001333103520260005600', now() - interval '3 hours', false, 0, '{"error": "Tiempo de espera agotado consultando el portal"}'),
    ('11001333103520260005600', now() - interval '2 hours', false, 0, '{"error": "Tiempo de espera agotado consultando el portal"}'),
    ('11001333103520260005600', now() - interval '1 hour',  false, 0, '{"error": "Tiempo de espera agotado consultando el portal"}');

UPDATE vigilancia.radicados_vigilados SET fallos_consecutivos = 3
WHERE radicado = '11001333103520260005600';
