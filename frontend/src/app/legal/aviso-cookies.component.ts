import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { CLAVE_AVISO_COOKIES, DATOS_LEGALES } from './datos-legales';

/**
 * Aviso de cookies. La app solo usa cookies necesarias y preferencias propias, así que no hay nada opcional que
 * aceptar o rechazar: el aviso informa y se cierra. Vuelve a salir si cambia la versión de los textos legales.
 */
@Component({
  selector: 'app-aviso-cookies',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <section class="aviso" role="region" aria-label="Aviso de cookies">
        <p>Usamos solo las cookies necesarias para que inicie sesión de forma segura y para recordar sus preferencias.
          No usamos cookies de publicidad ni de seguimiento.
          <a routerLink="/cookies">Política de cookies</a> · <a routerLink="/privacidad">Privacidad</a></p>
        <button class="btn btn-primary btn-sm" type="button" (click)="cerrar()">Entendido</button>
      </section>
    }
  `,
  styles: [`
    .aviso {
      position: fixed; left: 16px; right: 16px; bottom: 16px; z-index: 70; max-width: 720px; margin: 0 auto;
      display: flex; align-items: center; gap: 16px; padding: 14px 16px 14px 20px;
      background: var(--surface); color: var(--ink); border: 1px solid var(--line); border-radius: 16px; box-shadow: var(--shadow-lg);
      font-size: 13.5px; line-height: 1.5;
    }
    p { margin: 0; flex: 1; }
    @media (max-width: 560px) { .aviso { flex-direction: column; align-items: stretch; } }
    @media print { .aviso { display: none; } }
  `]
})
export class AvisoCookiesComponent {
  readonly visible = signal(leer() !== DATOS_LEGALES.version);

  cerrar(): void {
    this.visible.set(false);
    try { localStorage.setItem(CLAVE_AVISO_COOKIES, DATOS_LEGALES.version); } catch { /* sin almacenamiento: vuelve a salir */ }
  }
}

function leer(): string | null {
  try { return localStorage.getItem(CLAVE_AVISO_COOKIES); } catch { return null; }
}
