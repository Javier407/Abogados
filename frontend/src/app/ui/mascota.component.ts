import { Component, ChangeDetectionStrategy, computed, input } from '@angular/core';

/** Cómo está Lawy: tranquilo, pensando (cuando algo carga) o contento (cuando todo está al día). */
export type AnimoMascota = 'neutro' | 'pensando' | 'contento';

/** El nombre del asistente de la firma. Se cambia aquí y en ningún otro lugar del portal. */
export const NOMBRE_ASISTENTE = 'Lawy';

/**
 * Lawy, el perro abogado del portal: dibujo propio en 2D plano (SVG): un beagle crema con orejas largas color
 * canela, gafas redondas, toga negra, corbatín de lunares y un mazo. Es decorativo: el texto que lo acompaña dice lo
 * importante, así que por defecto no se anuncia a los lectores de pantalla. Parpadea de vez en cuando, salvo con
 * «reducir movimiento».
 */
@Component({
  selector: 'app-mascota',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      class="lawy"
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
        <pattern [attr.id]="idPatron()" width="5" height="5" patternUnits="userSpaceOnUse">
          <rect width="5" height="5" fill="#9b2c3f" />
          <circle cx="2.5" cy="2.5" r="0.9" fill="#f4c8d1" />
        </pattern>
      </defs>
      <!-- sombra -->
      <ellipse cx="60" cy="113" rx="36" ry="5" class="sombra" />
      <!-- toga -->
      <path d="M28 112 C 28 90, 40 77, 60 77 C 80 77, 92 90, 92 112 Z" class="toga-negra" />
      <path d="M60 79 L 50 112 M60 79 L 70 112" class="pliegue" fill="none" stroke-width="1.4" />
      <!-- camisa y corbatín -->
      <path d="M49 78 L 60 92 L 71 78 Z" class="camisa" />
      <path d="M60 84 L 49 79 L 49 89 Z" [attr.fill]="'url(#' + idPatron() + ')'" />
      <path d="M60 84 L 71 79 L 71 89 Z" [attr.fill]="'url(#' + idPatron() + ')'" />
      <circle cx="60" cy="84" r="2.6" class="nudo" />
      <ellipse cx="38" cy="104" rx="7" ry="5.5" class="pata" />
      <!-- orejas largas -->
      <path d="M33 30 C 17 34, 13 60, 22 74 C 28 82, 38 76, 38 64 L 40 38 Z" class="oreja" />
      <path d="M87 30 C 103 34, 107 60, 98 74 C 92 82, 82 76, 82 64 L 80 38 Z" class="oreja" />
      <!-- cabeza -->
      <ellipse cx="60" cy="47" rx="29" ry="28" class="crema" />
      <!-- manto canela con la franja blanca al centro -->
      <path d="M33 38 C 34 22, 46 17, 60 17 C 74 17, 86 22, 87 38 C 80 33, 70 32, 64 36 L 60 46 L 56 36 C 50 32, 40 33, 33 38 Z" class="canela" />
      <!-- cejas -->
      <path d="M41 31 Q 47 27.5 53 30.5 M67 30.5 Q 73 27.5 79 31" class="ceja" fill="none" stroke-width="2.2" stroke-linecap="round" />
      <!-- hocico -->
      <ellipse cx="60" cy="61" rx="15" ry="10.5" class="hocico" />
      <!-- ojos -->
      <g class="ojos">
        <ellipse cx="48" cy="45" rx="3.6" ry="4.4" class="pupila" />
        <ellipse cx="72" cy="45" rx="3.6" ry="4.4" class="pupila" />
        <circle cx="49.2" cy="43.4" r="1.3" class="brillo" />
        <circle cx="73.2" cy="43.4" r="1.3" class="brillo" />
      </g>
      <!-- párpados (parpadeo) -->
      <g class="parpados">
        <ellipse cx="48" cy="45" rx="5" ry="5.6" class="crema" />
        <ellipse cx="72" cy="45" rx="5" ry="5.6" class="crema" />
      </g>
      <!-- gafas redondas -->
      <g class="gafas" fill="none" stroke-width="2.4">
        <circle cx="48" cy="45" r="9.5" class="lente" />
        <circle cx="72" cy="45" r="9.5" class="lente" />
        <path d="M57.5 44 Q 60 41.5 62.5 44" />
        <path d="M38.5 43 L 33 40.5 M81.5 43 L 87 40.5" stroke-linecap="round" />
      </g>
      <!-- mazo en la pata: el mango sale de la pata y la cabeza queda atravesada al mango -->
      <g class="mazo">
        <line x1="84" y1="99" x2="97.5" y2="81" class="mango" stroke-width="4" stroke-linecap="round" />
        <g transform="rotate(37 99 78.5)">
          <rect x="89" y="73.5" width="20" height="10" rx="2.5" class="cabeza-mazo" />
          <rect x="88" y="72.5" width="4" height="12" rx="1.5" class="banda" />
          <rect x="106" y="72.5" width="4" height="12" rx="1.5" class="banda" />
        </g>
      </g>
      <circle cx="84" cy="99" r="6.5" class="pata" />
      <!-- la pata que se acomoda las gafas (solo aparece en la animación) -->
      <ellipse cx="37" cy="58" rx="5.5" ry="5" class="pata pata-gafas" />
      <!-- nariz y boca -->
      <path d="M53.5 55 Q 60 51 66.5 55 Q 65 61 60 61.5 Q 55 61 53.5 55 Z" class="nariz" />
      <circle cx="57.5" cy="54.6" r="1.1" class="brillo" />
      <path d="M60 61.5 L 60 64 M60 64 Q 56.5 67.5 53 65 M60 64 Q 63.5 67.5 67 65" class="boca" fill="none" stroke-width="1.7" stroke-linecap="round" />
      <path d="M57 66.4 Q 60 71 63 66.4 Z" class="lengua" />
    </svg>
  `,
  styles: [
    `
      :host { display: inline-flex; flex: none; }
      .sombra { fill: currentColor; opacity: 0.12; }
      .toga-negra { fill: #1d2130; }
      .pliegue { stroke: #343a50; }
      .camisa { fill: #ffffff; }
      .nudo { fill: #7a1f30; }
      .crema { fill: #fff6e6; }
      .hocico { fill: #ffffff; }
      .canela { fill: #d4893f; }
      .oreja { fill: #8a5226; }
      .ceja { stroke: #6b3d1b; }
      .pupila { fill: #231a14; }
      .brillo { fill: #ffffff; }
      .nariz { fill: #2b1d16; }
      .boca { stroke: #2b1d16; }
      .lengua { fill: #e8798f; opacity: 0; }
      .gafas { stroke: #20232c; }
      .lente { fill: none; }
      .pata { fill: #fff6e6; stroke: #e5d6bd; stroke-width: 1; }
      .mango { stroke: #8b5a2b; }
      .cabeza-mazo { fill: #a0632f; }
      .banda { fill: #6f3f1c; }
      :host-context(.dark) .toga-negra { fill: #0f121c; }

      /* Cada 7 s: parpadea, se sube las gafas con la pata y vuelve a parpadear. Párpados abiertos = sin altura. */
      .parpados { transform-origin: 60px 40px; transform: scaleY(0); }
      .pata-gafas { opacity: 0; }
      .gafas { transform-origin: 60px 45px; }
      @media (prefers-reduced-motion: no-preference) {
        .parpados { animation: parpadeo 7s infinite; }
        .gafas { animation: acomodar 7s infinite; }
        .pata-gafas { animation: empujar 7s infinite; }
        .pensando .ojos { animation: mirar 2.4s ease-in-out infinite; }
        .pensando .mazo { animation: golpe 1.2s ease-in-out infinite; transform-origin: 84px 99px; }
        .contento { animation: saltito 0.6s ease-out 1; transform-origin: 60px 112px; }
      }
      .pensando .pupila { transform: translate(-1.5px, -1.5px); }
      .contento .lengua { opacity: 1; }
      @keyframes parpadeo {
        0%, 38%, 42%, 91%, 95%, 100% { transform: scaleY(0); }
        40%, 93% { transform: scaleY(1); }
      }
      @keyframes acomodar {
        0%, 74%, 90%, 100% { transform: translateY(0) rotate(0deg); }
        79% { transform: translateY(-3px) rotate(-4deg); }
        84% { transform: translateY(-1px) rotate(1.5deg); }
      }
      @keyframes empujar {
        0%, 70%, 90%, 100% { opacity: 0; transform: translateY(12px); }
        75% { opacity: 1; transform: translateY(1px); }
        79% { opacity: 1; transform: translateY(-3px); }
        85% { opacity: 1; transform: translateY(2px); }
        88% { opacity: 0; transform: translateY(10px); }
      }
      @keyframes mirar {
        0%, 100% { transform: translateX(0); }
        50% { transform: translateX(2px); }
      }
      @keyframes golpe {
        0%, 100% { transform: rotate(0deg); }
        50% { transform: rotate(14deg); }
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
  /** Texto para lectores de pantalla. Vacío = decorativo (no se anuncia). */
  readonly etiqueta = input('');
  /** Cada dibujo necesita su propio id para el patrón del corbatín. */
  private static siguiente = 0;
  private readonly numero = MascotaComponent.siguiente++;
  readonly idPatron = computed(() => `lawy-lunares-${this.numero}`);
}
