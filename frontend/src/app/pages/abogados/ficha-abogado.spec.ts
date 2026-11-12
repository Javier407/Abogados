import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import axe from 'axe-core';

import { FichaAbogadoComponent } from './ficha-abogado.component';

/** La ficha del abogado: qué casos tiene, con qué papel, de qué tipo y cuánto tiempo le queda para responder. */
describe('Ficha del abogado', () => {
  let http: HttpTestingController;

  const ABOGADA = {
    idPersona: 52345678, nombres: 'Catalina', apellidos: 'Gómez', email: 'cgomez@firma.test', idCargo: 2,
    cargo: 'Abogada asociada', idSede: 1, sede: 'Bogotá', area: 'Civil', tarjetaProfesional: '201345', activo: true,
  };

  function proceso(id: number, tipo: string, rol: string, estado = 'ACTIVO', pendientes = 0) {
    return {
      idProceso: id, radicadoLegible: `11001-31-03-032-2026-0050${id}-00`, asunto: `Asunto ${id}`, codigoRama: 'CIVIL',
      rama: 'Civil y comercial', tipoProceso: tipo, etapa: 'PRUEBAS', estado, rol, desde: '2026-09-01',
      terminosPendientes: pendientes, proximoVencimiento: pendientes ? '2026-11-20' : null,
    };
  }

  function termino(id: number, restantes: number, estado = 'PENDIENTE') {
    return {
      idTermino: id, idProceso: 1, radicado: '11001310303220260050100', radicadoLegible: '11001-31-03-032-2026-00501-00',
      rama: 'Civil y comercial', asunto: 'Asunto 1', descripcion: `Término ${id}`, fechaInicio: '2026-11-01',
      diasHabiles: 20, fechaVencimiento: `2026-11-${String(10 + id).padStart(2, '0')}`, estado, idResponsable: 52345678,
      diasHabilesRestantes: restantes, situacion: restantes < 0 ? 'VENCIDO' : restantes <= 5 ? 'POR_VENCER' : 'AL_DIA',
    };
  }

  function montar(abogada: object = ABOGADA) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '52345678' })) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(FichaAbogadoComponent);
    f.detectChanges();
    http.expectOne(r => r.url.endsWith('/api/abogados')).flush([abogada]);
    http.expectOne(r => r.url.endsWith('/api/procesos/de-abogado/52345678')).flush([
      proceso(1, 'Verbal', 'RESPONSABLE', 'ACTIVO', 2), proceso(2, 'Verbal', 'APOYO'),
      proceso(3, 'Ejecutivo', 'RESPONSABLE', 'SUSPENDIDO'), proceso(4, 'Ejecutivo', 'RESPONSABLE', 'TERMINADO'),
    ]);
    const terminos = http.expectOne(r => r.url.endsWith('/api/terminos'));
    expect(terminos.request.params.get('idResponsable')).toBe('52345678');
    terminos.flush([termino(1, -2), termino(2, 0), termino(3, 5), termino(4, 6), termino(5, 15), termino(6, 40),
      termino(7, 3, 'CUMPLIDO')]);
    const audiencias = http.expectOne(r => r.url.endsWith('/api/audiencias'));
    expect(audiencias.request.params.get('idAsistente')).toBe('52345678');
    audiencias.flush([{ idActuacion: 9, idProceso: 1, radicado: 'x', radicadoLegible: 'x', asunto: 'Asunto 1',
      fecha: '2026-11-25', descripcion: 'Audiencia inicial' }]);
    f.detectChanges();
    return f;
  }

  afterEach(() => http.verify());

  it('cuenta los procesos abiertos por papel y deja aparte los cerrados', () => {
    const c = montar().componentInstance;

    expect(c.aCargo().map(p => p.idProceso)).toEqual([1, 3]);
    expect(c.enApoyo().map(p => p.idProceso)).toEqual([2]);
    expect(c.cerrados().map(p => p.idProceso)).toEqual([4]);
    expect(c.porTipo()).toEqual([
      { tipo: 'Verbal', rama: 'Civil y comercial', total: 2, aCargo: 1 },
      { tipo: 'Ejecutivo', rama: 'Civil y comercial', total: 1, aCargo: 1 },   // el terminado no cuenta
    ]);
  });

  it('reparte los términos pendientes en el rango de respuesta', () => {
    const c = montar().componentInstance;

    // -2 | 0 y 5 | 6 y 15 | 40. El cumplido no cuenta.
    expect(c.rangos().map(r => [r.id, r.total])).toEqual([
      ['vencidos', 1], ['urgentes', 2], ['proximos', 2], ['holgados', 1],
    ]);
    expect(c.pendientes().length).toBe(6);
    expect(c.pendientes()[0].descripcion).toBe('Término 1');   // el más próximo primero
  });

  it('muestra la ficha, filtra la tabla y no tiene violaciones graves de accesibilidad', async () => {
    const f = montar();
    const el = f.nativeElement as HTMLElement;

    expect(el.querySelector('h1')?.textContent).toContain('Catalina Gómez');
    expect(el.textContent).toContain('Rango de respuesta');
    expect(el.textContent).toContain('De hoy a 5 días hábiles');
    expect(el.querySelectorAll('.tabla-ficha tbody tr').length).toBe(3);   // abiertos

    const cerrados = [...el.querySelectorAll('button')].find(b => b.textContent?.includes('Cerrados')) as HTMLButtonElement;
    cerrados.click();
    f.detectChanges();
    expect(el.querySelectorAll('.tabla-ficha tbody tr').length).toBe(1);
    expect(cerrados.getAttribute('aria-pressed')).toBe('true');

    document.body.appendChild(el);
    const r = await axe.run(el, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
      rules: { 'color-contrast': { enabled: false } },
    });
    el.remove();
    expect(r.violations.filter(v => v.impact === 'serious' || v.impact === 'critical').map(v => v.id)).toEqual([]);
  });

  it('avisa si un abogado inactivo sigue a cargo de procesos abiertos', () => {
    const el = montar({ ...ABOGADA, activo: false }).nativeElement as HTMLElement;

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('sigue a cargo de 2 proceso(s)');
  });
});
