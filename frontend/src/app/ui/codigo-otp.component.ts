import {
  Component,
  ElementRef,
  forwardRef,
  input,
  output,
  signal,
  viewChildren,
  ChangeDetectionStrategy,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Código de verificación en casillas de un dígito (estilo OTP): avanza solo, retrocede con Borrar, acepta pegar
 * el código completo y avisa cuando está completo. Funciona con ngModel (required valida que no esté vacío).
 */
@Component({
  selector: 'app-codigo-otp',
  standalone: true,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CodigoOtpComponent), multi: true },
  ],
  template: `
    <div class="otp" role="group" [attr.aria-label]="etiqueta()" [class.con-error]="conError()">
      @for (d of digitos(); track $index; let i = $index) {
        <input
          #casilla
          class="casilla"
          [class.llena]="!!d"
          type="text"
          inputmode="numeric"
          maxlength="1"
          [attr.autocomplete]="i === 0 ? 'one-time-code' : 'off'"
          [attr.aria-label]="'Dígito ' + (i + 1) + ' de ' + largo()"
          [value]="d"
          [disabled]="deshabilitado()"
          (input)="escribir(i, $event)"
          (keydown)="tecla(i, $event)"
          (paste)="pegar($event)"
          (focus)="seleccionar($event)"
        />
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .otp {
        display: flex;
        gap: 10px;
        justify-content: center;
      }
      .casilla {
        width: 48px;
        height: 56px;
        text-align: center;
        font-size: 22px;
        font-weight: 700;
        font-family: var(--font);
        border: 1.5px solid var(--line-input);
        border-radius: 12px;
        background: var(--surface);
        color: var(--ink);
        transition:
          border-color 0.15s,
          box-shadow 0.15s,
          transform 0.15s;
      }
      .casilla:focus {
        outline: none;
        border-color: var(--accent);
        box-shadow: 0 0 0 4px var(--accent-soft);
      }
      .casilla.llena {
        border-color: var(--accent);
        animation: aparecer 0.18s ease-out;
      }
      .con-error .casilla {
        border-color: var(--danger);
        animation: temblar 0.35s ease-in-out;
      }
      @keyframes aparecer {
        from {
          transform: scale(0.85);
        }
        to {
          transform: scale(1);
        }
      }
      @keyframes temblar {
        0%,
        100% {
          transform: translateX(0);
        }
        25% {
          transform: translateX(-4px);
        }
        75% {
          transform: translateX(4px);
        }
      }
      @media (max-width: 400px) {
        .otp {
          gap: 6px;
        }
        .casilla {
          width: 40px;
          height: 50px;
          font-size: 20px;
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .casilla {
          animation: none !important;
        }
      }
    `,
  ],
})
export class CodigoOtpComponent implements ControlValueAccessor {
  readonly largo = input(6);
  readonly etiqueta = input('Código de 6 dígitos');
  readonly conError = input(false);
  readonly completo = output<string>();

  readonly digitos = signal<string[]>(Array(6).fill(''));
  readonly deshabilitado = signal(false);
  private readonly casillas = viewChildren<ElementRef<HTMLInputElement>>('casilla');
  private alCambiar: (v: string) => void = () => {};
  private alTocar: () => void = () => {};

  writeValue(v: string | null): void {
    const limpio = (v ?? '').replace(/\D/g, '').slice(0, this.largo());
    this.digitos.set(Array.from({ length: this.largo() }, (_, i) => limpio[i] ?? ''));
  }
  registerOnChange(fn: (v: string) => void): void {
    this.alCambiar = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.alTocar = fn;
  }
  setDisabledState(d: boolean): void {
    this.deshabilitado.set(d);
  }

  escribir(i: number, e: Event): void {
    const el = e.target as HTMLInputElement;
    const d = el.value.replace(/\D/g, '').slice(-1);
    el.value = d;
    this.poner(i, d);
    if (d) this.enfocar(i + 1);
  }

  tecla(i: number, e: KeyboardEvent): void {
    if (e.key === 'Backspace' && !this.digitos()[i] && i > 0) {
      e.preventDefault();
      this.poner(i - 1, '');
      this.enfocar(i - 1);
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      this.enfocar(i - 1);
    } else if (e.key === 'ArrowRight') {
      e.preventDefault();
      this.enfocar(i + 1);
    }
  }

  pegar(e: ClipboardEvent): void {
    const texto = (e.clipboardData?.getData('text') ?? '')
      .replace(/\D/g, '')
      .slice(0, this.largo());
    if (!texto) return;
    e.preventDefault();
    this.writeValue(texto);
    this.emitir();
    this.enfocar(Math.min(texto.length, this.largo() - 1));
  }

  seleccionar(e: FocusEvent): void {
    (e.target as HTMLInputElement).select();
  }

  private poner(i: number, d: string): void {
    const l = [...this.digitos()];
    l[i] = d;
    this.digitos.set(l);
    this.emitir();
  }

  private emitir(): void {
    const v = this.digitos().join('');
    this.alCambiar(v);
    this.alTocar();
    if (v.length === this.largo()) this.completo.emit(v);
  }

  private enfocar(i: number): void {
    this.casillas()[Math.max(0, Math.min(i, this.largo() - 1))]?.nativeElement.focus();
  }
}
