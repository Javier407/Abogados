import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { autenticacion } from './auth.interceptor';
import { AuthService } from './auth.service';

const SESION = {
  token: 'eyJ.abc.def', expira: new Date(Date.now() + 3_600_000).toISOString(), idUsuario: 79123456,
  usuario: 'arodriguez', nombre: 'Andrés Felipe Rodríguez Mejía', roles: ['SOCIO']
};

describe('Autenticación en el frontend', () => {
  let http: HttpClient;
  let api: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([autenticacion])), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpClient);
    api = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  function iniciar(sesion = SESION) {
    auth.iniciar('arodriguez', 'Prueba2026*').subscribe();
    api.expectOne('/api/auth/login').flush(sesion);
  }

  it('guarda la sesión y agrega el token a las llamadas a la API', () => {
    iniciar();
    http.get('/api/procesos').subscribe();
    const req = api.expectOne('/api/procesos');
    expect(req.request.headers.get('Authorization')).toBe('Bearer eyJ.abc.def');
    expect(auth.tiene('SOCIO')).toBe(true);
    expect(auth.esPersonal()).toBe(true);
  });

  it('no manda el token al login ni a otros sitios', () => {
    iniciar();
    http.get('https://otro-sitio.test/datos').subscribe();
    expect(api.expectOne('https://otro-sitio.test/datos').request.headers.has('Authorization')).toBe(false);
  });

  it('un 401 cierra la sesión', () => {
    iniciar();
    http.get('/api/procesos').subscribe({ error: () => {} });
    api.expectOne('/api/procesos').flush({ error: 'Inicie sesión' }, { status: 401, statusText: 'Unauthorized' });
    expect(auth.conectado()).toBe(false);
    expect(sessionStorage.getItem('gj.sesion')).toBeNull();
  });

  it('un token vencido no se usa', () => {
    iniciar({ ...SESION, expira: new Date(Date.now() - 1000).toISOString() });
    expect(auth.token()).toBeNull();
    expect(auth.conectado()).toBe(false);
  });
});
