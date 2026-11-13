import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ApiService, mensajeDeError } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { MascotaComponent, NOMBRE_ASISTENTE } from './mascota.component';
import { Bloque, bloquesDe, digitos } from './respuesta-asistente';

interface Mensaje {
  de: 'persona' | 'asistente';
  texto: string;
  bloques?: Bloque[];
}

/**
 * «Pregúntale a Lawy»: un botón con su cara abajo a la derecha que abre una conversación. Responde lo mismo que el
 * bot de Telegram (sus procesos, términos, audiencias o un radicado suyo), con las mismas reglas: solo el equipo de
 * la firma, solo lo suyo y solo lectura. La respuesta la arma el servidor con los datos reales; aquí se muestra
 * ordenada: tablas para las listas, y en color lo vencido, lo que vence pronto, las fechas y los radicados (que se
 * pueden tocar para ver ese proceso).
 */
@Component({
  selector: 'app-pregunta-toga',
  standalone: true,
  imports: [FormsModule, MascotaComponent, NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-template #segmentos let-lista>
      @for (s of lista; track $index) {
        @switch (s.tipo) {
          @case ('radicado') {
            <button type="button" class="seg seg-radicado" (click)="preguntar(digitos(s.texto))" [disabled]="pensando()"
                    [attr.aria-label]="'Ver el proceso ' + s.texto">{{ s.texto }}</button>
          }
          @case ('texto') { <span>{{ s.texto }}</span> }
          @default { <span class="seg" [class]="'seg seg-' + s.tipo">{{ s.texto }}</span> }
        }
      }
    </ng-template>

    @if (abierto()) {
      <section class="panel" [class.grande]="grande()" role="dialog" aria-modal="false" aria-labelledby="asistente-titulo">
        <header>
          <span class="cara"><app-mascota [tamano]="50" [animo]="pensando() ? 'pensando' : 'neutro'" /></span>
          <div class="titulos">
            <h2 id="asistente-titulo">{{ nombre }}</h2>
            <p>Asistente de la firma · solo consulta</p>
          </div>
          <button type="button" class="boton-cabecera agrandar" (click)="grande.set(!grande())" [attr.aria-pressed]="grande()"
                  [attr.aria-label]="grande() ? 'Achicar la conversación' : 'Agrandar la conversación'">
            @if (grande()) {
              <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M9 3v6H3M15 21v-6h6M21 9h-6V3M3 15h6v6" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" /></svg>
            } @else {
              <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M3 9V3h6M21 15v6h-6M15 3h6v6M9 21H3v-6" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" /></svg>
            }
          </button>
          <button type="button" class="boton-cabecera" (click)="cerrar()" [attr.aria-label]="'Cerrar la conversación con ' + nombre">×</button>
        </header>

        <div class="conversacion" #conversacion aria-live="polite">
          @if (mensajes().length === 0) {
            <div class="bienvenida">
              <app-mascota [tamano]="72" animo="contento" />
              <p><strong>Hola, soy {{ nombre }}.</strong> Pregúnteme por sus procesos, los términos que le vencen o sus
                audiencias, o escríbame el radicado de un proceso suyo.</p>
            </div>
          }
          @for (m of mensajes(); track $index) {
            @if (m.de === 'persona') {
              <p class="mensaje mio">{{ m.texto }}</p>
            } @else {
              <div class="fila-asistente">
                <span class="mini" aria-hidden="true"><app-mascota [tamano]="26" /></span>
                <div class="mensaje respuesta">
                  @for (b of m.bloques; track $index) {
                    @switch (b.tipo) {
                      @case ('parrafo') {
                        <p [class.titulo]="b.titulo"><ng-container *ngTemplateOutlet="segmentos; context: { $implicit: b.segmentos }" /></p>
                      }
                      @case ('tabla') {
                        <div class="tabla-envoltura">
                          <table>
                            <thead><tr>@for (c of b.columnas; track c) { <th scope="col">{{ c }}</th> }</tr></thead>
                            <tbody>
                              @for (f of b.filas; track $index) {
                                <tr [class]="'u-' + f.urgencia">
                                  @for (celda of f.celdas; track $index) {
                                    <td [attr.data-columna]="b.columnas[$index]"><ng-container *ngTemplateOutlet="segmentos; context: { $implicit: celda }" /></td>
                                  }
                                </tr>
                              }
                            </tbody>
                          </table>
                        </div>
                      }
                      @case ('lista') {
                        <ul>
                          @for (i of b.items; track $index) {
                            <li [class]="'u-' + i.urgencia"><ng-container *ngTemplateOutlet="segmentos; context: { $implicit: i.segmentos }" /></li>
                          }
                        </ul>
                      }
                    }
                  }
                </div>
              </div>
            }
          }
          @if (pensando()) {
            <div class="fila-asistente" role="status">
              <span class="mini" aria-hidden="true"><app-mascota [tamano]="26" animo="pensando" /></span>
              <p class="mensaje pensando"><span class="sr-only">{{ nombre }} está revisando…</span>
                <span class="puntos" aria-hidden="true"><i></i><i></i><i></i></span></p>
            </div>
          }
        </div>

        <div class="sugerencias" aria-label="Preguntas frecuentes">
          @for (s of sugerencias; track s) {
            <button type="button" (click)="preguntar(s)" [disabled]="pensando()">{{ s }}</button>
          }
        </div>
        <form (ngSubmit)="preguntar(texto)" class="escribir">
          <label class="sr-only" for="pregunta-asistente">Su pregunta para {{ nombre }}</label>
          <input #campo id="pregunta-asistente" name="texto" [(ngModel)]="texto" maxlength="300" autocomplete="off"
                 placeholder="Escriba su pregunta o un radicado" [disabled]="pensando()" />
          <button class="enviar" type="submit" [disabled]="pensando() || !texto.trim()" aria-label="Preguntar">
            <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M3 11.5 21 3l-8.5 18-2-7.5Z" fill="currentColor" /></svg>
          </button>
        </form>
      </section>
    }
    <button type="button" class="lanzador" (click)="alternar()" [attr.aria-expanded]="abierto()"
            [attr.aria-label]="abierto() ? 'Cerrar la conversación con ' + nombre : 'Pregúntale a ' + nombre">
      <app-mascota [tamano]="56" />
    </button>
  `,
  styles: [
    `
      :host { position: fixed; right: 20px; bottom: 20px; z-index: 60; display: flex; flex-direction: column;
              align-items: flex-end; gap: 12px; }
      .lanzador {
        width: 68px; height: 68px; border-radius: 50%; border: 2px solid var(--surface); padding: 0;
        background: radial-gradient(circle at 30% 25%, var(--accent-soft), var(--surface) 70%);
        box-shadow: var(--shadow-md), 0 0 0 3px var(--accent-soft); display: grid; place-items: center;
      }
      .lanzador:hover { box-shadow: var(--shadow-lg), 0 0 0 4px var(--accent); }
      .panel {
        width: clamp(380px, 40vw, 600px); height: min(700px, calc(100vh - 116px)); display: flex;
        flex-direction: column; border-radius: 18px; background: var(--surface); border: 1px solid var(--line);
        box-shadow: var(--shadow-lg); overflow: hidden; transition: width .2s ease, height .2s ease;
      }
      .panel.grande { width: min(980px, calc(100vw - 48px)); height: calc(100vh - 116px); }
      /* Celular: ocupa la pantalla, encima del botón. */
      @media (max-width: 640px) {
        :host { right: 12px; bottom: 12px; }
        .panel, .panel.grande { position: fixed; inset: 8px 8px 88px 8px; width: auto; height: auto; }
        .agrandar { display: none; }
      }
      @media (max-width: 420px) {
        .titulos p, .bienvenida app-mascota { display: none; }
        .cara { width: 46px; height: 46px; }
        header { padding: 10px 12px; }
      }
      @media (prefers-reduced-motion: no-preference) {
        .panel { animation: subir .22s cubic-bezier(.22, 1, .36, 1) both; }
        .fila-asistente, .mensaje.mio { animation: aparecer .25s ease-out both; }
        .puntos i { animation: punto 1.1s infinite ease-in-out; }
        .puntos i:nth-child(2) { animation-delay: .15s; }
        .puntos i:nth-child(3) { animation-delay: .3s; }
      }
      @keyframes subir { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
      @keyframes aparecer { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: none; } }
      @keyframes punto { 0%, 80%, 100% { opacity: .25; transform: translateY(0); } 40% { opacity: 1; transform: translateY(-3px); } }

      header {
        display: flex; align-items: center; gap: 12px; padding: 12px 14px; color: #fff;
        background: linear-gradient(120deg, var(--accent), color-mix(in srgb, var(--accent) 60%, #000));
      }
      .cara { width: 56px; height: 56px; flex: none; border-radius: 50%; background: #fff; display: grid; place-items: center;
              box-shadow: 0 2px 8px rgb(0 0 0 / .2); }
      .titulos { flex: 1; min-width: 0; }
      .titulos h2 { font-size: 19px; font-weight: 800; letter-spacing: .01em; color: #fff; line-height: 1.2; }
      .titulos p { font-size: 12.5px; opacity: .9; color: #fff; }
      .boton-cabecera { border: 0; background: rgb(255 255 255 / .15); font-size: 22px; line-height: 1; color: #fff;
                        width: 36px; height: 36px; border-radius: 50%; display: grid; place-items: center; flex: none; }
      .boton-cabecera:hover { background: rgb(255 255 255 / .28); }

      .conversacion { flex: 1; min-height: 0; overflow-y: auto; padding: 16px; display: flex; flex-direction: column;
                      gap: 12px; background: var(--surface-2); }
      .bienvenida { display: flex; gap: 14px; align-items: center; padding: 14px; border-radius: 14px;
                    background: var(--surface); border: 1px dashed var(--line); font-size: 14px; line-height: 1.5;
                    color: var(--ink-2); }
      .fila-asistente { display: flex; gap: 8px; align-items: flex-start; max-width: 100%; }
      .mini { flex: none; width: 34px; height: 34px; border-radius: 50%; background: var(--surface);
              border: 1px solid var(--line); display: grid; place-items: center; }
      .mensaje { font-size: 14px; line-height: 1.55; border-radius: 14px; color: var(--ink); }
      .mensaje.mio {
        align-self: flex-end; max-width: 80%; padding: 9px 14px; white-space: pre-line;
        background: var(--accent); color: var(--on-accent, #fff); border-bottom-right-radius: 4px;
      }
      .respuesta {
        min-width: 0; flex: 1; padding: 12px 14px; background: var(--surface); container-type: inline-size; border: 1px solid var(--line);
        border-top-left-radius: 4px; box-shadow: var(--shadow-sm); display: flex; flex-direction: column; gap: 8px;
      }
      .respuesta p { margin: 0; }
      .respuesta p.titulo { font-weight: 700; color: var(--ink); }
      .pensando { padding: 10px 14px; background: var(--surface); border: 1px solid var(--line); }
      .puntos { display: inline-flex; gap: 4px; }
      .puntos i { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); display: block; }

      /* Tablas */
      .tabla-envoltura { overflow-x: auto; border: 1px solid var(--line); border-radius: 10px; }
      table { width: 100%; border-collapse: collapse; font-size: 13px; line-height: 1.45; }
      th { text-align: left; font-size: 11.5px; font-weight: 700; text-transform: uppercase; letter-spacing: .04em;
           color: var(--muted); background: var(--surface-2); padding: 8px 10px; white-space: nowrap; }
      td { padding: 9px 10px; border-top: 1px solid var(--line); vertical-align: top; min-width: 90px; }
      td:first-child { min-width: 0; white-space: nowrap; }
      tbody tr:nth-child(even) td { background: color-mix(in srgb, var(--surface-2) 55%, transparent); }

      /* Sin espacio para la tabla: cada fila es una tarjeta con el nombre de cada dato. */
      @container (max-width: 540px) {
        .tabla-envoltura { border: 0; border-radius: 0; overflow: visible; }
        table, tbody, tr, td { display: block; width: 100%; }
        thead { display: none; }
        tr { border: 1px solid var(--line); border-left-width: 4px; border-radius: 10px; overflow: hidden;
             background: var(--surface); }
        tr + tr { margin-top: 8px; }
        tr.u-alta { border-left-color: var(--color-danger); }
        tr.u-media { border-left-color: var(--color-warn); }
        td, td:first-child, tbody tr:nth-child(even) td {
          display: grid; grid-template-columns: 96px minmax(0, 1fr); gap: 10px; align-items: baseline;
          padding: 7px 10px; min-width: 0; white-space: normal; box-shadow: none; background: transparent;
        }
        td + td { border-top: 1px solid var(--line); }
        tr td:first-child { border-top: 0; }
        tr.u-alta td { background: color-mix(in srgb, var(--color-danger) 6%, transparent); }
        td::before { content: attr(data-columna); font-size: 11px; font-weight: 700; text-transform: uppercase;
                     letter-spacing: .04em; color: var(--muted); }
      }
      tr td:first-child { box-shadow: inset 3px 0 0 transparent; }
      tr.u-alta td { background: color-mix(in srgb, var(--color-danger) 7%, transparent); }
      tr.u-alta td:first-child { box-shadow: inset 3px 0 0 var(--color-danger); }
      tr.u-media td:first-child { box-shadow: inset 3px 0 0 var(--color-warn); }

      /* Listas */
      ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
      li { padding: 7px 10px; border-radius: 10px; background: var(--surface-2); border-left: 3px solid var(--line); }
      li.u-alta { border-left-color: var(--color-danger); }
      li.u-media { border-left-color: var(--color-warn); }

      /* Lo resaltado */
      .seg { white-space: nowrap; }
      .seg-radicado {
        font: inherit; font-family: var(--mono, monospace); font-size: 11.5px; padding: 1px 6px; border-radius: 6px;
        border: 0; background: var(--accent-soft); color: var(--accent-ink); cursor: pointer; white-space: nowrap;
      }
      .seg-radicado:hover:not(:disabled) { background: var(--accent); color: var(--on-accent, #fff); }
      .seg-fecha { font-weight: 700; color: var(--ink); }
      .seg-vencido { background: var(--color-danger); color: #fff; font-weight: 700; font-size: 11.5px;
                     padding: 1px 7px; border-radius: 99px; }
      .seg-hoy { background: var(--color-warn); color: #1d1300; font-weight: 700; font-size: 11.5px;
                 padding: 1px 7px; border-radius: 99px; }
      .seg-pronto { background: var(--color-warn-soft); color: var(--ink); font-weight: 600; font-size: 11.5px;
                    padding: 1px 7px; border-radius: 99px; }
      .seg-ok { background: var(--color-ok-soft); color: var(--ink); font-size: 11.5px; padding: 1px 7px; border-radius: 99px; }
      .seg-comando { font-family: var(--mono, monospace); color: var(--accent-ink); }

      .sugerencias { display: flex; gap: 6px; overflow-x: auto; padding: 10px 14px 4px; background: var(--surface);
                     scrollbar-width: thin; }
      .sugerencias button { flex: none; white-space: nowrap; }
      .sugerencias button {
        font: inherit; font-size: 13px; font-weight: 600; padding: 7px 14px; border-radius: 99px;
        border: 1px solid var(--accent-soft); background: var(--accent-soft); color: var(--accent-ink);
      }
      .sugerencias button:hover:not(:disabled) { border-color: var(--accent); }
      .escribir { display: flex; gap: 8px; padding: 10px 14px 14px; background: var(--surface); }
      .escribir input {
        flex: 1; min-width: 0; font: inherit; font-size: 14.5px; padding: 11px 16px; border-radius: 99px;
        border: 1px solid var(--line-input, var(--line)); background: var(--surface-2); color: var(--ink);
      }
      .escribir input:focus { outline: 2px solid var(--accent); outline-offset: 1px; }
      .enviar { flex: none; width: 44px; height: 44px; border-radius: 50%; border: 0; background: var(--accent);
                color: var(--on-accent, #fff); display: grid; place-items: center; }
      .enviar:disabled { opacity: .45; }
      .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
      @media print { :host { display: none; } }
    `,
  ],
})
export class PreguntaTogaComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  readonly nombre = NOMBRE_ASISTENTE;
  readonly digitos = digitos;
  /** Los socios también pueden preguntar por el equipo (el servidor lo comprueba con su cargo). */
  readonly sugerencias = [
    'Mis procesos', '¿Qué términos me vencen?', 'Mis audiencias',
    ...(this.auth.tiene('SOCIO') ? ['Abogados'] : []),
  ];

  readonly abierto = signal(false);
  /** Modo grande: casi toda la pantalla, para leer tablas largas. */
  readonly grande = signal(false);
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
    this.mensajes.update((m) => [...m, { de: 'asistente', texto, bloques: bloquesDe(texto) }]);
    this.bajar();
  }

  private bajar(): void {
    setTimeout(() => {
      const c = this.conversacion()?.nativeElement;
      if (c) c.scrollTop = c.scrollHeight;
    });
  }
}
