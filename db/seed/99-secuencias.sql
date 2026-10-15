-- Ajusta las secuencias de identidad al mayor id insertado, para que los nuevos registros no choquen.
SET client_encoding = 'UTF8';

SELECT setval(pg_get_serial_sequence('gabinete.clientes',            'id_cliente'),   (SELECT max(id_cliente)   FROM gabinete.clientes));
SELECT setval(pg_get_serial_sequence('gabinete.peritos',             'id_perito'),    (SELECT max(id_perito)    FROM gabinete.peritos));
SELECT setval(pg_get_serial_sequence('gabinete.honorarios_pactados', 'id_honorario'), (SELECT max(id_honorario) FROM gabinete.honorarios_pactados));
SELECT setval(pg_get_serial_sequence('procesos.procesos',            'id_proceso'),   (SELECT max(id_proceso)   FROM procesos.procesos));

ANALYZE;
