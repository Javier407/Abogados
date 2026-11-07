import { Component, input, ChangeDetectionStrategy } from '@angular/core';

/**
 * Marca Lexora: monograma (una L que sostiene una X, el «cruce» de la ley con el plazo) en un cuadro índigo,
 * y el nombre en Plus Jakarta Sans. Variante «icono» para el menú colapsado.
 */
@Component({
  selector: 'app-logo',
  standalone: true,
  template: `
    <span class="marca" role="img" aria-label="Lexora">
      <svg class="monograma" viewBox="0 0 40 40" aria-hidden="true">
        <rect width="40" height="40" rx="11" fill="var(--accent, #4338FF)" />
        <path
          d="M11.5 10v19.5H22"
          fill="none"
          stroke="#fff"
          stroke-width="4.2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <path
          d="M19 10.5 29 22"
          fill="none"
          stroke="#fff"
          stroke-width="4.2"
          stroke-linecap="round"
        />
        <path
          d="M29 10.5 25.2 15"
          fill="none"
          stroke="#fff"
          stroke-opacity=".55"
          stroke-width="4.2"
          stroke-linecap="round"
        />
        <circle cx="29" cy="29.5" r="2.6" fill="#fff" fill-opacity=".9" />
      </svg>
      @if (variante() === 'completo') {
        <span class="nombre" aria-hidden="true">Lexora</span>
      }
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      :host {
        display: inline-flex;
      }
      .marca {
        display: inline-flex;
        align-items: center;
        gap: 10px;
        color: var(--ink);
      }
      .monograma {
        width: var(--logo-tam, 34px);
        height: var(--logo-tam, 34px);
        flex: none;
        display: block;
      }
      .nombre {
        font-weight: 800;
        font-size: var(--logo-texto, 19px);
        letter-spacing: -0.03em;
        line-height: 1;
      }
    `,
  ],
})
export class LogoComponent {
  readonly variante = input<'completo' | 'icono'>('completo');
}
