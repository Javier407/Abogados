-- Preferencias de aviso del personal y notificaciones generadas a partir de los términos.
SET client_encoding = 'UTF8';

-- Todos los abogados y dependientes reciben avisos en la app y por correo; algunos también por Telegram.
INSERT INTO notificaciones.preferencias (id_persona, canal, destino)
SELECT p.id_persona, c.canal, CASE c.canal WHEN 'EMAIL' THEN p.email END
FROM gabinete.personal p
JOIN gabinete.cargos g ON g.id_cargo = p.id_cargo
CROSS JOIN (VALUES ('APP'), ('EMAIL')) AS c(canal)
WHERE g.es_abogado OR g.codigo IN ('DEPENDIENTE', 'PARALEGAL');

INSERT INTO notificaciones.preferencias (id_persona, canal, destino) VALUES
    (79123456,   'TELEGRAM', '100000001'),   -- chat_id de ejemplo
    (52345678,   'TELEGRAM', '100000002'),
    (1037612345, 'TELEGRAM', '100000003'),
    (1001234567, 'TELEGRAM', '100000004');

-- Una notificación en la app por cada término vencido o por vencer (lo que haría el servicio cada mañana).
INSERT INTO notificaciones.notificaciones (id_persona, canal, evento, titulo, mensaje, referencia, estado, creada)
SELECT t.id_responsable, 'APP',
       CASE t.situacion WHEN 'VENCIDO' THEN 'termino.vencido' ELSE 'termino.por_vencer' END,
       CASE t.situacion WHEN 'VENCIDO' THEN 'Término vencido' ELSE 'Término por vencer' END,
       t.descripcion || ' — proceso ' || p.radicado || ' — vence ' || to_char(t.fecha_vencimiento, 'DD/MM/YYYY') ||
       CASE t.situacion WHEN 'VENCIDO' THEN ' (vencido)'
                        ELSE ' (' || t.dias_habiles_restantes || ' días hábiles)' END,
       jsonb_build_object('id_proceso', t.id_proceso, 'id_termino', t.id_termino),
       'PENDIENTE', now() - interval '2 hours'
FROM procesos.v_terminos t
JOIN procesos.procesos p ON p.id_proceso = t.id_proceso
WHERE t.situacion IN ('VENCIDO', 'POR_VENCER');

-- Avisos ya enviados por correo y Telegram sobre actuaciones detectadas.
INSERT INTO notificaciones.notificaciones (id_persona, canal, evento, titulo, mensaje, referencia, estado, creada, enviada) VALUES
    (52345678, 'EMAIL',    'actuacion.detectada', 'Nuevo auto en un proceso',
     'Juzgado 008 Laboral de Bogotá — 11001310500820250051200: Niega prueba testimonial',
     '{"id_proceso": 12}', 'ENVIADA', now() - interval '2 days', now() - interval '2 days'),
    (52345678, 'TELEGRAM', 'actuacion.detectada', 'Nuevo auto en un proceso',
     'Tribunal Superior de Bogotá, Sala Laboral — 11001310500820230040101: Admite recurso y corre traslado para alegar',
     '{"id_proceso": 16}', 'ENVIADA', now() - interval '1 day', now() - interval '1 day'),
    (1023987456, 'EMAIL',  'actuacion.detectada', 'Nuevo auto en un proceso',
     'Juzgado 015 Civil Municipal de Bogotá — 11001400301520260049800: Admite tutela y ordena informe a la EPS',
     '{"id_proceso": 29}', 'LEIDA', now() - interval '4 days', now() - interval '4 days'),
    (1020456789, 'EMAIL',  'actuacion.detectada', 'Falla en la vigilancia',
     'No se pudo consultar el radicado 11001333103520260005600 en tres intentos seguidos',
     '{"id_proceso": 26}', 'FALLIDA', now() - interval '1 hour', NULL);
