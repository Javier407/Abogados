import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, mensajeDeError } from '../core/api.service';
import { MascotaComponent, NOMBRE_ASISTENTE } from './mascota.component';

interface Mensaje {
  de: 'persona' | 'toga';
  texto: string;
}

/**
 * «Pregúntale a Toga»: un botón con su cara abajo a la derecha que abre una conversación. Responde lo mismo que el
 * bot de Telegram (sus procesos, términos, audiencias o un radicado suyo), con las mismas reglas: solo el equipo de
 * la firma, solo lo suyo y solo lectura. La respuesta la arma el servidor con los datos reales.
 */
@Component({
  selector: 'app-pregunta-toga',
  standalone: true,
  imports: [FormsModule, MascotaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (abierto()) {
      <section class="panel" role="dialog" aria-modal="false" aria-labelledby="toga-titulo">
        <header>
          <app-mascota [tamano]="40" [animo]="pensando() ? 'pensando' : 'neutro'" />
          <h2 id="toga-titulo">Pregúntale a {{ nombre }}</h2>
          <button type="button" class="cerrar" (click)="cerrar()" aria-label="Cerrar la conversación con Toga">×</button>
        </header>
        <div class="conversacion" #conversacion aria-live="polite">
          @if (mensajes().length === 0) {
            <p class="vacio">Hola. Pregúnteme por sus procesos, los términos que le vencen o sus audiencias, o escríbame
              el radicado de un proceso suyo.</p>
          }
          @for (m of mensajes(); track $index) {
            <p class="mensaje" [class.mio]="m.de === 'persona'">{{ m.texto }}</p>
          }
          @if (pensando()) { <p class="mensaje pensando" role="status">{{ nombre }} está revisando…</p> }
        </div>
        <div class="sugerencias" aria-label="Preguntas frecuentes">
          @for (s of sugerencias; track s) {
            <button type="button" (click)="preguntar(s)" [disabled]="pensando()">{{ s }}</button>
          }
        </div>
        <form (ngSubmit)="preguntar(texto)" class="escribir">
          <label class="sr-only" for="pregunta-toga">Su pregunta para Toga</label>
          <input #campo id="pregunta-toga" name="texto" [(ngModel)]="texto" maxlength="300" autocomplete="off"
                 placeholder="Escriba su pregunta o un radicado" [disabled]="pensando()" />
          <button class="btn btn-primary btn-sm" type="submit" [disabled]="pensando() || !texto.trim()">Preguntar</button>
        </form>
      </section>
    }
    <button type="button" class="lanzador" (click)="alternar()" [attr.aria-expanded]="abierto()"
            [attr.aria-label]="abierto() ? 'Cerrar la conversación con Toga' : 'Pregúntale a Toga'">
      <app-mascota [tamano]="52" />
    </button>
  `,
  styles: [
    `
      :host { position: fixed; right: 20px; bottom: 20px; z-index: 60; display: flex; flex-direction: column;
              align-items: flex-end; gap: 12px; }
      .lanzador {
        width: 64px; height: 64px; border-radius: 50%; border: 1px solid var(--line); background: var(--surface);
        box-shadow: var(--shadow-md); display: grid; place-items: center; padding: 0;
      }
      .lanzador:hover { box-shadow: var(--shadow-lg); }
      .panel {
        width: min(380px, calc(100vw - 32px)); max-height: min(560px, calc(100vh - 120px)); display: flex;
        flex-direction: column; border-radius: 16px; background: var(--surface); border: 1px solid var(--line);
        box-shadow: var(--shadow-lg); overflow: hidden;
      }
      @media (prefers-reduced-motion: no-preference) {
        .panel { animation: subir .22s cubic-bezier(.22, 1, .36, 1) both; }
      }
      @keyframes subir { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
      header { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-bottom: 1px solid var(--line);
               background: linear-gradient(120deg, var(--accent-soft), var(--surface) 80%); }
      header h2 { flex: 1; font-size: 15px; font-weight: 700; }
      .cerrar { border: 0; background: none; font-size: 22px; line-height: 1; color: var(--muted); padding: 4px 8px; }
      .conversacion { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 8px; }
      .vacio { color: var(--muted); font-size: 13.5px; }
      .mensaje {
        white-space: pre-line; font-size: 13.5px; line-height: 1.45; padding: 8px 11px; border-radius: 12px;
        background: var(--surface-2); color: var(--ink); max-width: 92%; align-self: flex-start;
      }
      .mensaje.mio { align-self: flex-end; background: var(--accent); color: #fff; }
      .mensaje.pensando { color: var(--muted); font-style: italic; }
      .sugerencias { display: flex; gap: 6px; flex-wrap: wrap; padding: 0 12px 8px; }
      .sugerencias button {
        font: inherit; font-size: 12.5px; padding: 5px 10px; border-radius: 99px; border: 1px solid var(--line);
        background: var(--surface); color: var(--ink-2);
      }
      .sugerencias button:hover:not(:disabled) { border-color: var(--accent); color: var(--accent-ink); }
      .escribir { display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid var(--line); }
      .escribir input {
        flex: 1; min-width: 0; font: inherit; font-size: 13.5px; padding: 8px 10px; border-radius: 10px;
        border: 1px solid var(--line-input, var(--line)); background: var(--surface); color: var(--ink);
      }
      .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
      @media print { :host { display: none; } }
    `,
  ],
})
export class PreguntaTogaComponent {
  private api = inject(ApiService);
  readonly nombre = NOMBRE_ASISTENTE;
  readonly sugerencias = ['Mis procesos', '¿Qué términos me vencen?', 'Mis audiencias'];

  readonly abierto = signal(false);
  readonly pensando = signal(false);
  readonly mensajes = signal<Mensaje[]>([]);
  texto = '';

  private readonly conversacion = viewChild<ElementRef<HTMLElement>>('conversacion');
  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');

  alternar(): void {
    this.abierto() ? this.cerrar() : this.abrir();
  }

  abrir(): void {
    this.abierto.set(true);
    setTimeout(() => this.campo()?.nativeElement.focus());
  }

  cerrar(): void {
    this.abierto.set(false);
  }

  @HostListener('document:keydown.escape')
  alEscape(): void {
    if (this.abierto()) this.cerrar();
  }

  preguntar(texto: string): void {
    const pregunta = (texto ?? '').trim();
    if (!pregunta || this.pensando()) return;
    this.texto = '';
    this.mensajes.update((m) => [...m, { de: 'persona', texto: pregunta }]);
    this.pensando.set(true);
    this.bajar();
    this.api.preguntarToga(pregunta).subscribe({
      next: (r) => this.responder(r.respuesta),
      error: (e) => this.responder(mensajeDeError(e, 'No pude responder ahora. Intente de nuevo en un momento.')),
    });
  }

  private responder(texto: string): void {
    this.pensando.set(false);
    this.mensajes.update((m) => [...m, { de: 'toga', texto }]);
    this.bajar();
  }

  private bajar(): void {
    setTimeout(() => {
      const c = this.conversacion()?.nativeElement;
      if (c) c.scrollTop = c.scrollHeight;
    });
  }
}
