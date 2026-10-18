import { Component, OnInit, inject, input } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AuthService, MANEJAN_FINANZAS } from '../../core/auth.service';
import { Gasto, GastoSolicitud } from '../../core/modelos';

/** Gastos del proceso: lo que adelantó la firma (y si el cliente lo debe devolver) o lo que pagó el cliente. */
@Component({
  selector: 'app-gastos-proceso',
  standalone: true,
  imports: [FormsModule, CurrencyPipe, DatePipe],
  template: `
    <section class="card">
      <div class="card-header encabezado-gastos">
        <h2>Gastos ({{ gastos.length }}) · {{ total | currency:'COP':'symbol-narrow':'1.0-0' }}</h2>
        <button class="btn btn-secondary btn-sm" type="button" (click)="abierto = !abierto" [attr.aria-expanded]="abierto">Registrar gasto</button>
      </div>
      <div class="card-body">
        @if (mensaje) { <div class="alerta alerta-exito">{{ mensaje }}</div> }
        @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }
        @if (abierto) {
          <form #f="ngForm" (ngSubmit)="registrar(f)" novalidate class="form-gasto">
            <div class="form-grid">
              <div class="form-group form-group-full">
                <label class="form-label" for="gConcepto">Concepto <span class="requerido">*</span></label>
                <input id="gConcepto" class="form-input" name="concepto" [(ngModel)]="nuevo.concepto" required maxlength="150"
                       placeholder="Ej.: Copias auténticas del expediente, honorarios del perito, póliza judicial" />
              </div>
              <div class="form-group">
                <label class="form-label" for="gFecha">Fecha</label>
                <input id="gFecha" class="form-input" type="date" name="fecha" [(ngModel)]="nuevo.fecha" required [max]="hoy" />
              </div>
              <div class="form-group">
                <label class="form-label" for="gValor">Valor (COP) <span class="requerido">*</span></label>
                <input id="gValor" class="form-input" type="number" name="valor" [(ngModel)]="nuevo.valor" required min="1" />
              </div>
              <div class="form-group">
                <label class="form-label" for="gQuien">Lo pagó</label>
                <select id="gQuien" class="form-input" name="pagadoPor" [(ngModel)]="nuevo.pagadoPor" (ngModelChange)="nuevo.reembolsable = nuevo.reembolsable && $event === 'FIRMA'">
                  <option value="FIRMA">La firma</option>
                  <option value="CLIENTE">El cliente</option>
                </select>
              </div>
              <div class="form-group">
                <span class="form-label">Reembolso</span>
                <label class="resumen-chip chip-check"><input type="checkbox" name="reembolsable" [(ngModel)]="nuevo.reembolsable"
                       [disabled]="nuevo.pagadoPor !== 'FIRMA'" /> El cliente lo debe devolver</label>
              </div>
              <div class="form-group form-group-full">
                <label class="form-label" for="gSoporte">Enlace al soporte</label>
                <input id="gSoporte" class="form-input" type="url" name="soporteUrl" [(ngModel)]="nuevo.soporteUrl" placeholder="https://…" maxlength="300" />
              </div>
            </div>
            <div class="form-actions">
              <button class="btn btn-primary" type="submit" [disabled]="guardando">Guardar gasto</button>
              <button class="btn btn-secondary" type="button" (click)="abierto = false">Cancelar</button>
            </div>
          </form>
        }
        @if (gastos.length === 0) {
          <p class="form-ayuda">No hay gastos registrados.</p>
        } @else {
          <div class="tabla-wrapper">
            <table class="tabla">
              <thead><tr><th>Fecha</th><th>Concepto</th><th>Valor</th><th>Lo pagó</th><th>Reembolso</th>@if (maneja) {<th></th>}</tr></thead>
              <tbody>
                @for (g of gastos; track g.idGasto) {
                  <tr>
                    <td class="fecha-celda">{{ g.fecha | date:'dd/MM/yyyy' }}</td>
                    <td class="titulo-celda">{{ g.concepto }}@if (g.soporteUrl) {<br><small><a [href]="g.soporteUrl" target="_blank" rel="noopener">Soporte</a></small>}</td>
                    <td class="valor-celda">{{ g.valor | currency:'COP':'symbol-narrow':'1.0-0' }}</td>
                    <td>{{ g.pagadoPor === 'FIRMA' ? 'La firma' : 'El cliente' }}</td>
                    <td>
                      @if (!g.reembolsable) { — }
                      @else if (g.reembolsadoEn) { <span class="badge badge-success">Reembolsado</span> }
                      @else { <span class="badge badge-warning">Por reembolsar</span> }
                    </td>
                    @if (maneja) {
                      <td class="acciones-celda">
                        @if (g.reembolsable && !g.reembolsadoEn) {
                          <button class="btn btn-secondary btn-sm" type="button" (click)="reembolsar(g)">Reembolsado</button>
                        }
                        @if (!g.reembolsadoEn) {
                          <button class="btn-quitar" type="button" (click)="eliminar(g)" [attr.aria-label]="'Eliminar el gasto ' + g.concepto">×</button>
                        }
                      </td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </section>
  `,
  styles: [`
    .encabezado-gastos { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
    .form-gasto { background: var(--parchment-2); border-radius: 14px; padding: 16px; margin-bottom: 16px; }
    .chip-check { background: #fff; display: inline-flex; align-items: center; gap: 6px; cursor: pointer; }
    .btn-quitar { border: 0; background: none; color: var(--muted); font-size: 18px; cursor: pointer; padding: 0 4px; }
    .btn-quitar:hover { color: var(--mahogany); }
  `]
})
export class GastosProcesoComponent implements OnInit {
  private api = inject(ApiService);
  readonly maneja = inject(AuthService).tiene(...MANEJAN_FINANZAS);
  readonly idProceso = input.required<number>();
  readonly hoy = new Date().toLocaleDateString('en-CA');

  gastos: Gasto[] = [];
  nuevo: GastoSolicitud = this.vacio(0);
  abierto = false;
  guardando = false;
  mensaje = '';
  error = '';

  get total(): number {
    return this.gastos.reduce((s, g) => s + g.valor, 0);
  }

  ngOnInit(): void {
    this.nuevo = this.vacio(this.idProceso());
    this.cargar();
  }

  cargar(): void {
    this.api.gastos({ idProceso: this.idProceso() }).subscribe({ next: g => this.gastos = g, error: e => this.error = mensajeDeError(e) });
  }

  registrar(f: NgForm): void {
    if (f.invalid || !this.nuevo.valor) { this.error = 'Indique concepto, fecha y valor.'; return; }
    this.guardando = true;
    this.mensaje = this.error = '';
    const datos = { ...this.nuevo, soporteUrl: this.nuevo.soporteUrl || null };
    this.api.registrarGasto(datos).subscribe({
      next: g => {
        this.guardando = false;
        this.mensaje = 'Gasto registrado' + (g.reembolsable ? ': queda en la cartera del cliente hasta que lo devuelva.' : '.');
        this.abierto = false;
        this.nuevo = this.vacio(this.idProceso());
        this.cargar();
      },
      error: e => { this.guardando = false; this.error = mensajeDeError(e); }
    });
  }

  reembolsar(g: Gasto): void {
    this.api.reembolsarGasto(g.idGasto).subscribe({ next: () => this.cargar(), error: e => this.error = mensajeDeError(e) });
  }

  eliminar(g: Gasto): void {
    if (!confirm(`¿Eliminar el gasto «${g.concepto}»?`)) return;
    this.api.eliminarGasto(g.idGasto).subscribe({ next: () => this.cargar(), error: e => this.error = mensajeDeError(e) });
  }

  private vacio(idProceso: number): GastoSolicitud {
    return { idProceso, fecha: this.hoy, concepto: '', valor: null, pagadoPor: 'FIRMA', reembolsable: true, soporteUrl: '' };
  }
}
