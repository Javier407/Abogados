import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import axe from 'axe-core';

import { EquipoProcesoComponent } from './equipo-proceso.component';
import { AuthService } from '../../core/auth.service';

/** Pestaña Equipo: cambiar al abogado a cargo con su motivo y ver quién estuvo antes. */
describe('Equipo del proceso', () => {
  let http: HttpTestingController;

  const CATALINA = 52345678;
  const PEDRO = 79123456;

  function montar(cerrado = false) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: AuthService, useValue: { tiene: () => true } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(EquipoProcesoComponent);
    f.componentRef.setInput('idProceso', 34);
    f.componentRef.setInput('cerrado', cerrado);
    f.detectChanges();
    http.expectOne(r => r.url.endsWith('/api/procesos/34/asignaciones')).flush([
      { idProceso: 34, idPersona: CATALINA, rol: 'RESPONSABLE', desde: '2026-03-01', nombre: 'Catalina Gómez' },
    ]);
    http.expectOne(r => r.url.endsWith('/api/procesos/34/asignaciones/historial')).flush([
      { idCambio: 3, idPersona: CATALINA, rol: 'RESPONSABLE', desde: '2026-03-01', nombre: 'Catalina Gómez' },
      { idCambio: 1, idPersona: 1010101010, rol: 'RESPONSABLE', desde: '2025-08-01', hasta: '2026-03-01',
        motivo: 'RETIRO_DE_LA_FIRMA', nota: 'Se fue a otra firma', nombre: 'Diana Ochoa' },
    ]);
    http.expectOne(r => r.url.endsWith('/api/abogados')).flush([
      { idPersona: CATALINA, nombres: 'Catalina', apellidos: 'Gómez', activo: true },
      { idPersona: PEDRO, nombres: 'Pedro', apellidos: 'Rincón', activo: true },
    ]);
    f.detectChanges();
    return f;
  }

  afterEach(() => http.verify());

  it('muestra quién está a cargo y quién estuvo antes con el motivo', () => {
    const el = montar().nativeElement as HTMLElement;

    expect(el.textContent).toContain('Catalina Gómez · A cargo');
    expect(el.textContent).toContain('Quién estuvo antes');
    expect(el.textContent).toContain('Diana Ochoa');
    expect(el.textContent).toContain('Salió por retiro de la firma · Se fue a otra firma');
  });

  it('cambia al abogado a cargo con el motivo y le pasa lo pendiente', async () => {
    const f = montar();
    const c = f.componentInstance;
    const el = f.nativeElement as HTMLElement;
    let cambios = 0;
    c.cambio.subscribe(() => cambios++);

    (Array.from(el.querySelectorAll('button')).find(b => b.textContent?.includes('Cambiar abogado a cargo')) as HTMLButtonElement).click();
    f.detectChanges();
    expect(c.candidatos().map(a => a.idPersona)).toEqual([PEDRO]);       // el que ya está a cargo no sale

    c.relevo = { idResponsable: PEDRO, motivo: 'RENUNCIA', nota: 'Memorial del 10/11', anteriorQuedaDeApoyo: false,
                 trasladarPendientes: true };
    f.detectChanges();
    await f.whenStable();
    (el.querySelector('form.relevo') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const peticion = http.expectOne(r => r.url.endsWith('/api/procesos/34/relevo'));
    expect(peticion.request.body).toEqual({ idResponsable: PEDRO, motivo: 'RENUNCIA', nota: 'Memorial del 10/11',
                                            anteriorQuedaDeApoyo: false, trasladarPendientes: true });
    peticion.flush({ idAnterior: CATALINA, idNuevo: PEDRO, terminosTrasladados: 2, audienciasTrasladadas: 1 });
    http.expectOne(r => r.url.endsWith('/api/procesos/34/asignaciones')).flush([]);
    http.expectOne(r => r.url.endsWith('/api/procesos/34/asignaciones/historial')).flush([]);
    f.detectChanges();

    expect(c.mensaje).toBe('Pedro Rincón quedó a cargo del proceso. Se le pasaron 2 término(s) y 1 audiencia(s). '
      + 'Se avisó a los dos abogados.');
    expect(cambios).toBe(1);
  });

  it('en un proceso cerrado no deja cambiar el equipo', () => {
    const el = montar(true).nativeElement as HTMLElement;

    expect(el.textContent).not.toContain('Cambiar abogado a cargo');
    expect(el.querySelector('select[aria-label="Persona que se agrega"]')).toBeNull();
  });

  it('el formulario del relevo no tiene violaciones graves de accesibilidad', async () => {
    const f = montar();
    f.componentInstance.relevando = true;
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
