import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Type } from '@angular/core';
import axe from 'axe-core';

import { LoginComponent } from './pages/login/login.component';
import { CalendarioComponent } from './pages/calendario/calendario.component';
import { AsistenteProcesoComponent } from './pages/procesos/asistente-proceso.component';

/**
 * F16: accesibilidad automática con axe-core, el motor de Axe DevTools (Deque). Falla si una pantalla tiene
 * violaciones graves o críticas de WCAG 2.1 A/AA. El contraste no se puede medir en jsdom (no pinta): ese se
 * revisa con la auditoría en el navegador (docs/10-accesibilidad.md).
 */
async function violaciones<T>(componente: Type<T>, entradas: Record<string, unknown> = {}) {
  await TestBed.configureTestingModule({
    imports: [componente],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
  }).compileComponents();
  const f = TestBed.createComponent(componente);
  for (const [k, v] of Object.entries(entradas)) f.componentRef.setInput(k, v);
  f.detectChanges();
  document.body.appendChild(f.nativeElement);
  const r = await axe.run(f.nativeElement, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    rules: { 'color-contrast': { enabled: false } }
  });
  f.nativeElement.remove();
  return r.violations
    .filter(v => v.impact === 'serious' || v.impact === 'critical')
    .map(v => `${v.id}: ${v.help} (${v.nodes.length})`);
}

describe('Accesibilidad (axe-core)', () => {
  it('detecta una violación conocida (la prueba no da verde en falso)', async () => {
    const malo = document.createElement('div');
    malo.innerHTML = '<input type="text"><img src="x.png">';
    document.body.appendChild(malo);
    const r = await axe.run(malo, { runOnly: { type: 'tag', values: ['wcag2a'] } });
    malo.remove();
    expect(r.violations.map(v => v.id)).toEqual(expect.arrayContaining(['label', 'image-alt']));
  });

  it('el inicio de sesión no tiene violaciones graves', async () => {
    expect(await violaciones(LoginComponent)).toEqual([]);
  });

  it('el asistente de borradores no tiene violaciones graves', async () => {
    expect(await violaciones(AsistenteProcesoComponent, { idProceso: 1, radicado: '11001-31-03-032-2024-00458-00' }))
      .toEqual([]);
  });

  it('el calendario no tiene violaciones graves (F16: antes role=grid sin filas)', async () => {
    expect(await violaciones(CalendarioComponent)).toEqual([]);
  });
});
