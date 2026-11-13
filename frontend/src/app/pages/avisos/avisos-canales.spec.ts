import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AvisosComponent } from './avisos.component';
import { SesionService } from '../../core/sesion.service';
import { AuthService } from '../../core/auth.service';

const YO = 52345678;
const CANALES = {
  configurado: true,
  canales: [
    { canal: 'WHATSAPP', nombre: 'WhatsApp', vinculado: false },
    { canal: 'INSTAGRAM', nombre: 'Instagram', vinculado: true },
  ],
};

/** Avisos → Lawy por WhatsApp e Instagram: genera el código para escribir en el chat y desvincula. */
describe('Lawy por WhatsApp e Instagram en Avisos', () => {
  let http: HttpTestingController;

  function montar(esCliente = false) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: SesionService, useValue: { idPersona: signal(YO), noLeidos: signal(0), persona: signal({ nombres: 'Catalina' }), refrescarNoLeidos: () => undefined } },
        { provide: AuthService, useValue: { esCliente: signal(esCliente) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(AvisosComponent);
    f.detectChanges();
    http.expectOne(r => r.url === '/api/notificaciones').flush([]);
    http.expectOne(r => r.url === '/api/notificaciones/telegram').flush({ configurado: false, vinculado: false, bot: null });
    return f;
  }

  afterEach(() => http.verify());

  it('muestra cada canal y genera el mensaje «vincular <código>»', () => {
    const f = montar();
    http.expectOne(r => r.url === '/api/notificaciones/canales' && r.params.get('idPersona') === String(YO)).flush(CANALES);
    f.detectChanges();
    const el = f.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Lawy por WhatsApp e Instagram');
    const filas = Array.from(el.querySelectorAll('.lista-canales li')).map(li =>
      [li.querySelector('span')?.textContent?.trim(), li.querySelector('button')?.textContent?.trim()]);
    expect(filas).toEqual([['WhatsApp: sin vincular', 'Vincular WhatsApp'], ['Instagram: vinculado', 'Desvincular']]);

    (Array.from(el.querySelectorAll('.lista-canales button')).find(b => b.textContent?.includes('Vincular WhatsApp')) as HTMLButtonElement).click();
    const p = http.expectOne(r => r.url === '/api/notificaciones/canales/whatsapp/vinculo');
    expect(p.request.method).toBe('POST');
    p.flush({ canal: 'WHATSAPP', nombre: 'WhatsApp', codigo: 'K7P2M9QX', mensaje: 'vincular K7P2M9QX' });
    f.detectChanges();
    expect(el.querySelector('.codigo-vinculo')?.textContent).toBe('vincular K7P2M9QX');
    expect(el.textContent).toContain('solo en WhatsApp');
  });

  it('desvincula tras confirmar y vuelve a cargar el estado', () => {
    const f = montar();
    http.expectOne('/api/notificaciones/canales?idPersona=' + YO).flush(CANALES);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    f.componentInstance.desvincularCanal(CANALES.canales[1] as never);
    const d = http.expectOne(r => r.url === '/api/notificaciones/canales/instagram');
    expect(d.request.method).toBe('DELETE');
    d.flush({ desvinculado: true });
    http.expectOne(r => r.url === '/api/notificaciones/canales').flush({ ...CANALES, canales: CANALES.canales.map(c => ({ ...c, vinculado: false })) });
  });

  it('sin la clave de Talki en el servidor no muestra la tarjeta', () => {
    const f = montar();
    http.expectOne(r => r.url === '/api/notificaciones/canales').flush({ ...CANALES, configurado: false });
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).textContent).not.toContain('Lawy por WhatsApp');
  });

  it('a un cliente no le ofrece los canales (Toga no le responde datos)', () => {
    const f = montar(true);
    http.expectNone(r => r.url === '/api/notificaciones/canales');
    expect(f.componentInstance.canales).toBeNull();
  });
});
