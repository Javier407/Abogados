import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { LogoComponent } from '../core/logo.component';
import { DATOS_LEGALES } from './datos-legales';

/** Marco de las páginas legales (públicas, sin sesión): marca, pestañas entre políticas y aviso de borrador. */
@Component({
  selector: 'app-pagina-legal',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, LogoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="legal">
      <header class="barra">
        <a routerLink="/" class="marca" aria-label="Lexora, ir al inicio"><app-logo /></a>
        <nav aria-label="Documentos legales">
          <a routerLink="/privacidad" routerLinkActive="activa" ariaCurrentWhenActive="page">Privacidad</a>
          <a routerLink="/cookies" routerLinkActive="activa" ariaCurrentWhenActive="page">Cookies</a>
        </nav>
      </header>
      <main class="contenido" id="contenido">
        <h1>{{ titulo() }}</h1>
        <p class="meta">{{ datos.responsable }} · versión {{ datos.version }} · vigente desde {{ datos.vigenteDesde }}</p>
        @if (!datos.revisada) {
          <p class="borrador" role="note">Borrador pendiente de revisión jurídica por la firma. Los datos marcados
            [COMPLETAR] aún no se han definido.</p>
        }
        <ng-content />
      </main>
    </div>
  `,
  styles: [`
    .legal { min-height: 100vh; background: var(--bg-page); }
    .barra {
      position: sticky; top: 0; z-index: 5; display: flex; align-items: center; justify-content: space-between; gap: 16px;
      padding: 14px 24px; background: var(--surface); border-bottom: 1px solid var(--line);
    }
    .marca { display: inline-flex; text-decoration: none; }
    nav { display: flex; gap: 4px; }
    nav a { padding: 8px 14px; border-radius: 9px; font-weight: 600; color: var(--muted); text-decoration: none; }
    nav a:hover { background: var(--surface-2); color: var(--ink); }
    nav a.activa { background: var(--accent-soft); color: var(--accent-ink); }
    .contenido { max-width: 780px; margin: 0 auto; padding: 40px 24px 80px; line-height: 1.7; font-size: 15px; }
    h1 { font-size: 30px; }
    .meta { color: var(--muted); margin: 6px 0 20px; }
    .borrador { padding: 12px 16px; border-radius: 12px; background: var(--warn-soft); color: var(--warn); font-weight: 600; margin-bottom: 24px; }
    :host ::ng-deep .contenido h2 { font-size: 19px; margin: 32px 0 8px; }
    :host ::ng-deep .contenido ul { padding-left: 22px; }
    :host ::ng-deep .contenido li { margin: 4px 0; }
    :host ::ng-deep .contenido p { margin: 8px 0; }
    @media (max-width: 600px) { .barra { padding: 12px 16px; } .contenido { padding: 28px 16px 64px; } h1 { font-size: 24px; } }
  `]
})
export class PaginaLegalComponent {
  readonly titulo = input.required<string>();
  readonly datos = DATOS_LEGALES;
}
