import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import axe from 'axe-core';

import { VigilanciaComponent } from './vigilancia.component';

/** Vigilancia: además de la salud de la consulta, muestra cómo va cada proceso y lo que pasó en el juzgado. */
describe('Vigilancia', () => {
  let http: HttpTestingController;

  const RAD = '11001310303220260050100';
  const hace = (dias: number) => new Date(Date.now() - dias * 86_400_000).toISOString().slice(0, 10);

  function montar() {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(VigilanciaComponent);
    f.detectChanges();
    http.expectOne(r => r.url.endsWith('/api/vigilancia/fuentes')).flush({ disponibles: ['MANUAL', 'SIMULADA'] });
    http.expectOne(r => r.url.endsWith('/api/procesos')).flush([
      { idProceso: 34, asunto: 'Incumplimiento de contrato', etapa: 'PRUEBAS', estado: 'ACTIVO' },
    ]);
    http.expectOne(r => r.url.endsWith('/api/terminos')).flush([
      { idTermino: 2, idProceso: 34, descripcion: 'Aportar dictamen', fechaVencimiento: '2026-11-30', estado: 'PENDIENTE',
        diasHabilesRestantes: 12 },
      { idTermino: 1, idProceso: 34, descripcion: 'Pronunciarse sobre excepciones', fechaVencimiento: '2026-11-14',
        estado: 'PENDIENTE', diasHabilesRestantes: 2 },
      { idTermino: 3, idProceso: 34, descripcion: 'Ya hecho', fechaVencimiento: '2026-11-01', estado: 'CUMPLIDO',
        diasHabilesRestantes: -5 },
    ]);
    http.expectOne(r => r.url.endsWith('/api/vigilancia/radicados')).flush([
      { radicado: RAD, radicadoLegible: '11001-31-03-032-2026-00501-00', idProceso: 34, fuente: 'SIMULADA',
        frecuenciaHoras: 12, activo: true, ultimaConsulta: '2026-11-11T13:00:00Z', fallosConsecutivos: 0, enAlerta: false,
        despacho: 'Juzgado 32 Civil', ultimaActuacion: hace(2), ultimoError: null, lineaBaseHecha: true },
    ]);
    f.detectChanges();
    return f;
  }

  afterEach(() => http.verify());

  it('dice cómo va el proceso: etapa, novedad y lo próximo que vence', () => {
    const el = montar().nativeElement as HTMLElement;
    const fila = el.querySelector('tbody tr')!.textContent!;

    expect(fila).toContain('Incumplimiento de contrato');
    expect(fila).toContain('Pruebas');
    expect(fila).toContain('Novedad');
    expect(fila).toContain('Pronunciarse sobre excepciones');   // el más próximo, no el cumplido
    expect(fila).toContain('faltan 2 días hábiles');
    expect(fila).toContain('y 1 más');
    expect(fila).toContain('Al día');
  });

  it('en el panel muestra lo que pasó en el juzgado, también lo que ya estaba', async () => {
    const f = montar();
    const el = f.nativeElement as HTMLElement;
    (el.querySelector('tbody button.enlace') as HTMLButtonElement).click();

    const act = http.expectOne(r => r.url.endsWith(`/api/vigilancia/radicados/${RAD}/actuaciones`));
    act.flush([
      { fecha: '2026-11-10', actuacion: 'Auto corre traslado de excepciones', anotacion: 'Por 5 días', nueva: true,
        vista: '2026-11-10T13:00:00Z' },
      { fecha: '2026-03-02', actuacion: 'Auto admite demanda', anotacion: null, nueva: false, vista: '2026-03-05T13:00:00Z' },
    ]);
    http.expectOne(r => r.url.endsWith(`/api/vigilancia/radicados/${RAD}/consultas`)).flush([]);
    http.expectOne(r => r.url.endsWith('/api/procesos/34/providencias')).flush([]);
    f.detectChanges();

    const panel = el.querySelector('aside')!.textContent!;
    expect(panel).toContain('Cómo va el proceso');
    expect(panel).toContain('Etapa: Pruebas');
    expect(panel).toContain('Auto corre traslado de excepciones');
    expect(panel).toContain('Auto admite demanda');
    expect(panel).toContain('ya estaba al empezar a vigilar');

    document.body.appendChild(el);
    const r = await axe.run(el, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
      rules: { 'color-contrast': { enabled: false } },
    });
    el.remove();
    expect(r.violations.filter(v => v.impact === 'serious' || v.impact === 'critical').map(v => v.id)).toEqual([]);
  });
});
