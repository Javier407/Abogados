import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/** Agrega el token a cada llamada a /api y, si el gateway responde 401, vuelve al inicio de sesión. */
export const autenticacion: HttpInterceptorFn = (peticion, siguiente) => {
  const auth = inject(AuthService);
  const esLogin = peticion.url.endsWith('/api/auth/login');
  const token = esLogin ? null : auth.token();
  const conToken = token && peticion.url.startsWith('/api/')
    ? peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : peticion;
  return siguiente(conToken).pipe(catchError((e: unknown) => {
    if (e instanceof HttpErrorResponse && e.status === 401 && !esLogin) auth.cerrar(true);
    return throwError(() => e);
  }));
};
