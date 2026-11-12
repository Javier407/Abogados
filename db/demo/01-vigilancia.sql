-- Datos de ejemplo de vigilancia para una base nueva. Corre DESPUÉS de las migraciones (db/00-init.sh), porque usa
-- la fuente SIMULADA, la línea base y las actuaciones conocidas de la migración 002. No se aplica a bases existentes.
SET client_encoding = 'UTF8';

-- Los radicados del ejemplo se consultan con la fuente simulada (nunca con el portal real: ver ADR 0005).
UPDATE vigilancia.radicados_vigilados
SET fuente = 'SIMULADA', frecuencia_horas = 12, linea_base_hecha = true
WHERE radicado IN ('11001310500820250051200', '11001310500820230040101', '05001310502120260011800',
                   '11001400301520260049800', '11001333103520260005600');

-- Lo que la vigilancia vio en el juzgado. avisada = false: ya existía en la primera consulta (línea base).
INSERT INTO vigilancia.actuaciones_conocidas (radicado, referencia, fecha, actuacion, anotacion, vista, avisada) VALUES
    ('11001310500820250051200', 'SIM:EJ1', current_date - 60, 'Auto admite demanda', NULL, now() - interval '10 days', false),
    ('11001310500820250051200', 'SIM:EJ2', current_date - 20, 'Auto fija fecha de audiencia de conciliación',
        'Audiencia del artículo 77 del CPTSS', now() - interval '10 days', false),
    ('11001310500820250051200', 'SIM:EJ3', current_date - 2, 'Niega prueba testimonial',
        'Se niega por impertinente el testimonio solicitado', now() - interval '2 days', true),
    ('11001310500820230040101', 'SIM:EJ4', current_date - 40, 'Sentencia de primera instancia', NULL, now() - interval '9 days', false),
    ('11001310500820230040101', 'SIM:EJ5', current_date - 1, 'Admite recurso y corre traslado para alegar',
        'Traslado común de cinco días', now() - interval '1 day', true),
    ('05001310502120260011800', 'SIM:EJ6', current_date - 3, 'Corre traslado de la contestación', NULL, now() - interval '3 days', true),
    ('11001400301520260049800', 'SIM:EJ7', current_date - 4, 'Admite tutela y ordena informe a la EPS',
        'Informe en dos días', now() - interval '4 days', true);

-- Historial de consultas con el formato que guarda el servicio: lineaBase, actuaciones, nuevas y la última vista.
INSERT INTO vigilancia.consultas (radicado, fecha, exitosa, actuaciones_nuevas, detalle) VALUES
    ('11001310500820250051200', now() - interval '10 days', true,  0, '{"lineaBase": true, "actuaciones": 2, "nuevas": 0}'),
    ('11001310500820250051200', now() - interval '2 days',  true,  1, '{"actuaciones": 3, "nuevas": 1, "ultima": "Niega prueba testimonial"}'),
    ('11001310500820250051200', now() - interval '1 day',   true,  0, '{"actuaciones": 3, "nuevas": 0}'),
    ('11001310500820230040101', now() - interval '9 days',  true,  0, '{"lineaBase": true, "actuaciones": 1, "nuevas": 0}'),
    ('11001310500820230040101', now() - interval '1 day',   true,  1, '{"actuaciones": 2, "nuevas": 1, "ultima": "Admite recurso y corre traslado para alegar"}'),
    ('05001310502120260011800', now() - interval '3 days',  true,  1, '{"actuaciones": 1, "nuevas": 1, "ultima": "Corre traslado de la contestación"}'),
    ('11001400301520260049800', now() - interval '4 days',  true,  1, '{"actuaciones": 1, "nuevas": 1, "ultima": "Admite tutela y ordena informe a la EPS"}'),
    ('11001333103520260005600', now() - interval '3 hours', false, 0, '{"error": "Tiempo de espera agotado consultando la fuente"}'),
    ('11001333103520260005600', now() - interval '2 hours', false, 0, '{"error": "Tiempo de espera agotado consultando la fuente"}'),
    ('11001333103520260005600', now() - interval '1 hour',  false, 0, '{"error": "Tiempo de espera agotado consultando la fuente"}');

-- Lo que resume cada radicado sale de su historial: última consulta, fecha de la última actuación vista y fallos.
UPDATE vigilancia.radicados_vigilados rv
SET ultima_consulta = c.ultima,
    ultima_actuacion = (SELECT max(fecha) FROM vigilancia.actuaciones_conocidas a WHERE a.radicado = rv.radicado)
FROM (SELECT radicado, max(fecha) AS ultima FROM vigilancia.consultas GROUP BY radicado) c
WHERE c.radicado = rv.radicado;

UPDATE vigilancia.radicados_vigilados
SET fallos_consecutivos = 3, ultimo_error = 'Tiempo de espera agotado consultando la fuente'
WHERE radicado = '11001333103520260005600';
