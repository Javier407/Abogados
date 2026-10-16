import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/** Sin sesión, cualquier pantalla lleva al inicio de sesión (y vuelve a donde iba después). */
export const conSesion: CanActivateFn = (_ruta, estado) => {
  const auth = inject(AuthService);
  return auth.token()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { volver: estado.url } });
};

/** Para pantallas restringidas por rol (el gateway igual lo verifica en cada llamada). */
export const conRol = (...roles: string[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  if (auth.tiene(...roles)) return true;
  // Sin el rol: al panel si es personal; a "Mi cuenta" si no (evita redirigir en círculo).
  return inject(Router).createUrlTree([auth.esPersonal() ? '/' : '/cuenta']);
};
