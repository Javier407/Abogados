import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/** Rutas que no llevan el token de acceso (van con usuario y contraseña o con la cookie de la sesión). */
const SIN_TOKEN = ['/api/auth/login', '/api/auth/renovar', '/api/auth/salir'];

/**
 * Agrega el token a cada llamada a /api (renovándolo antes si está por vencer). Si el gateway responde 401
 * (token revocado o vencido), intenta renovar una vez y repite la llamada; si no se puede, vuelve al login.
 */
export const autenticacion: HttpInterceptorFn = (peticion, siguiente) => {
  const auth = inject(AuthService);
  if (!peticion.url.startsWith('/api/') || SIN_TOKEN.some(r => peticion.url.endsWith(r))) {
    return siguiente(peticion);
  }
  const con = (p: HttpRequest<unknown>, token: string | null) =>
    token ? p.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : p;

  return auth.tokenVigente().pipe(
    switchMap(token => siguiente(con(peticion, token))),
    catchError((e: unknown) => {
      if (!(e instanceof HttpErrorResponse) || e.status !== 401) return throwError(() => e);
      return auth.renovar().pipe(switchMap(nueva => {
        if (!nueva) {
          auth.cerrar(true);
          return throwError(() => e);
        }
        return siguiente(con(peticion, nueva.token));
      }));
    })
  );
};
