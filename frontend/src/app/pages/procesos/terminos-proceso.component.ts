import { Component, OnInit, inject, input, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { PlazoProceso, Termino, TerminoSolicitud, etiqueta, textoRestantes } from '../../core/modelos';

/**
 * Términos de un proceso: los plazos de referencia de su tipo de proceso (cada tipo tiene los suyos), los términos
 * que ya corren y el formulario para agregar uno con su vencimiento calculado en días hábiles.
 */
@Component({
  selector: 'app-terminos-proceso',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="card">
      <div class="card-header"><h2>Términos del proceso</h2></div>
      <div class="card-body">
        @if (mensaje) {
          <div class="alerta alerta-exito">{{ mensaje }}</div>
        }
        @if (error) {
          <div class="alerta alerta-error">{{ error }}</div>
        }

        @if (plazos.length) {
          <details class="plazos" open>
            <summary>Plazos de este tipo de proceso ({{ plazos.length }})</summary>
            <p class="form-ayuda">
              Lo que fija la ley para un proceso como este, según el lado del cliente. «Usar» llena el
              formulario de abajo; usted pone la fecha en que se notificó.
            </p>
            <ul>
              @for (p of plazos; track p.tipoProvidencia + p.descripcion) {
                <li [class.opcional]="p.opcional">
                  <div>
                    <strong>{{ p.descripcion }}</strong>
                    <small>{{ p.diasHabiles }} días hábiles · {{ p.norma }}</small>
                    @if (p.momento) {
                      <small>Corre: {{ minuscula(p.momento) }}</small>
                    }
                    @if (!p.validada) {
                      <small class="a-validar">De referencia: confírmelo con la norma</small>
                    }
                  </div>
                  <button
                    class="btn btn-secondary btn-sm"
                    type="button"
                    (click)="usar(p)"
                    [attr.aria-label]="'Usar el plazo: ' + p.descripcion"
                  >
                    Usar
                  </button>
                </li>
              }
            </ul>
          </details>
        }

        <h3 class="subtitulo">Términos que corren en este proceso</h3>
        @if (terminos.length === 0) {
          <p class="form-ayuda">Este proceso no tiene términos registrados.</p>
        } @else {
          <div class="tabla-wrapper">
            <table class="tabla">
              <thead>
                <tr>
                  <th>Qué hay que hacer</th>
                  <th>Notificado</th>
                  <th>Días</th>
                  <th>Vence</th>
                  <th>Situación</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (t of terminos; track t.idTermino) {
                  <tr>
                    <td class="titulo-celda">
                      {{ t.descripcion }}
                      @if (t.norma) {
                        <br /><small class="norma">{{ t.norma }}</small>
                      }
                    </td>
                    <td class="fecha-celda">{{ t.fechaInicio | date: 'dd/MM/yyyy' }}</td>
                    <td>{{ t.diasHabiles }}</td>
                    <td class="fecha-celda">
                      <strong>{{ t.fechaVencimiento | date: 'EEE dd/MM/yyyy' }}</strong>
                    </td>
                    <td>
                      <span
                        class="badge"
                        [class.badge-danger]="t.situacion === 'VENCIDO'"
                        [class.badge-warning]="t.situacion === 'POR_VENCER'"
                        [class.badge-success]="t.situacion === 'AL_DIA'"
                        [class.badge-muted]="t.situacion === 'CUMPLIDO'"
                        >{{ etiqueta(t.situacion) }}</span
                      >
                      @if (t.situacion !== 'CUMPLIDO') {
                        <br /><small>{{ textoRestantes(t.diasHabilesRestantes) }}</small>
                      }
                    </td>
                    <td class="acciones-celda">
                      @if (t.estado === 'PENDIENTE') {
                        <button class="btn btn-secondary btn-sm" type="button" (click)="cumplir(t)">
                          Ya se cumplió
                        </button>
                      }
                      <button class="btn btn-danger btn-sm" type="button" (click)="eliminar(t)">
                        Quitar
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }

        <form class="mt-5" #form="ngForm" (ngSubmit)="agregar(form)" novalidate>
          <div class="form-grid">
            <div class="form-group form-group-full">
              <label class="form-label" for="descTermino"
                >Nuevo término <span class="requerido">*</span></label
              >
              <input
                id="descTermino"
                class="form-input"
                name="descripcion"
                [(ngModel)]="nuevo.descripcion"
                required
                maxlength="150"
                placeholder="Ej: Contestar la demanda"
              />
            </div>
            <div class="form-group">
              <label class="form-label" for="fechaTermino"
                >Notificado el <span class="requerido">*</span></label
              >
              <input
                id="fechaTermino"
                class="form-input"
                type="date"
                name="fechaInicio"
                [(ngModel)]="nuevo.fechaInicio"
                (ngModelChange)="calcular()"
                required
              />
              <span class="form-ayuda"
                >Fecha del estado electrónico; el conteo empieza el día hábil siguiente.</span
              >
            </div>
            <div class="form-group">
              <label class="form-label" for="diasTermino"
                >Días hábiles <span class="requerido">*</span></label
              >
              <input
                id="diasTermino"
                class="form-input"
                type="number"
                name="diasHabiles"
                [(ngModel)]="nuevo.diasHabiles"
                (ngModelChange)="calcular()"
                required
                min="1"
                max="365"
              />
            </div>
            <div class="form-group">
              <label class="form-label" for="normaTermino">Norma que lo fija</label>
              <input
                id="normaTermino"
                class="form-input"
                name="norma"
                [(ngModel)]="nuevo.norma"
                maxlength="120"
                placeholder="Ej: CGP art. 369"
              />
              <span class="form-ayuda">Opcional. Sirve para que cualquiera sepa de dónde sale el plazo.</span>
            </div>
            <div class="form-group">
              <span class="form-label">Vence</span>
              <span class="vence-calculado">{{
                vence ? (vence | date: 'EEEE dd/MM/yyyy') : '—'
              }}</span>
              @if (errorVence) {
                <span class="error-msg">{{ errorVence }}</span>
              }
            </div>
          </div>
          <div class="form-actions">
            <button class="btn btn-primary" type="submit" [disabled]="guardando || !vence">
              Agregar término
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .vence-calculado {
        font-size: 1.05rem;
        font-weight: 700;
        color: var(--primary);
        padding: 8px 0;
        text-transform: capitalize;
      }
      .subtitulo {
        font-size: 14px;
        font-weight: 700;
        margin: 18px 0 8px;
      }
      .plazos {
        border: 1px solid var(--line);
        border-radius: 12px;
        padding: 10px 14px;
        background: var(--surface-2);
      }
      .plazos summary {
        cursor: pointer;
        font-weight: 700;
      }
      .plazos ul {
        list-style: none;
        margin: 8px 0 0;
        padding: 0;
        display: grid;
        gap: 8px;
        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      }
      .plazos li {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 10px;
        padding: 10px 12px;
        border-radius: 10px;
        border: 1px solid var(--line);
        background: var(--surface);
      }
      .plazos li.opcional {
        border-style: dashed;
      }
      .plazos small {
        display: block;
        color: var(--muted);
      }
      .plazos .a-validar,
      .norma {
        color: var(--accent-ink);
        font-weight: 600;
      }
    `,
  ],
})
export class TerminosProcesoComponent implements OnInit {
  private api = inject(ApiService);
  readonly idProceso = input.required<number>();
  readonly etiqueta = etiqueta;
  readonly textoRestantes = textoRestantes;

  terminos: Termino[] = [];
  /** Los plazos de referencia del tipo de proceso (rama, tipo y lado del cliente). */
  plazos: PlazoProceso[] = [];
  nuevo: TerminoSolicitud = this.vacio();
  vence: string | null = null;
  errorVence = '';
  guardando = false;
  mensaje = '';
  error = '';
  private calculo$ = new Subject<void>();

  ngOnInit(): void {
    this.cargar();
    // Si no se pueden leer los plazos, la pestaña sigue sirviendo: solo no se muestran.
    this.api
      .plazosProceso(this.idProceso())
      .subscribe({ next: (p) => (this.plazos = p), error: () => (this.plazos = []) });
    this.calculo$
      .pipe(
        debounceTime(250),
        switchMap(() => {
          const { fechaInicio, diasHabiles } = this.nuevo;
          if (!fechaInicio || !diasHabiles || diasHabiles < 1)
            return of({ fecha: null, error: '' });
          return this.api.vencimiento(fechaInicio, diasHabiles).pipe(
            switchMap((v) => of({ fecha: v.fechaVencimiento as string | null, error: '' })),
            catchError((e) => of({ fecha: null, error: mensajeDeError(e) })),
          );
        }),
      )
      .subscribe((r) => {
        this.vence = r.fecha;
        this.errorVence = r.error;
      });
    this.calcular();
  }

  vacio(): TerminoSolicitud {
    return { descripcion: '', fechaInicio: hoy(), diasHabiles: 10 };
  }

  calcular(): void {
    this.calculo$.next();
  }

  /** Llena el formulario con un plazo del tipo de proceso; la fecha de notificación la pone quien lo registra. */
  usar(p: PlazoProceso): void {
    this.nuevo = { ...this.nuevo, descripcion: p.descripcion, diasHabiles: p.diasHabiles, norma: p.norma };
    this.calcular();
    setTimeout(() => document.getElementById('fechaTermino')?.focus());
  }

  minuscula(texto: string): string {
    return texto.charAt(0).toLowerCase() + texto.slice(1);
  }

  cargar(): void {
    this.api.terminos({ idProceso: this.idProceso() }).subscribe({
      next: (t) => (this.terminos = t),
      error: (e) => (this.error = mensajeDeError(e)),
    });
  }

  agregar(form: NgForm): void {
    this.mensaje = this.error = '';
    if (form.invalid) {
      this.error = 'Complete la descripción, la fecha y los días.';
      return;
    }
    this.guardando = true;
    this.api.crearTermino(this.idProceso(), this.nuevo).subscribe({
      next: (t) => {
        this.mensaje = `Término registrado: vence el ${t.fechaVencimiento}.`;
        this.guardando = false;
        form.resetForm(this.vacio());
        this.nuevo = this.vacio();
        this.calcular();
        this.cargar();
      },
      error: (e) => {
        this.error = mensajeDeError(e);
        this.guardando = false;
      },
    });
  }

  cumplir(t: Termino): void {
    this.api
      .cumplirTermino(t.idTermino)
      .subscribe({ next: () => this.cargar(), error: (e) => (this.error = mensajeDeError(e)) });
  }

  eliminar(t: Termino): void {
    if (!confirm(`¿Quitar el término "${t.descripcion}"?`)) return;
    this.api
      .eliminarTermino(t.idTermino)
      .subscribe({ next: () => this.cargar(), error: (e) => (this.error = mensajeDeError(e)) });
  }
}

function hoy(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
