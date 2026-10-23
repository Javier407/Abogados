import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AuthService, MANEJAN_FINANZAS } from '../../core/auth.service';
import { DetalleFactura } from '../../core/modelos';

/**
 * Representación gráfica de la factura electrónica (F14): lo que se imprime o se guarda en PDF. Lleva los datos que
 * pide la DIAN: emisor, resolución de numeración, adquirente, líneas, impuestos, CUFE y el código QR de consulta.
 */
@Component({
  selector: 'app-factura',
  standalone: true,
  imports: [RouterLink, CurrencyPipe, DatePipe],
  template: `
    <div class="modulo-container">
      <div class="no-imprimir acciones-factura">
        <a class="btn btn-secondary btn-sm" routerLink="/facturacion">Volver</a>
        @if (d) {
          <button class="btn btn-primary btn-sm" type="button" (click)="imprimir()">Imprimir o guardar PDF</button>
          @if (puedeAnular && d.factura.estado === 'ACEPTADA') {
            <button class="btn btn-danger btn-sm" type="button" (click)="anular()">Anular con nota crédito</button>
          }
        }
      </div>
      @if (mensaje) { <div class="alerta alerta-exito no-imprimir">{{ mensaje }}</div> }
      @if (error) { <div class="alerta alerta-error no-imprimir" role="alert">{{ error }}</div> }

      @if (d; as d) {
        <article class="card factura">
          @if (d.factura.ambiente === 'PRUEBAS') { <p class="marca-prueba">Documento de prueba · sin validez fiscal</p> }
          <header class="cabeza">
            <div>
              <h1>{{ d.emisor.razonSocial }}</h1>
              <p>NIT {{ d.emisor.nit }} · {{ d.emisor.direccion }}, {{ d.emisor.ciudad }}
                @if (d.emisor.telefono) { · Tel. {{ d.emisor.telefono }} } @if (d.emisor.email) { · {{ d.emisor.email }} }</p>
              <p class="pequeno">Autorización de numeración DIAN N.º {{ d.factura.resolucion }}
                @if (d.config.resolucion === d.factura.resolucion) { del {{ d.config.vigenteDesde }} al {{ d.config.vigenteHasta }},
                  {{ d.config.prefijo }}{{ d.config.rangoDesde }} a {{ d.config.prefijo }}{{ d.config.rangoHasta }} }</p>
            </div>
            <div class="numero">
              <span>Factura electrónica de venta</span>
              <strong class="mono">{{ d.factura.prefijo }}{{ d.factura.numero }}</strong>
              <span class="badge" [class.badge-success]="d.factura.estado === 'ACEPTADA'" [class.badge-danger]="d.factura.estado === 'RECHAZADA'"
                    [class.badge-muted]="d.factura.estado === 'ANULADA'">{{ d.factura.estado }}</span>
            </div>
          </header>

          <section class="datos">
            <div><h2>Adquirente</h2>
              <p><strong>{{ d.factura.adquirente.nombre }}</strong><br>{{ d.factura.adquirente.tipoDocumento }} {{ d.factura.adquirente.numeroDocumento }}<br>
                {{ d.factura.adquirente.direccion }}, {{ d.factura.adquirente.ciudad }}<br>{{ d.factura.adquirente.email }}</p></div>
            <div><h2>Factura</h2>
              <p>Emitida (hora de Colombia): {{ d.factura.emitida | date:'dd/MM/yyyy HH:mm':'-0500' }}<br>Vence: {{ d.factura.vence | date:'dd/MM/yyyy' }}<br>
                Forma de pago: {{ d.factura.formaPago === 'CONTADO' ? 'Contado' : 'Crédito' }}
                @if (d.factura.idProceso) { <br>Proceso: {{ d.factura.idProceso }} }</p></div>
          </section>

          <table class="tabla">
            <thead><tr><th>#</th><th>Descripción</th><th class="valor-celda">Cant.</th><th class="valor-celda">Valor unitario</th>
              <th class="valor-celda">IVA</th><th class="valor-celda">Valor</th></tr></thead>
            <tbody>
              @for (l of d.factura.lineas; track l.linea) {
                <tr><td>{{ l.linea }}</td><td>{{ l.descripcion }}</td><td class="valor-celda">{{ l.cantidad }}</td>
                  <td class="valor-celda">{{ l.valorUnitario | currency:'COP':'symbol-narrow':'1.0-2' }}</td>
                  <td class="valor-celda">{{ l.ivaPct }} %</td>
                  <td class="valor-celda">{{ l.valor | currency:'COP':'symbol-narrow':'1.0-2' }}</td></tr>
              }
            </tbody>
          </table>

          <section class="pie">
            <div class="qr">
              @if (qr()) { <img [src]="qr()" width="150" height="150" alt="Código QR de consulta en la DIAN" /> }
              <p class="pequeno cufe">CUFE: <span class="mono">{{ d.factura.cufe || 'pendiente' }}</span></p>
              @if (d.factura.respuesta) { <p class="pequeno">{{ d.factura.respuesta }}</p> }
            </div>
            <dl class="totales">
              <dt>Servicios</dt><dd>{{ d.factura.totales.subtotal | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
              <dt>IVA</dt><dd>{{ d.factura.totales.iva | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
              <dt>Reembolsos de gastos (sin IVA)</dt><dd>{{ d.factura.totales.reembolsos | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
              <dt class="fuerte">Total</dt><dd class="fuerte">{{ d.factura.totales.total | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
              @if (d.factura.totales.retencion > 0) {
                <dt>Retención en la fuente estimada ({{ d.factura.totales.retencionPct }} %)</dt>
                <dd>− {{ d.factura.totales.retencion | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
                <dt class="fuerte">Valor a pagar</dt><dd class="fuerte">{{ d.factura.totales.neto | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
              }
            </dl>
          </section>
          @if (d.factura.observaciones) { <p><strong>Observaciones:</strong> {{ d.factura.observaciones }}</p> }
          @if (d.factura.notaCredito; as n) {
            <p class="anulada"><strong>Anulada</strong> con la nota crédito {{ n.prefijo }}{{ n.numero }} del {{ n.emitida | date:'dd/MM/yyyy':'-0500' }}
              por {{ n.valor | currency:'COP':'symbol-narrow':'1.0-2' }}: {{ n.motivo }}<br><span class="pequeno">CUDE: <span class="mono">{{ n.cude }}</span></span></p>
          }
          <p class="pequeno">Consulte esta factura en la DIAN: {{ d.urlConsulta }}</p>
        </article>
      } @else if (!error) { <p class="empty-state">Cargando…</p> }
    </div>
  `,
  styles: [`
    .acciones-factura { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
    .factura { padding: 24px; position: relative; }
    .marca-prueba { position: absolute; top: 10px; right: 24px; margin: 0; color: var(--mahogany); font-weight: 700;
      text-transform: uppercase; letter-spacing: 1px; font-size: 12px; }
    .cabeza { display: flex; justify-content: space-between; gap: 20px; flex-wrap: wrap; border-bottom: 2px solid var(--blue);
      padding-bottom: 12px; margin-bottom: 14px; }
    .cabeza h1 { margin: 0 0 4px; font-family: var(--display); font-size: 22px; color: var(--blue); }
    .cabeza p { margin: 2px 0; }
    .numero { display: grid; gap: 4px; text-align: right; align-content: start; }
    .numero strong { font-size: 20px; }
    .datos { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; margin-bottom: 14px; }
    .datos h2 { font-size: 13px; text-transform: uppercase; color: var(--muted); margin: 0 0 4px; letter-spacing: .5px; }
    .datos p { margin: 0; }
    .pie { display: flex; justify-content: space-between; gap: 20px; flex-wrap: wrap; margin-top: 16px; }
    .qr { max-width: 360px; }
    .cufe { word-break: break-all; }
    .totales { display: grid; grid-template-columns: max-content max-content; gap: 6px 24px; margin: 0; align-content: start; }
    .totales dd { margin: 0; text-align: right; font-variant-numeric: tabular-nums; }
    .totales .fuerte { font-weight: 700; }
    .pequeno { font-size: 12px; color: var(--muted); }
    .anulada { border: 1px solid var(--mahogany); border-radius: 8px; padding: 8px 12px; color: var(--mahogany); }
    @media print { .factura { border: 0 !important; padding: 0; } }
  `]
})
export class FacturaComponent implements OnInit {
  private api = inject(ApiService);
  private ruta = inject(ActivatedRoute);
  readonly puedeAnular = inject(AuthService).tiene(...MANEJAN_FINANZAS);
  /** Signal: el QR se arma después de un await nativo, fuera de zone.js. */
  readonly qr = signal('');
  d: DetalleFactura | null = null;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    this.cargar();
  }

  imprimir(): void {
    window.print();
  }

  anular(): void {
    const motivo = prompt('Motivo de la anulación (queda en la nota crédito):');
    if (!motivo || !motivo.trim() || !this.d) return;
    this.mensaje = this.error = '';
    this.api.anularFactura(this.d.factura.idFactura, motivo.trim()).subscribe({
      next: d => { this.poner(d); this.mensaje = 'Factura anulada con una nota crédito.'; },
      error: e => this.error = mensajeDeError(e)
    });
  }

  private cargar(): void {
    const id = Number(this.ruta.snapshot.paramMap.get('id'));
    this.api.factura(id).subscribe({ next: d => this.poner(d), error: e => this.error = mensajeDeError(e) });
  }

  private async poner(d: DetalleFactura): Promise<void> {
    this.d = d;
    this.qr.set('');
    const modulo = await import('qrcode');
    const qr = ((modulo as unknown as { default?: typeof modulo }).default ?? modulo);
    try {
      this.qr.set(await qr.toDataURL(d.textoQr, { margin: 1, width: 150, errorCorrectionLevel: 'M' }));
    } catch (e) {
      console.error('No se pudo dibujar el código QR', e);
    }
  }
}
