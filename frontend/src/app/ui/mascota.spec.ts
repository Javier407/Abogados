import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { MascotaComponent, NOMBRE_ASISTENTE } from './mascota.component';
import { EsqueletoComponent } from './esqueleto.component';
import { DashboardComponent } from '../dashboard/dashboard.component';
import { AuthService } from '../core/auth.service';

/** Toga (la mascota), el esqueleto de carga y el resumen del día en el inicio. */
describe('Toga y el inicio', () => {
  it('es decorativa salvo que se le dé una etiqueta, y cada dibujo tiene su propio patrón', () => {
    const a = TestBed.createComponent(MascotaComponent);
    a.detectChanges();
    const svg = a.nativeElement.querySelector('svg') as SVGElement;
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(svg.getAttribute('role')).toBeNull();

    const b = TestBed.createComponent(MascotaComponent);
    b.componentRef.setInput('etiqueta', `${NOMBRE_ASISTENTE}, el asistente`);
    b.componentRef.setInput('animo', 'pensando');
    b.detectChanges();
    const svg2 = b.nativeElement.querySelector('svg') as SVGElement;
    expect(svg2.getAttribute('role')).toBe('img');
    expect(svg2.getAttribute('aria-label')).toBe('Lawy, el asistente');
    expect(svg2.classList).toContain('pensando');
    expect(a.nativeElement.querySelector('pattern').id).not.toBe(b.nativeElement.querySelector('pattern').id);
  });

  it('el esqueleto anuncia que carga y dibuja las filas pedidas', () => {
    const f = TestBed.createComponent(EsqueletoComponent);
    f.componentRef.setInput('texto', 'Cargando procesos…');
    f.componentRef.setInput('cantidadFilas', 3);
    f.componentRef.setInput('cantidadColumnas', 4);
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    expect(el.querySelector('[role="status"]')?.getAttribute('aria-busy')).toBe('true');
    expect(el.textContent).toContain('Cargando procesos…');
    expect(el.querySelectorAll('.fila').length).toBe(3);
    expect(el.querySelectorAll('.skeleton').length).toBe(12);
  });

  describe('resumen del día', () => {
    let http: HttpTestingController;

    function termino(id: number, situacion: string) {
      return { idTermino: id, idProceso: 1, descripcion: `T${id}`, fechaVencimiento: `2026-11-${10 + id}`,
               estado: 'PENDIENTE', situacion, diasHabilesRestantes: 1 };
    }

    function montar(terminos: object[]) {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
          { provide: AuthService, useValue: {
              tiene: () => false, sesion: () => ({ nombre: 'Catalina Gómez' }), idUsuario: () => 52345678,
              esPersonal: () => false, token: () => null } },
        ],
      });
      http = TestBed.inject(HttpTestingController);
      const f = TestBed.createComponent(DashboardComponent);
      f.detectChanges();
      for (const r of http.match(() => true)) {
        r.flush(r.request.url.endsWith('/api/terminos') ? terminos
          : r.request.url.endsWith('/api/notificaciones/no-leidas') ? { cantidad: 0 } : []);
      }
      f.detectChanges();
      return f;
    }

    it('cuenta lo urgente en una frase y reparte los términos en el anillo', () => {
      const c = montar([termino(1, 'VENCIDO'), termino(2, 'POR_VENCER'), termino(3, 'POR_VENCER'), termino(4, 'AL_DIA')])
        .componentInstance;

      expect(c.resumenToga()).toBe('Hoy tiene 1 término vencido y 2 por vencer en 5 días hábiles. Empiece por los vencidos.');
      expect(c.animo()).toBe('neutro');
      const r = c.reparto();
      expect([r.total, r.vencidos, r.porVencer, r.conTiempo]).toEqual([4, 1, 2, 1]);
      expect(r.fondo).toBe('conic-gradient(var(--danger) 0 25%, var(--warn) 25% 75%, var(--ok) 75% 100%)');
    });

    it('sin nada urgente, Toga está contenta', () => {
      const f = montar([]);
      expect(f.componentInstance.resumenToga()).toBe('Todo está al día. Buen momento para adelantar trabajo.');
      expect(f.componentInstance.animo()).toBe('contento');
      expect((f.nativeElement as HTMLElement).querySelector('.anillo')?.getAttribute('aria-label'))
        .toBe('No tiene términos pendientes');
    });
  });
});
