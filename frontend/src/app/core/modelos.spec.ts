import { avancePlazo, diasNoContados, textoRestantes } from './modelos';

/** Las cuentas que la pantalla de términos le muestra a quien no conoce el sistema. */
describe('términos: cómo se contó y cuánto queda', () => {
  it('cuenta los días del calendario que no fueron hábiles', () => {
    // Notificado el jueves 5/11/2026 con 5 días hábiles: vence el jueves 12/11 (no cuentan sábado ni domingo).
    expect(
      diasNoContados({ fechaInicio: '2026-11-05', fechaVencimiento: '2026-11-12', diasHabiles: 5 }),
    ).toBe(2);
    // 20 días hábiles con dos fines de semana largos por festivo: 30 días corridos, 10 no contaron.
    expect(
      diasNoContados({ fechaInicio: '2026-10-30', fechaVencimiento: '2026-11-29', diasHabiles: 20 }),
    ).toBe(10);
    // Nunca negativo, aunque los datos vengan raros.
    expect(
      diasNoContados({ fechaInicio: '2026-11-05', fechaVencimiento: '2026-11-06', diasHabiles: 3 }),
    ).toBe(0);
  });

  it('dice qué parte del plazo se ha consumido', () => {
    const t = (diasHabiles: number, diasHabilesRestantes: number, estado = 'PENDIENTE') => ({
      estado,
      diasHabiles,
      diasHabilesRestantes,
    });
    expect(avancePlazo(t(20, 20))).toBe(0); // recién notificado
    expect(avancePlazo(t(20, 5))).toBe(75);
    expect(avancePlazo(t(3, 0))).toBe(100); // vence hoy
    expect(avancePlazo(t(10, -4))).toBe(100); // vencido: no pasa de 100
    expect(avancePlazo(t(10, 8, 'CUMPLIDO'))).toBe(100);
  });

  it('describe lo que falta en palabras', () => {
    expect(textoRestantes(0)).toBe('vence hoy');
    expect(textoRestantes(1)).toBe('falta 1 día hábil');
    expect(textoRestantes(3)).toBe('faltan 3 días hábiles');
    expect(textoRestantes(-2)).toBe('venció hace 2 días hábiles');
  });
});
