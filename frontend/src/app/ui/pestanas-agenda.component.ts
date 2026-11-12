import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Términos y audiencias: dos vistas del mismo trabajo (qué vence y cuándo), unidas por pestañas. */
@Component({
  selector: 'app-pestanas-agenda',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="pestanas" aria-label="Términos y audiencias">
      <a routerLink="/terminos" routerLinkActive="activa" ariaCurrentWhenActive="page">Términos</a>
      <a routerLink="/calendario" routerLinkActive="activa" ariaCurrentWhenActive="page"
        >Calendario y audiencias</a
      >
    </nav>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .pestanas {
        display: inline-flex;
        gap: 4px;
        padding: 4px;
        border-radius: 12px;
        background: var(--surface);
        border: 1px solid var(--line);
        width: fit-content;
        max-width: 100%;
        overflow-x: auto;
      }
      a {
        padding: 8px 16px;
        border-radius: 9px;
        font-weight: 600;
        font-size: 13.5px;
        white-space: nowrap;
        color: var(--muted);
        text-decoration: none;
        transition:
          background-color 0.15s,
          color 0.15s;
      }
      a:hover {
        color: var(--ink);
        background: var(--surface-2);
      }
      a.activa {
        background: var(--ink);
        color: var(--surface);
      }
    `,
  ],
})
export class PestanasAgendaComponent {}
