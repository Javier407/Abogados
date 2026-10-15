-- Cuentas de prueba. TODAS las cuentas usan la contraseña de prueba: Prueba2026*
-- (se guarda con BCrypt mediante pgcrypto; es solo para la base de pruebas).
SET client_encoding = 'UTF8';

INSERT INTO identidad.roles (id_rol, codigo, nombre) VALUES
    (1, 'ADMIN',       'Administrador del sistema'),
    (2, 'SOCIO',       'Socio de la firma'),
    (3, 'ABOGADO',     'Abogado (asociado o junior)'),
    (4, 'DEPENDIENTE', 'Dependiente judicial o paralegal'),
    (5, 'ADMINISTRATIVO', 'Personal administrativo'),
    (6, 'CLIENTE',     'Cliente con acceso a sus procesos');

INSERT INTO identidad.usuarios (id_usuario, usuario, nombre, email, password_hash) VALUES
    (79123456,   'arodriguez', 'Andrés Felipe Rodríguez Mejía',  'arodriguez@rodriguezasociados.co', crypt('Prueba2026*', gen_salt('bf', 10))),
    (52345678,   'cgomez',     'Catalina Gómez Restrepo',        'cgomez@rodriguezasociados.co',     crypt('Prueba2026*', gen_salt('bf', 10))),
    (80234567,   'jtorres',    'Julián Andrés Torres Vargas',    'jtorres@rodriguezasociados.co',    crypt('Prueba2026*', gen_salt('bf', 10))),
    (1020456789, 'lpineda',    'Laura Marcela Pineda Ruiz',      'lpineda@rodriguezasociados.co',    crypt('Prueba2026*', gen_salt('bf', 10))),
    (1037612345, 'shenao',     'Santiago Henao Gil',             'shenao@rodriguezasociados.co',     crypt('Prueba2026*', gen_salt('bf', 10))),
    (43567890,   'dochoa',     'Diana Patricia Ochoa Arango',    'dochoa@rodriguezasociados.co',     crypt('Prueba2026*', gen_salt('bf', 10))),
    (1019087654, 'crojas',     'Camila Andrea Rojas Peña',       'crojas@rodriguezasociados.co',     crypt('Prueba2026*', gen_salt('bf', 10))),
    (1152443210, 'mcardona',   'Mateo Cardona Ríos',             'mcardona@rodriguezasociados.co',   crypt('Prueba2026*', gen_salt('bf', 10))),
    (1023987456, 'vsuarez',    'Valentina Suárez Mora',          'vsuarez@rodriguezasociados.co',    crypt('Prueba2026*', gen_salt('bf', 10))),
    (1001234567, 'scastro',    'Sebastián Castro León',          'scastro@rodriguezasociados.co',    crypt('Prueba2026*', gen_salt('bf', 10))),
    (1036654321, 'imunoz',     'Isabela Muñoz Zapata',           'imunoz@rodriguezasociados.co',     crypt('Prueba2026*', gen_salt('bf', 10))),
    (1018765432, 'pnino',      'Paula Andrea Niño Díaz',         'pnino@rodriguezasociados.co',      crypt('Prueba2026*', gen_salt('bf', 10))),
    (52987654,   'gpardo',     'Gloria Esperanza Pardo Rincón',  'gpardo@rodriguezasociados.co',     crypt('Prueba2026*', gen_salt('bf', 10))),
    (79876543,   'hquintero',  'Hernán Darío Quintero Silva',    'hquintero@rodriguezasociados.co',  crypt('Prueba2026*', gen_salt('bf', 10))),
    -- Dos clientes con acceso de consulta a sus procesos
    (1032456781, 'cmendez',    'Carolina Méndez Ortiz',          'carolina.mendez@correo-ejemplo.co', crypt('Prueba2026*', gen_salt('bf', 10))),
    (900456789,  'andina',     'Inversiones Andina SAS',         'juridico@andina-ejemplo.co',        crypt('Prueba2026*', gen_salt('bf', 10)));

INSERT INTO identidad.usuario_rol (id_usuario, id_rol) VALUES
    (79123456, 1), (79123456, 2),            -- socio fundador, también administrador
    (52345678, 2),
    (80234567, 3), (1020456789, 3), (1037612345, 3), (43567890, 3),
    (1019087654, 3), (1152443210, 3), (1023987456, 3),
    (1001234567, 4), (1036654321, 4), (1018765432, 4),
    (52987654, 5), (79876543, 5),
    (1032456781, 6), (900456789, 6);
