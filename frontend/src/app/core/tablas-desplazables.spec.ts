import { ApplicationInitStatus, provideAppInitializer } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { vigilarTablasDesplazables } from './tablas-desplazables';

/** Una tabla ancha se puede desplazar con el teclado (WCAG 2.1.1); una que cabe no suma paradas de Tab. */
describe('vigilarTablasDesplazables', () => {
  const original = globalThis.ResizeObserver;
  beforeAll(() => {
    // jsdom no trae ResizeObserver ni calcula medidas: un doble mínimo basta.
    globalThis.ResizeObserver ??= class { observe() {} unobserve() {} disconnect() {} } as unknown as typeof ResizeObserver;
  });
  afterAll(() => { globalThis.ResizeObserver = original; });
  afterEach(() => document.body.innerHTML = '');

  function tabla(scroll: number, cliente: number, titulo = 'Honorarios'): HTMLElement {
    const tarjeta = document.createElement('section');
    tarjeta.className = 'card';
    tarjeta.innerHTML = `<h2>${titulo}</h2><div class="tabla-wrapper"><table><tr><td>dato</td></tr></table></div>`;
    document.body.appendChild(tarjeta);
    const w = tarjeta.querySelector<HTMLElement>('.tabla-wrapper')!;
    Object.defineProperty(w, 'scrollWidth', { configurable: true, get: () => scroll });
    Object.defineProperty(w, 'clientWidth', { configurable: true, get: () => cliente });
    return w;
  }

  async function iniciar() {
    TestBed.configureTestingModule({ providers: [provideAppInitializer(vigilarTablasDesplazables)] });
    await TestBed.inject(ApplicationInitStatus).donePromise;
    await Promise.resolve();
  }

  it('la que desborda se vuelve enfocable, con rol y nombre del título', async () => {
    const ancha = tabla(900, 300);
    const cabe = tabla(300, 300, 'Otra');
    await iniciar();
    expect(ancha.getAttribute('tabindex')).toBe('0');
    expect(ancha.getAttribute('role')).toBe('region');
    expect(ancha.getAttribute('aria-label')).toBe('Tabla: Honorarios (desplazable)');
    expect(cabe.hasAttribute('tabindex')).toBe(false);
  });

  it('las tablas que aparecen después también se revisan, y se quita al dejar de desbordar', async () => {
    await iniciar();
    let ancho = 300;
    const w = tabla(900, 300);
    Object.defineProperty(w, 'clientWidth', { configurable: true, get: () => ancho });
    await new Promise(r => setTimeout(r));   // MutationObserver + microtarea
    expect(w.getAttribute('tabindex')).toBe('0');
    ancho = 900;
    dispatchEvent(new Event('resize'));
    await Promise.resolve();
    expect(w.hasAttribute('tabindex')).toBe(false);
    expect(w.hasAttribute('role')).toBe(false);
  });
});
