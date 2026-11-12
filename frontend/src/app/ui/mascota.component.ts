import { Component, ChangeDetectionStrategy, computed, input } from '@angular/core';

/** Cómo está Toga: tranquila, pensando (cuando algo carga) o contenta (cuando todo está al día). */
export type AnimoMascota = 'neutro' | 'pensando' | 'contento';

/** El nombre del asistente de la firma. Se cambia aquí y en ningún otro lugar. */
export const NOMBRE_ASISTENTE = 'Toga';

/**
 * Toga, la gata abogada del portal: dibujo propio en SVG (gatita negra, ojos grandes, cuello blanco y corbata a
 * rayas). Es decorativa: el texto que la acompaña dice lo importante, así que por defecto no se anuncia a los
 * lectores de pantalla. Parpadea de vez en cuando, salvo con «reducir movimiento».
 */
@Component({
  selector: 'app-mascota',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      class="toga"
      [class.pensando]="animo() === 'pensando'"
      [class.contento]="animo() === 'contento'"
      [attr.width]="tamano()"
      [attr.height]="tamano()"
      viewBox="0 0 120 120"
      [attr.role]="etiqueta() ? 'img' : null"
      [attr.aria-label]="etiqueta() || null"
      [attr.aria-hidden]="etiqueta() ? null : 'true'"
      focusable="false"
    >
      <defs>
        <pattern [attr.id]="idRayas()" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(35)">
          <rect width="6" height="6" fill="#B86A86" />
          <rect width="3" height="6" fill="#5E5A66" />
        </pattern>
      </defs>
      <!-- sombra -->
      <ellipse cx="60" cy="112" rx="34" ry="5" class="sombra" />
      <!-- cola -->
      <path d="M86 98 C 108 96, 110 76, 98 70" class="pelo cola" fill="none" stroke-width="9" stroke-linecap="round" />
      <!-- cuerpo -->
      <path d="M34 110 C 30 84, 40 70, 60 70 C 80 70, 90 84, 86 110 Z" class="pelo" />
      <!-- patitas -->
      <ellipse cx="48" cy="110" rx="7" ry="4" class="pelo" />
      <ellipse cx="72" cy="110" rx="7" ry="4" class="pelo" />
      <!-- orejas -->
      <path d="M30 42 L 30 10 L 52 28 Z" class="pelo" />
      <path d="M90 42 L 90 10 L 68 28 Z" class="pelo" />
      <path d="M34 34 L 34 18 L 46 28 Z" class="oreja-dentro" />
      <path d="M86 34 L 86 18 L 74 28 Z" class="oreja-dentro" />
      <!-- cabeza -->
      <ellipse cx="60" cy="46" rx="34" ry="28" class="pelo" />
      <!-- cuello blanco (sobre la barbilla) -->
      <path d="M38 71 L 60 87 L 82 71 L 76 66 L 60 78 L 44 66 Z" class="cuello" />
      <!-- corbata -->
      <path d="M56 80 L 64 80 L 62 87 L 67 104 L 60 110 L 53 104 L 58 87 Z" [attr.fill]="'url(#' + idRayas() + ')'" />
      <path d="M56 79 L 64 79 L 62 87 L 58 87 Z" class="nudo" />
      <!-- bigotes -->
      <g class="bigotes" stroke-width="1.4" stroke-linecap="round">
        <line x1="22" y1="50" x2="8" y2="46" />
        <line x1="22" y1="55" x2="8" y2="56" />
        <line x1="98" y1="50" x2="112" y2="46" />
        <line x1="98" y1="55" x2="112" y2="56" />
      </g>
      <!-- ojos -->
      <g class="ojos">
        <circle cx="46" cy="44" r="11" class="iris" />
        <circle cx="74" cy="44" r="11" class="iris" />
        <circle cx="46" cy="45" r="7" class="pupila" />
        <circle cx="74" cy="45" r="7" class="pupila" />
        <circle cx="43" cy="41" r="2.4" class="brillo" />
        <circle cx="71" cy="41" r="2.4" class="brillo" />
      </g>
      <!-- párpados (parpadeo) -->
      <g class="parpados">
        <rect x="34" y="32" width="24" height="24" rx="11" class="pelo" />
        <rect x="62" y="32" width="24" height="24" rx="11" class="pelo" />
      </g>
      <!-- nariz y boca -->
      <path d="M57 56 L 63 56 L 60 60 Z" class="nariz" />
      <path d="M60 60 Q 56 64 53 61 M60 60 Q 64 64 67 61" class="boca" fill="none" stroke-width="1.6" stroke-linecap="round" />
    </svg>
  `,
  styles: [
    `
      :host { display: inline-flex; flex: none; }
      .pelo { fill: #17161c; }
      .cola { stroke: #17161c; }
      .sombra { fill: currentColor; opacity: 0.12; }
      .cuello { fill: #fbfbfd; }
      .nudo { fill: #8f4a63; }
      .oreja-dentro { fill: #3a2f38; }
      .bigotes { stroke: #d8d6de; opacity: 0.85; }
      .iris { fill: #a7b34a; }
      .pupila { fill: #1d2310; }
      .brillo { fill: #ffffff; }
      .nariz { fill: #e48aa5; }
      .boca { stroke: #d8d6de; }
      :host-context(.dark) .pelo { fill: #0d0c11; }
      :host-context(.dark) .cola { stroke: #0d0c11; }
      :host-context(.dark) .cuello { fill: #e9e8ee; }

      /* Párpados: abiertos (fuera de los ojos) y bajan un instante cada pocos segundos. */
      .parpados { transform-origin: 60px 32px; transform: scaleY(0); }
      @media (prefers-reduced-motion: no-preference) {
        .parpados { animation: parpadeo 5.5s infinite; }
        .pensando .ojos { animation: mirar 2.4s ease-in-out infinite; }
        .contento { animation: saltito 0.6s ease-out 1; transform-origin: 60px 112px; }
      }
      .pensando .pupila { transform: translate(-2px, -2px); }
      .contento .boca { stroke-width: 2.2; }
      @keyframes parpadeo {
        0%, 92%, 100% { transform: scaleY(0); }
        95% { transform: scaleY(1); }
      }
      @keyframes mirar {
        0%, 100% { transform: translateX(0); }
        50% { transform: translateX(2px); }
      }
      @keyframes saltito {
        0% { transform: translateY(0) scale(1); }
        40% { transform: translateY(-4px) scale(1.02); }
        100% { transform: translateY(0) scale(1); }
      }
    `,
  ],
})
export class MascotaComponent {
  readonly tamano = input(72);
  readonly animo = input<AnimoMascota>('neutro');
  /** Texto para lectores de pantalla. Vacío = decorativa (no se anuncia). */
  readonly etiqueta = input('');
  /** Cada dibujo necesita su propio id para el patrón de la corbata. */
  private static siguiente = 0;
  private readonly numero = MascotaComponent.siguiente++;
  readonly idRayas = computed(() => `toga-rayas-${this.numero}`);
}
