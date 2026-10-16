import { Component, OnInit, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { Termino, TerminoSolicitud, etiqueta, textoRestantes } from '../../core/modelos';

/** Términos de un proceso y formulario para agregar uno con su vencimiento calculado en días hábiles. */
@Component({
  selector: 'app-terminos-proceso',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="card">
      <div class="card-header"><h2>Términos del proceso</h2></div>
      <div class="card-body">
        @if (mensaje) { <div class="alerta alerta-exito">{{ mensaje }}</div> }
        @if (error) { <div class="alerta alerta-error">{{ error }}</div> }

        @if (terminos.length === 0) {
          <p class="form-ayuda">Este proceso no tiene términos registrados.</p>
        } @else {
          <div class="tabla-wrapper">
            <table class="tabla">
              <thead><tr><th>Término</th><th>Notificado</th><th>Días</th><th>Vence</th><th>Situación</th><th></th></tr></thead>
              <tbody>
                @for (t of terminos; track t.idTermino) {
                  <tr>
                    <td class="titulo-celda">{{ t.descripcion }}</td>
                    <td class="fecha-celda">{{ t.fechaInicio | date:'dd/MM/yyyy' }}</td>
                    <td>{{ t.diasHabiles }}</td>
                    <td class="fecha-celda"><strong>{{ t.fechaVencimiento | date:'EEE dd/MM/yyyy' }}</strong></td>
                    <td>
                      <span class="badge" [class.badge-danger]="t.situacion === 'VENCIDO'"
                        [class.badge-warning]="t.situacion === 'POR_VENCER'" [class.badge-success]="t.situacion === 'AL_DIA'"
                        [class.badge-muted]="t.situacion === 'CUMPLIDO'">{{ etiqueta(t.situacion) }}</span>
                      @if (t.situacion !== 'CUMPLIDO') { <br><small>{{ textoRestantes(t.diasHabilesRestantes) }}</small> }
                    </td>
                    <td class="acciones-celda">
                      @if (t.estado === 'PENDIENTE') {
                        <button class="btn btn-secondary btn-sm" type="button" (click)="cumplir(t)">Cumplido</button>
                      }
                      <button class="btn btn-danger btn-sm" type="button" (click)="eliminar(t)">Quitar</button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }

        <form #form="ngForm" (ngSubmit)="agregar(form)" novalidate style="margin-top: 20px">
          <div class="form-grid">
            <div class="form-group form-group-full">
              <label class="form-label" for="descTermino">Nuevo término <span class="requerido">*</span></label>
              <input id="descTermino" class="form-input" name="descripcion" [(ngModel)]="nuevo.descripcion" required
                maxlength="150" placeholder="Ej: Contestar la demanda" />
            </div>
            <div class="form-group">
              <label class="form-label" for="fechaTermino">Notificado el <span class="requerido">*</span></label>
              <input id="fechaTermino" class="form-input" type="date" name="fechaInicio" [(ngModel)]="nuevo.fechaInicio"
                (ngModelChange)="calcular()" required />
              <span class="form-ayuda">Fecha del estado electrónico; el conteo empieza el día hábil siguiente.</span>
            </div>
            <div class="form-group">
              <label class="form-label" for="diasTermino">Días hábiles <span class="requerido">*</span></label>
              <input id="diasTermino" class="form-input" type="number" name="diasHabiles" [(ngModel)]="nuevo.diasHabiles"
                (ngModelChange)="calcular()" required min="1" max="365" />
            </div>
            <div class="form-group">
              <span class="form-label">Vence</span>
              <span class="vence-calculado">{{ vence ? (vence | date:'EEEE dd/MM/yyyy') : '—' }}</span>
              @if (errorVence) { <span class="error-msg">{{ errorVence }}</span> }
            </div>
          </div>
          <div class="form-actions">
            <button class="btn btn-primary" type="submit" [disabled]="guardando || !vence">Agregar término</button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`.vence-calculado { font-size: 1.05rem; font-weight: 700; color: var(--primary); padding: 8px 0; text-transform: capitalize; }`]
})
export class TerminosProcesoComponent implements OnInit {
  private api = inject(ApiService);
  readonly idProceso = input.required<number>();
  readonly etiqueta = etiqueta;
  readonly textoRestantes = textoRestantes;

  terminos: Termino[] = [];
  nuevo: TerminoSolicitud = this.vacio();
  vence: string | null = null;
  errorVence = '';
  guardando = false;
  mensaje = '';
  error = '';
  private calculo$ = new Subject<void>();

  ngOnInit(): void {
    this.cargar();
    this.calculo$.pipe(
      debounceTime(250),
      switchMap(() => {
        const { fechaInicio, diasHabiles } = this.nuevo;
        if (!fechaInicio || !diasHabiles || diasHabiles < 1) return of({ fecha: null, error: '' });
        return this.api.vencimiento(fechaInicio, diasHabiles).pipe(
          switchMap(v => of({ fecha: v.fechaVencimiento as string | null, error: '' })),
          catchError(e => of({ fecha: null, error: mensajeDeError(e) })));
      })
    ).subscribe(r => { this.vence = r.fecha; this.errorVence = r.error; });
    this.calcular();
  }

  vacio(): TerminoSolicitud {
    return { descripcion: '', fechaInicio: hoy(), diasHabiles: 10 };
  }

  calcular(): void {
    this.calculo$.next();
  }

  cargar(): void {
    this.api.terminos({ idProceso: this.idProceso() }).subscribe({
      next: t => this.terminos = t,
      error: e => this.error = mensajeDeError(e)
    });
  }

  agregar(form: NgForm): void {
    this.mensaje = this.error = '';
    if (form.invalid) { this.error = 'Complete la descripción, la fecha y los días.'; return; }
    this.guardando = true;
    this.api.crearTermino(this.idProceso(), this.nuevo).subscribe({
      next: t => {
        this.mensaje = `Término registrado: vence el ${t.fechaVencimiento}.`;
        this.guardando = false;
        form.resetForm(this.vacio());
        this.nuevo = this.vacio();
        this.calcular();
        this.cargar();
      },
      error: e => { this.error = mensajeDeError(e); this.guardando = false; }
    });
  }

  cumplir(t: Termino): void {
    this.api.cumplirTermino(t.idTermino).subscribe({ next: () => this.cargar(), error: e => this.error = mensajeDeError(e) });
  }

  eliminar(t: Termino): void {
    if (!confirm(`¿Quitar el término "${t.descripcion}"?`)) return;
    this.api.eliminarTermino(t.idTermino).subscribe({ next: () => this.cargar(), error: e => this.error = mensajeDeError(e) });
  }
}

function hoy(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
