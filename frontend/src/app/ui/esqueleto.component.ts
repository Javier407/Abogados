import { Component, ChangeDetectionStrategy, computed, input } from '@angular/core';

/**
 * Esqueleto de carga: la forma de la tabla o de la lista mientras llegan los datos, en lugar de un texto
 * «Cargando…». Para lectores de pantalla se anuncia el texto (role="status"); las barras son decorativas.
 */
@Component({
  selector: 'app-esqueleto',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="esqueleto" role="status" aria-live="polite" [attr.aria-busy]="true">
      <span class="sr-only">{{ texto() }}</span>
      @for (f of filas(); track f) {
        <div class="fila" aria-hidden="true">
          @for (c of columnas(); track c) {
            <span class="skeleton" [style.width.%]="ancho(f, c)"></span>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      .esqueleto {
        display: flex; flex-direction: column; gap: 0; padding: 6px 0;
        background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg);
      }
      .fila { display: flex; gap: 16px; align-items: center; padding: 14px 18px; border-top: 1px solid var(--line); }
      .fila:first-of-type { border-top: 0; }
      .fila .skeleton { height: 12px; }
      .fila .skeleton:first-child { height: 14px; }
      .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
    `,
  ],
})
export class EsqueletoComponent {
  /** Lo que lee un lector de pantalla mientras carga. */
  readonly texto = input('Cargando…');
  readonly cantidadFilas = input(5);
  readonly cantidadColumnas = input(4);

  readonly filas = computed(() => Array.from({ length: this.cantidadFilas() }, (_, i) => i));
  readonly columnas = computed(() => Array.from({ length: this.cantidadColumnas() }, (_, i) => i));

  /** Anchos distintos por fila y columna para que parezca contenido real (y siempre los mismos). */
  ancho(fila: number, columna: number): number {
    const base = [34, 22, 18, 14, 12][columna] ?? 12;
    return base + ((fila * 7 + columna * 5) % 9);
  }
}
