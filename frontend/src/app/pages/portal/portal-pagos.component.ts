import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { FinanzasPortal, etiqueta } from '../../core/modelos';

/** Portal del cliente: honorarios pactados, lo pagado, el saldo y los gastos que la firma adelantó. */
@Component({
  selector: 'app-portal-pagos',
  standalone: true,
  imports: [CurrencyPipe, DatePipe],
  template: `
    <div class="modulo-container">
      <div class="page-header">
        <h1>Mis pagos</h1>
        <p>Honorarios acordados con la firma, lo que ha pagado y lo que falta.</p>
      </div>
      @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }
      @if (cargando) { <p class="form-ayuda">Cargando…</p> }

      @if (finanzas; as f) {
        <div class="resumen-bar">
          <span class="resumen-chip">Saldo de honorarios: <strong>{{ f.saldoHonorarios | currency:'COP':'symbol-narrow':'1.0-0' }}</strong></span>
          <span class="resumen-chip">Gastos por reembolsar: <strong>{{ f.totalGastosPorReembolsar | currency:'COP':'symbol-narrow':'1.0-0' }}</strong></span>
        </div>

        @if (f.acuerdos.length === 0) { <div class="empty-state">No hay acuerdos de honorarios registrados.</div> }
        @for (a of f.acuerdos; track a.idHonorario) {
          <section class="card">
            <div class="card-header">
              <h2>{{ radicados[a.idProceso] || ('Proceso ' + a.idProceso) }} · {{ forma(a.formaCobro) }}</h2>
            </div>
            <div class="card-body">
              <dl class="portal-datos">
                <div><dt>Acordado</dt><dd>{{ a.total !== null ? (a.total | currency:'COP':'symbol-narrow':'1.0-0') : 'Según el resultado del proceso' }}
                  @if (a.porcentaje) { <small>({{ a.porcentaje }} %)</small> }</dd></div>
                <div><dt>Pagado</dt><dd>{{ a.pagado | currency:'COP':'symbol-narrow':'1.0-0' }}</dd></div>
                <div><dt>Saldo</dt><dd>{{ a.saldo !== null ? (a.saldo | currency:'COP':'symbol-narrow':'1.0-0') : '—' }}</dd></div>
                <div><dt>Estado</dt><dd>{{ etiqueta(a.estado) }}</dd></div>
              </dl>
              @if (a.pagos.length) {
                <h3 class="subtitulo-detalle">Pagos</h3>
                <ul class="lista-pagos">
                  @for (p of a.pagos; track $index) {
                    <li>{{ p.fecha | date:'dd/MM/yyyy' }} · {{ p.valor | currency:'COP':'symbol-narrow':'1.0-0' }} · {{ etiqueta(p.medio) }}
                      @if (p.referencia) { · {{ p.referencia }} }</li>
                  }
                </ul>
              }
            </div>
          </section>
        }

        @if (f.gastosPorReembolsar.length) {
          <section class="card">
            <div class="card-header"><h2>Gastos que la firma adelantó</h2></div>
            <div class="card-body">
              <ul class="lista-pagos">
                @for (g of f.gastosPorReembolsar; track $index) {
                  <li>{{ g.fecha | date:'dd/MM/yyyy' }} · {{ g.concepto }} · {{ g.valor | currency:'COP':'symbol-narrow':'1.0-0' }}
                    <small>({{ radicados[g.idProceso] || ('proceso ' + g.idProceso) }})</small></li>
                }
              </ul>
            </div>
          </section>
        }
        <p class="form-ayuda">Si ve algo que no coincide con sus registros, escríbale a su abogado.</p>
      }
    </div>
  `
})
export class PortalPagosComponent implements OnInit {
  private api = inject(ApiService);
  readonly etiqueta = etiqueta;

  finanzas: FinanzasPortal | null = null;
  radicados: Record<number, string> = {};
  cargando = true;
  error = '';

  ngOnInit(): void {
    forkJoin({ finanzas: this.api.portalFinanzas(), procesos: this.api.portalProcesos() }).subscribe({
      next: r => {
        this.finanzas = r.finanzas;
        this.radicados = Object.fromEntries(r.procesos.map(p => [p.idProceso, p.radicado]));
        this.cargando = false;
      },
      error: e => { this.error = mensajeDeError(e); this.cargando = false; }
    });
  }

  forma(f: string): string {
    return ({ FIJO: 'Valor fijo', CUOTA_LITIS: 'Cuota litis', MIXTO: 'Fijo más cuota litis',
      PORCENTAJE_CUANTIA: 'Porcentaje de la cuantía', MENSUAL: 'Mensualidad' } as Record<string, string>)[f] ?? etiqueta(f);
  }
}
