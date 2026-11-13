import { bloquesDe, segmentar, Tabla, Lista, Parrafo } from './respuesta-asistente';

/** Las respuestas del asistente (texto del servidor) se ven en el portal como tablas y con lo urgente resaltado. */
describe('Respuesta del asistente en bloques', () => {
  it('resalta radicados, fechas, vencidos, lo de hoy, lo próximo y los comandos', () => {
    const tipos = segmentar('11001-31-03-032-2026-00501-00 · 16/10/2026 a las 09:00 · VENCIDO · vence hoy · '
      + 'faltan 2 días hábiles · faltan 12 días hábiles · escriba /ayuda')
      .filter(s => s.tipo !== 'texto').map(s => [s.tipo, s.texto]);
    expect(tipos).toEqual([
      ['radicado', '11001-31-03-032-2026-00501-00'], ['fecha', '16/10/2026 a las 09:00'], ['vencido', 'VENCIDO'],
      ['hoy', 'vence hoy'], ['pronto', 'faltan 2 días hábiles'], ['ok', 'faltan 12 días hábiles'], ['comando', '/ayuda'],
    ]);
  });

  it('los procesos salen en tabla, con la fila urgente marcada', () => {
    const b = bloquesDe('Tiene 2 procesos abiertos:\n'
      + '• 11001-31-03-032-2026-00501-00 — Incumplimiento de contrato\n  Verbal, etapa pruebas (a cargo)\n'
      + '  2 término(s), todos VENCIDOS; el más antiguo desde el 16/10/2026\n'
      + '• 11001-40-03-015-2026-00777-00 — Cobro de facturas\n  Ejecutivo, etapa demanda (apoyo)');
    expect((b[0] as Parrafo).titulo).toBe(true);
    const t = b[1] as Tabla;
    expect(t.columnas).toEqual(['Proceso', 'Asunto', 'Tipo y etapa', 'Términos']);
    expect(t.filas.map(f => f.urgencia)).toEqual(['alta', 'normal']);
    expect(t.filas[1].celdas[3]).toEqual([{ texto: '—', tipo: 'texto' }]);
  });

  it('las audiencias, el detalle de un proceso y el equipo también salen en tabla', () => {
    const aud = bloquesDe('Sus audiencias (1):\n• 18/11/2026 a las 09:00 — Audiencia inicial\n'
      + '  11001-31-03-032-2026-00501-00 — Juzgado 32 Civil, sala 4')[1] as Tabla;
    expect(aud.columnas[0]).toBe('Cuándo');
    expect(aud.filas[0].celdas[3].map(s => s.texto).join('')).toBe('Juzgado 32 Civil, sala 4');

    const plazos = bloquesDe('Términos pendientes (1):\n• 10/11/2026 — Subsanar la demanda (vence hoy)')[1] as Tabla;
    expect(plazos.columnas).toEqual(['Vence', 'Qué hay que hacer', 'Estado']);
    expect(plazos.filas[0].urgencia).toBe('media');

    const equipo = bloquesDe('El equipo tiene 2 personas activas:\n'
      + '• Catalina Gómez Restrepo (Socio, Laboral) — 6 procesos abiertos, 4 con términos VENCIDOS\n'
      + '• Julián Andrés Torres Vargas (Abogado asociado) — 1 proceso abierto\n\nEscriba /abogado y el nombre.');
    const tabla = equipo[1] as Tabla;
    expect(tabla.columnas).toEqual(['Persona', 'Cargo y área', 'Procesos']);
    expect(tabla.filas.map(f => f.celdas[0][0].texto)).toEqual(['Catalina Gómez Restrepo', 'Julián Andrés Torres Vargas']);
    expect(tabla.filas[0].urgencia).toBe('alta');
    expect(equipo[2].tipo).toBe('parrafo');
  });

  it('si las viñetas no tienen una forma conocida, queda una lista', () => {
    const b = bloquesDe('Hay 2 personas con ese nombre. ¿Cuál?\n• Julián Andrés Torres Vargas\n• Laura Torres Ríos');
    expect(b[0].tipo).toBe('parrafo');
    expect((b[1] as Lista).items.length).toBe(2);
    expect(bloquesDe('')).toEqual([]);
  });
});
