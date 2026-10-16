import { Component, OnInit, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { Providencia } from '../../core/modelos';

/** Autos y sentencias del proceso; las detectadas por la vigilancia se marcan. */
@Component({
  selector: 'app-providencias-proceso',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="card">
      <div class="card-header"><h2>Actuaciones del juzgado ({{ providencias.length }})</h2></div>
      <div class="card-body">
        @if (error) { <div class="alerta alerta-error">{{ error }}</div> }
        @if (providencias.length === 0) {
          <p class="form-ayuda">No hay autos ni sentencias registrados.</p>
        } @else {
          <div class="tabla-wrapper">
            <table class="tabla">
              <thead><tr><th>Estado</th><th>Tipo</th><th>Actuación</th><th>Origen</th></tr></thead>
              <tbody>
                @for (p of providencias; track p.idProvidencia) {
                  <tr>
                    <td class="fecha-celda">{{ p.fechaEstado | date:'dd/MM/yyyy' }}</td>
                    <td><span class="badge" [class.badge-primary]="p.tipo === 'AUTO'" [class.badge-warning]="p.tipo === 'SENTENCIA'">{{ p.tipo }}</span></td>
                    <td class="titulo-celda">{{ p.clase }}@if (p.decision) {<br><small>{{ p.decision }}</small>}</td>
                    <td>
                      @if (p.fuente === 'VIGILANCIA') { <span class="badge badge-success">Detectada</span> }
                      @else { <span class="badge badge-muted">Manual</span> }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>
  `
})
export class ProvidenciasProcesoComponent implements OnInit {
  private api = inject(ApiService);
  readonly idProceso = input.required<number>();

  providencias: Providencia[] = [];
  error = '';

  ngOnInit(): void {
    this.api.providencias(this.idProceso()).subscribe({
      next: p => this.providencias = p,
      error: e => this.error = mensajeDeError(e)
    });
  }
}
