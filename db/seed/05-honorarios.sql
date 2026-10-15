-- Honorarios pactados por proceso y pagos recibidos.
SET client_encoding = 'UTF8';

INSERT INTO gabinete.honorarios_pactados
    (id_honorario, id_proceso, id_cliente, id_tarifa, forma_cobro, valor_fijo, porcentaje, valor_estimado, fecha_pacto, estado)
VALUES
    ( 1,  1,  1,    5, 'PORCENTAJE_CUANTIA', NULL,      15.00,  12750000, '2025-03-01', 'VIGENTE'),
    ( 2,  2,  2, NULL, 'FIJO',               35000000,  NULL,   35000000, '2024-07-12', 'VIGENTE'),
    ( 3,  3,  3,    7, 'MIXTO',              3501810,   15.00,   7821810, '2026-04-28', 'VIGENTE'),
    ( 4,  4,  4,    4, 'PORCENTAJE_CUANTIA', NULL,      20.00,   3700000, '2026-08-20', 'VIGENTE'),
    ( 5,  5,  5,    6, 'PORCENTAJE_CUANTIA', NULL,      10.00,  41000000, '2023-10-25', 'VIGENTE'),
    ( 6,  7,  6,   10, 'FIJO',               8754525,   NULL,    8754525, '2025-06-15', 'VIGENTE'),
    ( 7,  8,  7,   13, 'PORCENTAJE_CUANTIA', NULL,      20.00,   2880000, '2026-08-25', 'VIGENTE'),
    ( 8, 11, 10,   11, 'FIJO',               5252715,   NULL,    5252715, '2026-02-18', 'CUMPLIDO'),
    ( 9, 12, 11,   14, 'CUOTA_LITIS',        NULL,      30.00,  28800000, '2025-04-30', 'VIGENTE'),
    (10, 13, 12,   15, 'FIJO',               7000000,   NULL,    7000000, '2026-01-14', 'VIGENTE'),
    (11, 14, 13,   14, 'CUOTA_LITIS',        NULL,      25.00,   3000000, '2026-08-01', 'VIGENTE'),
    (12, 17, 16,   22, 'MIXTO',              1750905,   25.00,  46750905, '2025-05-15', 'VIGENTE'),
    (13, 19, 18,   22, 'MIXTO',              1423500,   25.00,  16423500, '2024-08-12', 'CUMPLIDO'),
    (14, 22, 21,   17, 'FIJO',               12000000,  NULL,   12000000, '2025-08-05', 'VIGENTE'),
    (15, 23, 22,   17, 'FIJO',               8754525,   NULL,    8754525, '2025-10-20', 'CUMPLIDO'),
    (16, 25, 24,   23, 'MIXTO',              5252715,   15.00,  39752715, '2025-07-28', 'VIGENTE'),
    (17, 26, 25,   23, 'MIXTO',              5252715,   15.00, 185252715, '2026-08-18', 'VIGENTE'),
    (18, 28, 27, NULL, 'FIJO',               60000000,  NULL,   60000000, '2025-09-15', 'VIGENTE'),
    (19, 29, 28,   24, 'FIJO',               1750905,   NULL,    1750905, '2026-10-05', 'VIGENTE'),
    (20, 30, 29,   25, 'FIJO',               7003620,   NULL,    7003620, '2025-11-10', 'VIGENTE');

-- Pagos: algunos honorarios están al día, otros con saldo pendiente.
INSERT INTO gabinete.pagos (id_honorario, fecha, valor, medio, referencia) VALUES
    ( 1, '2025-03-05',  4000000, 'TRANSFERENCIA', 'TRF-250305-01'),
    ( 2, '2024-07-15', 10000000, 'TRANSFERENCIA', 'TRF-240715-02'),
    ( 2, '2025-01-15', 10000000, 'TRANSFERENCIA', 'TRF-250115-01'),
    ( 2, '2025-07-15',  7500000, 'TRANSFERENCIA', 'TRF-250715-03'),
    ( 3, '2026-05-02',  3501810, 'CONSIGNACION',  'CON-260502-11'),
    ( 5, '2023-11-01',  5000000, 'TRANSFERENCIA', 'TRF-231101-07'),
    ( 6, '2025-06-20',  4377262, 'TARJETA',       'TAR-250620-04'),
    ( 6, '2025-12-20',  2000000, 'TARJETA',       'TAR-251220-02'),
    ( 8, '2026-02-20',  2626357, 'TRANSFERENCIA', 'TRF-260220-05'),
    ( 8, '2026-04-25',  2626358, 'TRANSFERENCIA', 'TRF-260425-01'),
    (10, '2026-01-20',  3500000, 'TRANSFERENCIA', 'TRF-260120-09'),
    (12, '2025-05-20',  1750905, 'EFECTIVO',      'REC-0412'),
    (13, '2024-08-20',  1423500, 'EFECTIVO',      'REC-0288'),
    (13, '2025-11-28', 15000000, 'TRANSFERENCIA', 'TRF-251128-02'),
    (14, '2025-08-10',  6000000, 'TRANSFERENCIA', 'TRF-250810-06'),
    (15, '2025-10-25',  8754525, 'TRANSFERENCIA', 'TRF-251025-03'),
    (16, '2025-08-01',  5252715, 'TRANSFERENCIA', 'TRF-250801-08'),
    (17, '2026-08-25',  2500000, 'TRANSFERENCIA', 'TRF-260825-04'),
    (18, '2025-09-20', 20000000, 'TRANSFERENCIA', 'TRF-250920-01'),
    (18, '2026-01-20', 20000000, 'TRANSFERENCIA', 'TRF-260120-02'),
    (19, '2026-10-06',  1750905, 'TARJETA',       'TAR-261006-01'),
    (20, '2025-11-15',  3500000, 'CONSIGNACION',  'CON-251115-03');
