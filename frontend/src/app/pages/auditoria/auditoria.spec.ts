import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuditoriaComponent } from './auditoria.component';

/** La pantalla de auditoría: muestra quién hizo qué, pagina hacia atrás y avisa si la cadena no cuadra. */
describe('Auditoría', () => {
  let http: HttpTestingController;

  function registro(id: number, usuario: string, estado: number | null) {
    return {
      id, hashAnterior: '0'.repeat(64), hash: String(id).repeat(64).slice(0, 64),
      acceso: { momento: '2026-11-08T15:04:05Z', usuario, roles: 'ABOGADO', metodo: 'DELETE', ruta: `/api/gastos/${id}`,
                estado, duracionMs: 3, ip: '100.64.0.7' }
    };
  }

  function montar() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(AuditoriaComponent);
    f.detectChanges();
    http.expectOne(r => r.url.endsWith('/api/usuarios')).flush([
      { idUsuario: 79123456, usuario: 'afr', nombre: 'Andrés Rodríguez', email: 'a@f.test', activo: true,
        roles: ['ABOGADO'], debeCambiarClave: false }
    ]);
    return f;
  }

  afterEach(() => http.verify());

  it('muestra a nombre de quién, qué hizo y el resultado, y pide los más antiguos con «Ver más»', () => {
    const f = montar();
    const primera = http.expectOne(r => r.url.endsWith('/api/auditoria/registros'));
    expect(primera.request.params.get('antesDe')).toBeNull();
    primera.flush({ registros: [registro(51, '79123456', 403), registro(50, 'anonimo', null)], siguiente: 50 });
    f.detectChanges();

    const texto = f.nativeElement.textContent as string;
    expect(texto).toContain('Andrés Rodríguez (79123456)');
    expect(texto).toContain('Borró');
    expect(texto).toContain('Negada');
    expect(texto).toContain('Sin sesión');
    expect(texto).toContain('Cortada');

    const verMas = [...f.nativeElement.querySelectorAll('button')]
      .find((b: HTMLButtonElement) => b.textContent?.includes('Ver más')) as HTMLButtonElement;
    verMas.click();
    const segunda = http.expectOne(r => r.url.endsWith('/api/auditoria/registros'));
    expect(segunda.request.params.get('antesDe')).toBe('50');
    segunda.flush({ registros: [registro(49, '79123456', 200)], siguiente: null });
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('tbody tr').length).toBe(3);
  });

  it('si la cadena no cuadra lo dice con el registro y el motivo', () => {
    const f = montar();
    http.expectOne(r => r.url.endsWith('/api/auditoria/registros')).flush({ registros: [], siguiente: null });
    f.componentInstance.revisar();
    http.expectOne(r => r.url.endsWith('/api/auditoria/verificacion')).flush({
      cadena: { integra: false, revisados: 41, ultimoId: 41, ultimoHash: 'a'.repeat(64), roturaEn: 42,
                motivo: 'El contenido del registro 42 cambió después de guardarlo' },
      escritura: { almacen: 'postgres', pendientes: 0, perdidos: 0 },
      momento: '2026-11-08T16:00:00Z'
    });
    f.detectChanges();
    const texto = f.nativeElement.textContent as string;
    expect(texto).toContain('La cadena no cuadra desde el registro 42');
    expect(texto).toContain('cambió después de guardarlo');
  });

  it('revisar desde un sello exige el número y un hash de 64 caracteres', () => {
    const f = montar();
    http.expectOne(r => r.url.endsWith('/api/auditoria/registros')).flush({ registros: [], siguiente: null });
    const c = f.componentInstance;
    c.selloId = 7;
    c.selloHash = 'corto';
    c.revisarSello();
    expect(c.error()).toContain('64 caracteres');
    http.expectNone(r => r.url.endsWith('/api/auditoria/verificacion'));

    c.selloHash = 'AB'.repeat(32);
    c.revisarSello();
    const r = http.expectOne(x => x.url.endsWith('/api/auditoria/verificacion'));
    expect(r.request.params.get('desde')).toBe('7');
    expect(r.request.params.get('hash')).toBe('ab'.repeat(32));
    r.flush({
      cadena: { integra: true, revisados: 3, ultimoId: 10, ultimoHash: 'c'.repeat(64), roturaEn: null, motivo: null },
      escritura: { almacen: 'postgres', pendientes: 0, perdidos: 0 }, momento: '2026-11-08T16:00:00Z'
    });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('3 registro(s) revisado(s) desde el sello');
  });
});
