import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AuthService, DIRECCION, MANEJAN_FINANZAS } from '../../core/auth.service';
import {
  Cliente, ConfigFacturacion, EstadoConfigFacturacion, EstadoFactura, Factura, FacturaSolicitud, FormaPagoFactura,
  LineaFacturaSolicitud, PendienteFacturar, VistaPreviaFactura
} from '../../core/modelos';

/**
 * Facturación electrónica (F14): facturas emitidas, nueva factura y configuración (resolución de la DIAN, IVA y
 * retenciones). Hoy el proveedor es SIMULADO: el flujo es el real, pero nada llega a la DIAN.
 */
@Component({
  selector: 'app-facturacion',
  standalone: true,
  imports: [FormsModule, RouterLink, CurrencyPipe, DatePipe],
  template: `
    <div class="modulo-container">
      <div class="page-header">
        <h1>Facturación</h1>
        <p>Facturas electrónicas de honorarios y reembolsos de gastos.
          @if (estado?.config?.proveedor === 'SIMULADO') { <span class="badge badge-warning">Proveedor SIMULADO: nada se envía a la DIAN</span> }
          @if (estado?.config?.ambiente === 'PRUEBAS') { <span class="badge badge-muted">Ambiente de pruebas</span> }</p>
      </div>
      @if (mensaje) { <div class="alerta alerta-exito">{{ mensaje }}</div> }
      @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }
      @if (estado?.impedimento) { <div class="alerta alerta-error">{{ estado!.impedimento }}</div> }
      @else if (estado && estado.restantes < 100) { <div class="alerta alerta-error">Quedan {{ estado.restantes }} números en la resolución. Pida una nueva a la DIAN.</div> }

      <div class="resumen-bar" role="tablist">
        <button class="btn btn-sm" type="button" role="tab" [class.btn-primary]="pestana === 'facturas'" [class.btn-secondary]="pestana !== 'facturas'"
                [attr.aria-selected]="pestana === 'facturas'" (click)="pestana = 'facturas'">Facturas</button>
        @if (puedeEmitir) {
          <button class="btn btn-sm" type="button" role="tab" [class.btn-primary]="pestana === 'nueva'" [class.btn-secondary]="pestana !== 'nueva'"
                  [attr.aria-selected]="pestana === 'nueva'" (click)="pestana = 'nueva'">Nueva factura</button>
          <button class="btn btn-sm" type="button" role="tab" [class.btn-primary]="pestana === 'config'" [class.btn-secondary]="pestana !== 'config'"
                  [attr.aria-selected]="pestana === 'config'" (click)="pestana = 'config'">Configuración</button>
        }
      </div>

      @switch (pestana) {
        @case ('facturas') {
          <div class="filtros">
            <select class="form-input" [(ngModel)]="filtroCliente" (ngModelChange)="cargar()" aria-label="Cliente">
              <option [ngValue]="null">Todos los clientes</option>
              @for (c of clientes; track c.idCliente) { <option [ngValue]="c.idCliente">{{ c.nombre }}</option> }
            </select>
            <select class="form-input" [(ngModel)]="filtroEstado" (ngModelChange)="cargar()" aria-label="Estado">
              <option value="">Todos los estados</option><option value="ACEPTADA">Aceptadas</option>
              <option value="RECHAZADA">Rechazadas</option><option value="ANULADA">Anuladas</option>
            </select>
          </div>
          <div class="card"><div class="card-body"><div class="tabla-wrapper"><table class="tabla">
            <thead><tr><th>Número</th><th>Fecha</th><th>Cliente</th><th class="valor-celda">Total</th><th class="valor-celda">A recibir</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              @for (f of facturas; track f.idFactura) {
                <tr>
                  <td class="mono">{{ f.prefijo }}{{ f.numero }}</td>
                  <td class="fecha-celda">{{ f.emitida | date:'dd/MM/yyyy':'-0500' }}</td>
                  <td class="titulo-celda">{{ f.adquirente.nombre }}</td>
                  <td class="valor-celda">{{ f.totales.total | currency:'COP':'symbol-narrow':'1.0-0' }}</td>
                  <td class="valor-celda">{{ f.totales.neto | currency:'COP':'symbol-narrow':'1.0-0' }}</td>
                  <td><span class="badge" [class.badge-success]="f.estado === 'ACEPTADA'" [class.badge-danger]="f.estado === 'RECHAZADA'"
                            [class.badge-muted]="f.estado === 'ANULADA'">{{ etiquetas[f.estado] }}</span></td>
                  <td class="acciones-celda"><a class="btn btn-secondary btn-sm" [routerLink]="['/facturas', f.idFactura]">Ver</a></td>
                </tr>
              } @empty { <tr><td colspan="7" class="empty-state">No hay facturas con ese filtro.</td></tr> }
            </tbody>
          </table></div></div></div>
        }

        @case ('nueva') {
          <section class="card">
            <div class="card-header"><h2>Nueva factura</h2></div>
            <div class="card-body">
              <div class="form-grid">
                <div class="form-group"><label class="form-label" for="fCliente">Cliente</label>
                  <select id="fCliente" class="form-input" [(ngModel)]="nueva.idCliente" (ngModelChange)="elegirCliente()">
                    <option [ngValue]="0" disabled>Elija el cliente</option>
                    @for (c of clientes; track c.idCliente) { <option [ngValue]="c.idCliente">{{ c.nombre }}</option> }
                  </select>
                  @if (clienteSinDatos) { <span class="error-msg">La ficha del cliente necesita correo y dirección para facturar.</span> }</div>
                <div class="form-group"><label class="form-label" for="fForma">Forma de pago</label>
                  <select id="fForma" class="form-input" [(ngModel)]="nueva.formaPago">
                    <option value="CREDITO">Crédito ({{ estado?.config?.diasCredito }} días)</option><option value="CONTADO">Contado</option>
                  </select></div>
                <div class="form-group"><label class="form-label" for="fRet">Retención en la fuente (%)</label>
                  <input id="fRet" class="form-input" type="number" min="0" max="100" step="0.01" [(ngModel)]="nueva.retencionPct"
                         [placeholder]="'Sugerida: ' + (previa?.retencionSugerida ?? '')" />
                  <span class="form-ayuda">La descuenta el cliente al pagar si es agente retenedor.</span></div>
                <div class="form-group form-group-full"><label class="form-label" for="fObs">Observaciones</label>
                  <input id="fObs" class="form-input" maxlength="300" [(ngModel)]="nueva.observaciones" /></div>
              </div>

              @if (pendientes.length) {
                <h3 class="subtitulo-detalle">Pendiente por facturar</h3>
                <ul class="pendientes">
                  @for (p of pendientes; track $index) {
                    <li><span>{{ p.descripcion }} · <strong>{{ p.valor | currency:'COP':'symbol-narrow':'1.0-0' }}</strong></span>
                      <button class="btn btn-secondary btn-sm" type="button" (click)="agregarPendiente(p)" [disabled]="yaAgregado(p)">Agregar</button></li>
                  }
                </ul>
              }

              <h3 class="subtitulo-detalle">Líneas</h3>
              <div class="tabla-wrapper"><table class="tabla">
                <thead><tr><th>Tipo</th><th>Descripción</th><th>Cantidad</th><th>Valor unitario</th><th></th></tr></thead>
                <tbody>
                  @for (l of nueva.lineas; track $index; let i = $index) {
                    <tr>
                      <td>{{ l.tipo === 'SERVICIO' ? 'Servicio' : 'Reembolso' }}</td>
                      @if (l.tipo === 'SERVICIO') {
                        <td><input class="form-input" maxlength="200" [(ngModel)]="l.descripcion" [attr.aria-label]="'Descripción de la línea ' + (i + 1)" /></td>
                        <td><input class="form-input corto" type="number" min="0.01" step="0.01" [(ngModel)]="l.cantidad" [attr.aria-label]="'Cantidad de la línea ' + (i + 1)" /></td>
                        <td><input class="form-input" type="number" min="1" step="0.01" [(ngModel)]="l.valorUnitario" [attr.aria-label]="'Valor unitario de la línea ' + (i + 1)" /></td>
                      } @else {
                        <td>{{ l.descripcion }}</td><td>1</td><td>{{ l.valorUnitario | currency:'COP':'symbol-narrow':'1.0-0' }}</td>
                      }
                      <td class="acciones-celda"><button class="btn-quitar" type="button" (click)="quitar(i)" [attr.aria-label]="'Quitar la línea ' + (i + 1)">×</button></td>
                    </tr>
                  } @empty { <tr><td colspan="5" class="empty-state">Agregue lo pendiente o una línea de servicio.</td></tr> }
                </tbody>
              </table></div>
              <div class="form-actions">
                <button class="btn btn-secondary btn-sm" type="button" (click)="agregarServicio()">Agregar línea de servicio</button>
                <button class="btn btn-secondary btn-sm" type="button" (click)="calcular()" [disabled]="!nueva.idCliente || !nueva.lineas.length">Calcular</button>
              </div>

              @if (previa) {
                <dl class="totales">
                  <dt>Servicios</dt><dd>{{ previa.totales.subtotal | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
                  <dt>IVA</dt><dd>{{ previa.totales.iva | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
                  <dt>Reembolsos (sin IVA)</dt><dd>{{ previa.totales.reembolsos | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
                  <dt class="fuerte">Total factura</dt><dd class="fuerte">{{ previa.totales.total | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
                  <dt>Retención ({{ previa.totales.retencionPct }} %)</dt><dd>− {{ previa.totales.retencion | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
                  <dt class="fuerte">A recibir</dt><dd class="fuerte">{{ previa.totales.neto | currency:'COP':'symbol-narrow':'1.0-2' }}</dd>
                </dl>
                <div class="form-actions">
                  <button class="btn btn-primary" type="button" (click)="emitir()" [disabled]="emitiendo || !!estado?.impedimento">
                    {{ emitiendo ? 'Emitiendo…' : 'Emitir factura' }}</button>
                </div>
              }
            </div>
          </section>
        }

        @case ('config') {
          @if (config) {
            <section class="card">
              <div class="card-header"><h2>Impuestos</h2></div>
              <div class="card-body">
                <p>@if (estado?.config?.validadaPor) { <span class="badge badge-success">Validados</span> por {{ estado!.config.validadaPor }}
                    el {{ estado!.config.validadaEn | date:'d MMM y' }}. }
                  @else { <span class="badge badge-warning">Sin validar</span> Son valores de referencia: que el contador de la firma los revise. }</p>
                <div class="form-grid">
                  <div class="form-group"><span class="form-label">IVA</span>
                    <label class="resumen-chip chip-check"><input type="checkbox" [(ngModel)]="config.responsableIva" [disabled]="!puedeConfigurar" /> La firma es responsable de IVA</label></div>
                  <div class="form-group"><label class="form-label" for="cIva">Tarifa de IVA (%)</label>
                    <input id="cIva" class="form-input" type="number" step="0.01" [(ngModel)]="config.tarifaIva" [disabled]="!puedeConfigurar" /></div>
                  <div class="form-group"><label class="form-label" for="cRetJ">Retención sugerida, persona jurídica (%)</label>
                    <input id="cRetJ" class="form-input" type="number" step="0.01" [(ngModel)]="config.retencionJuridica" [disabled]="!puedeConfigurar" /></div>
                  <div class="form-group"><label class="form-label" for="cRetN">Retención sugerida, persona natural (%)</label>
                    <input id="cRetN" class="form-input" type="number" step="0.01" [(ngModel)]="config.retencionNatural" [disabled]="!puedeConfigurar" /></div>
                  <div class="form-group"><label class="form-label" for="cDias">Días de crédito</label>
                    <input id="cDias" class="form-input" type="number" min="0" max="365" [(ngModel)]="config.diasCredito" [disabled]="!puedeConfigurar" /></div>
                </div>
                @if (!estado?.config?.validadaPor) {
                  <div class="form-actions"><button class="btn btn-secondary" type="button" (click)="validar()">Validar IVA y retenciones</button></div>
                }
              </div>
            </section>
            <section class="card">
              <div class="card-header"><h2>Resolución de numeración (DIAN)</h2></div>
              <div class="card-body">
                <p class="form-ayuda">Quedan {{ estado?.restantes }} números. Mientras la firma se habilita ante la DIAN se usa el rango del set de pruebas (SETP).</p>
                <div class="form-grid">
                  <div class="form-group"><label class="form-label" for="cRes">Número de resolución</label>
                    <input id="cRes" class="form-input" maxlength="30" [(ngModel)]="config.resolucion" [disabled]="!puedeConfigurar" /></div>
                  <div class="form-group"><label class="form-label" for="cPre">Prefijo</label>
                    <input id="cPre" class="form-input" maxlength="4" [(ngModel)]="config.prefijo" [disabled]="!puedeConfigurar" /></div>
                  <div class="form-group"><label class="form-label" for="cDesde">Rango desde</label>
                    <input id="cDesde" class="form-input" type="number" [(ngModel)]="config.rangoDesde" [disabled]="!puedeConfigurar" /></div>
                  <div class="form-group"><label class="form-label" for="cHasta">Rango hasta</label>
                    <input id="cHasta" class="form-input" type="number" [(ngModel)]="config.rangoHasta" [disabled]="!puedeConfigurar" /></div>
                  <div class="form-group"><label class="form-label" for="cSig">Siguiente número</label>
                    <input id="cSig" class="form-input" type="number" [(ngModel)]="config.siguiente" [disabled]="!puedeConfigurar" /></div>
                  <div class="form-group"><label class="form-label" for="cVd">Vigente desde</label>
                    <input id="cVd" class="form-input" type="date" [(ngModel)]="config.vigenteDesde" [disabled]="!puedeConfigurar" /></div>
                  <div class="form-group"><label class="form-label" for="cVh">Vigente hasta</label>
                    <input id="cVh" class="form-input" type="date" [(ngModel)]="config.vigenteHasta" [disabled]="!puedeConfigurar" /></div>
                  <div class="form-group"><label class="form-label" for="cPn">Prefijo de notas crédito</label>
                    <input id="cPn" class="form-input" maxlength="4" [(ngModel)]="config.prefijoNota" [disabled]="!puedeConfigurar" /></div>
                  <div class="form-group"><label class="form-label" for="cAmb">Ambiente</label>
                    <select id="cAmb" class="form-input" [(ngModel)]="config.ambiente" [disabled]="!puedeConfigurar">
                      <option value="PRUEBAS">Pruebas</option><option value="PRODUCCION">Producción</option></select></div>
                  <div class="form-group"><span class="form-label">Proveedor tecnológico</span><span>{{ config.proveedor }}</span></div>
                </div>
                @if (puedeConfigurar) {
                  <div class="form-actions"><button class="btn btn-primary" type="button" (click)="guardarConfig()">Guardar</button></div>
                } @else { <p class="form-ayuda">Solo la dirección (administrador o socio) cambia la configuración.</p> }
              </div>
            </section>
          }
        }
      }
    </div>
  `,
  styles: [`
    .filtros { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 12px; }
    .filtros .form-input { width: auto; min-width: 200px; }
    .pendientes { list-style: none; padding: 0; margin: 0 0 12px; display: grid; gap: 6px; }
    .pendientes li { display: flex; justify-content: space-between; align-items: center; gap: 10px; background: var(--parchment-2);
      border-radius: 8px; padding: 6px 10px; flex-wrap: wrap; }
    .corto { width: 90px; }
    .totales { display: grid; grid-template-columns: max-content max-content; gap: 6px 24px; margin: 16px 0 0 auto; width: fit-content; }
    .totales dd { margin: 0; text-align: right; font-variant-numeric: tabular-nums; }
    .totales .fuerte { font-weight: 700; }
  `]
})
export class FacturacionComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);
  private auth = inject(AuthService);
  readonly puedeEmitir = this.auth.tiene(...MANEJAN_FINANZAS);
  readonly puedeConfigurar = this.auth.tiene(...DIRECCION);
  readonly etiquetas: Record<EstadoFactura, string> = { ACEPTADA: 'Aceptada', RECHAZADA: 'Rechazada', ANULADA: 'Anulada' };

  pestana: 'facturas' | 'nueva' | 'config' = 'facturas';
  clientes: Cliente[] = [];
  facturas: Factura[] = [];
  filtroCliente: number | null = null;
  filtroEstado: EstadoFactura | '' = '';
  estado: EstadoConfigFacturacion | null = null;
  config: ConfigFacturacion | null = null;
  nueva: FacturaSolicitud = this.vacia();
  pendientes: PendienteFacturar[] = [];
  previa: VistaPreviaFactura | null = null;
  emitiendo = false;
  mensaje = '';
  error = '';

  get clienteSinDatos(): boolean {
    const c = this.clientes.find(x => x.idCliente === this.nueva.idCliente);
    return !!c && (!c.email || !c.direccion);
  }

  ngOnInit(): void {
    this.cargar();
    this.api.clientes().subscribe({ next: c => this.clientes = c, error: () => {} });
    if (this.puedeEmitir) this.cargarConfig();
  }

  cargar(): void {
    this.api.facturas(this.filtroCliente, this.filtroEstado).subscribe({
      next: f => this.facturas = f, error: e => this.error = mensajeDeError(e)
    });
  }

  elegirCliente(): void {
    this.nueva.lineas = [];
    this.previa = null;
    this.api.pendientesFacturar(this.nueva.idCliente).subscribe({
      next: p => this.pendientes = p, error: e => this.error = mensajeDeError(e)
    });
  }

  yaAgregado(p: PendienteFacturar): boolean {
    return this.nueva.lineas.some(l => (p.idGasto && l.idGasto === p.idGasto) || (p.idHonorario && l.idHonorario === p.idHonorario));
  }

  agregarPendiente(p: PendienteFacturar): void {
    this.nueva.lineas.push({ tipo: p.tipo, descripcion: p.descripcion, cantidad: 1, valorUnitario: p.valor,
      idHonorario: p.idHonorario ?? null, idGasto: p.idGasto ?? null });
    if (p.idProceso && !this.nueva.idProceso) this.nueva.idProceso = p.idProceso;
    this.calcular();
  }

  agregarServicio(): void {
    this.nueva.lineas.push({ tipo: 'SERVICIO', descripcion: '', cantidad: 1, valorUnitario: undefined });
    this.previa = null;
  }

  quitar(i: number): void {
    this.nueva.lineas.splice(i, 1);
    this.previa = null;
    if (this.nueva.lineas.length) this.calcular();
  }

  calcular(): void {
    this.error = '';
    this.api.vistaPreviaFactura(this.solicitud()).subscribe({
      next: p => this.previa = p, error: e => { this.previa = null; this.error = mensajeDeError(e); }
    });
  }

  emitir(): void {
    if (!this.previa || !confirm(`¿Emitir la factura por ${this.previa.totales.total.toLocaleString('es-CO')} pesos? Una vez emitida solo se puede anular con una nota crédito.`)) return;
    this.emitiendo = true;
    this.error = '';
    this.api.emitirFactura(this.solicitud()).subscribe({
      next: d => { this.emitiendo = false; this.router.navigate(['/facturas', d.factura.idFactura]); },
      error: e => { this.emitiendo = false; this.error = mensajeDeError(e); this.cargarConfig(); }
    });
  }

  guardarConfig(): void {
    if (!this.config) return;
    this.limpiar();
    this.api.guardarConfigFacturacion(this.config).subscribe({
      next: e => { this.ponerConfig(e); this.mensaje = e.config.validadaPor ? 'Configuración guardada.' : 'Configuración guardada. El IVA y las retenciones quedan sin validar.'; },
      error: e => this.error = mensajeDeError(e)
    });
  }

  validar(): void {
    if (!confirm('¿Confirma que el contador revisó la responsabilidad de IVA, la tarifa y las retenciones?')) return;
    this.limpiar();
    this.api.validarConfigFacturacion().subscribe({
      next: e => { this.ponerConfig(e); this.mensaje = 'IVA y retenciones validados.'; },
      error: e => this.error = mensajeDeError(e)
    });
  }

  private solicitud(): FacturaSolicitud {
    const ret = this.nueva.retencionPct;
    return { ...this.nueva, retencionPct: ret === undefined || ret === null || (ret as unknown) === '' ? null : ret,
      lineas: this.nueva.lineas.map(l => ({ ...l, cantidad: l.cantidad || 1 })) };
  }

  private cargarConfig(): void {
    this.api.configFacturacion().subscribe({ next: e => this.ponerConfig(e), error: () => {} });
  }

  private ponerConfig(e: EstadoConfigFacturacion): void {
    this.estado = e;
    this.config = { ...e.config };
  }

  private vacia(): FacturaSolicitud {
    return { idCliente: 0, idProceso: null, formaPago: 'CREDITO' as FormaPagoFactura, retencionPct: null, observaciones: '',
      lineas: [] as LineaFacturaSolicitud[] };
  }

  private limpiar(): void {
    this.mensaje = this.error = '';
  }
}
