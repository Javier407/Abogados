import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from './auth.service';

/**
 * Sin sesión, cualquier pantalla lleva al inicio de sesión (y vuelve a donde iba después).
 * Si esta pestaña no tiene token pero el navegador conserva la cookie de la sesión (otra pestaña, recarga),
 * se renueva sin pedir la contraseña.
 */
export const conSesion: CanActivateFn = (_ruta, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.token()) return true;
  return auth.renovar().pipe(map(s => s
    ? true
    : router.createUrlTree(['/login'], { queryParams: { volver: estado.url } })));
};

/** Para pantallas restringidas por rol (el gateway igual lo verifica en cada llamada). */
export const conRol = (...roles: string[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  if (auth.tiene(...roles)) return true;
  // Sin el rol: a su pantalla de inicio (el panel, el portal o "Mi cuenta"); evita redirigir en círculo.
  return inject(Router).createUrlTree([auth.inicio()]);
};

/** Con una contraseña temporal solo se puede ir a "Mi cuenta" hasta cambiarla. */
export const claveAlDia: CanActivateChildFn = (_ruta, estado) => {
  const auth = inject(AuthService);
  if (!auth.sesion()?.debeCambiarClave || estado.url.startsWith('/cuenta')) return true;
  return inject(Router).createUrlTree(['/cuenta'], { queryParams: { obligatorio: 1 } });
};
