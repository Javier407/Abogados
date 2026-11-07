import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEsCo from '@angular/common/locales/es-CO';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';

import { routes } from './app.routes';
import { autenticacion } from './core/auth.interceptor';
import { TemaService } from './core/tema.service';
import { vigilarTablasDesplazables } from './core/tablas-desplazables';
import { provideSpartanHlm } from '@spartan-ng/helm/utils';

registerLocaleData(localeEsCo);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withXhr(), withInterceptors([autenticacion])),
    { provide: LOCALE_ID, useValue: 'es-CO' },
    provideSpartanHlm(),
    provideAppInitializer(() => {
      inject(TemaService);
    }), // aplica el tema claro u oscuro desde el inicio
    provideAppInitializer(vigilarTablasDesplazables), // tablas anchas desplazables con el teclado
  ],
};
