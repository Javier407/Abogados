import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import axe from 'axe-core';

import { PreguntaTogaComponent } from './pregunta-toga.component';

/** «Pregúntale a Toga» en el portal: abre, pregunta, muestra la respuesta del servidor y se cierra con Escape. */
describe('Pregúntale a Toga', () => {
  let http: HttpTestingController;

  function montar() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(PreguntaTogaComponent);
    f.detectChanges();
    return f;
  }

  afterEach(() => http.verify());

  it('abre la conversación, pregunta con una sugerencia y muestra la respuesta', () => {
    const f = montar();
    const el = f.nativeElement as HTMLElement;
    const lanzador = el.querySelector('button.lanzador') as HTMLButtonElement;
    expect(lanzador.getAttribute('aria-label')).toBe('Pregúntale a Toga');

    lanzador.click();
    f.detectChanges();
    expect(el.querySelector('[role="dialog"]')).not.toBeNull();

    (Array.from(el.querySelectorAll('.sugerencias button')).find(b => b.textContent?.includes('términos')) as HTMLButtonElement).click();
    f.detectChanges();
    const peticion = http.expectOne(r => r.url.endsWith('/api/notificaciones/toga'));
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ texto: '¿Qué términos me vencen?' });
    expect(el.textContent).toContain('Toga está revisando');

    peticion.flush({ respuesta: 'Tiene 2 término(s) pendiente(s):\n• 14/11/2026 — Aportar dictamen' });
    f.detectChanges();
    const mensajes = Array.from(el.querySelectorAll('.mensaje')).map(m => m.textContent?.trim());
    expect(mensajes).toEqual(['¿Qué términos me vencen?', 'Tiene 2 término(s) pendiente(s):\n• 14/11/2026 — Aportar dictamen']);
    expect(el.querySelector('.mensaje.mio')?.textContent).toContain('¿Qué términos');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    f.detectChanges();
    expect(el.querySelector('[role="dialog"]')).toBeNull();
  });

  it('si el servidor falla, lo dice en la conversación sin romperse', () => {
    const f = montar();
    const c = f.componentInstance;
    c.abrir();
    c.preguntar('mis procesos');
    http.expectOne(r => r.url.endsWith('/api/notificaciones/toga')).flush({ error: 'Toga no está disponible' },
      { status: 503, statusText: 'Service Unavailable' });
    f.detectChanges();
    expect(c.pensando()).toBe(false);
    expect(c.mensajes().at(-1)?.de).toBe('toga');
    c.preguntar('   ');                                  // vacío: no pregunta
    expect(c.mensajes().length).toBe(2);
  });

  it('la conversación abierta no tiene violaciones graves de accesibilidad', async () => {
    const f = montar();
    f.componentInstance.abrir();
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    document.body.appendChild(el);
    const r = await axe.run(el, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
      rules: { 'color-contrast': { enabled: false } },
    });
    el.remove();
    expect(r.violations.filter(v => v.impact === 'serious' || v.impact === 'critical').map(v => v.id)).toEqual([]);
  });
});
