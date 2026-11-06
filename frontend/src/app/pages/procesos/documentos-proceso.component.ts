import { Component, OnInit, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AuthService, JURIDICO, JURIDICO_Y_DEPENDIENTES } from '../../core/auth.service';
import { guardarArchivo } from '../../core/descargas';
import { CATEGORIAS_DOCUMENTO, CategoriaDocumento, Documento, etiqueta, tamanoLegible } from '../../core/modelos';

/**
 * Expediente digital (F10): los archivos del proceso. Abogados y dependientes suben; abogados cambian y borran.
 * "Visible para el cliente" lo muestra en su portal (por defecto nada es visible).
 */
@Component({
  selector: 'app-documentos-proceso',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <section class="card">
      <div class="card-header encabezado-gastos">
        <h2>Documentos ({{ documentos.length }})</h2>
        @if (sube) {
          <button class="btn btn-secondary btn-sm" type="button" (click)="abierto = !abierto" [attr.aria-expanded]="abierto">Subir documento</button>
        }
      </div>
      <div class="card-body">
        @if (mensaje) { <div class="alerta alerta-exito">{{ mensaje }}</div> }
        @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }
        @if (abierto) {
          <form (ngSubmit)="subir()" novalidate class="form-gasto">
            <div class="form-grid">
              <div class="form-group form-group-full">
                <label class="form-label" for="dArchivo">Archivo <span class="requerido">*</span></label>
                <input id="dArchivo" class="form-input" type="file" (change)="elegir($event)"
                       accept=".pdf,.docx,.xlsx,.jpg,.jpeg,.png,.txt" />
                <span class="form-ayuda">PDF, Word, Excel, imagen o texto; máximo 20 MB.</span>
              </div>
              <div class="form-group">
                <label class="form-label" for="dCategoria">Categoría</label>
                <select id="dCategoria" class="form-input" name="categoria" [(ngModel)]="categoria">
                  @for (c of categorias; track c) { <option [value]="c">{{ etiqueta(c) }}</option> }
                </select>
              </div>
              <div class="form-group">
                <label class="form-label" for="dDescripcion">Descripción</label>
                <input id="dDescripcion" class="form-input" name="descripcion" [(ngModel)]="descripcion" maxlength="300"
                       placeholder="Ej.: Poder especial firmado y autenticado" />
              </div>
              <div class="form-group">
                <span class="form-label">Portal del cliente</span>
                <label class="resumen-chip chip-check"><input type="checkbox" name="visible" [(ngModel)]="visible" /> Visible para el cliente</label>
              </div>
            </div>
            <div class="form-actions">
              <button class="btn btn-primary" type="submit" [disabled]="subiendo || !archivo">{{ subiendo ? 'Subiendo…' : 'Subir' }}</button>
              <button class="btn btn-secondary" type="button" (click)="abierto = false">Cancelar</button>
            </div>
          </form>
        }
        @if (documentos.length === 0) {
          <p class="form-ayuda">Aún no hay documentos en el expediente.</p>
        } @else {
          <div class="tabla-wrapper">
            <table class="tabla">
              <thead><tr><th>Subido</th><th>Documento</th><th>Categoría</th><th>Tamaño</th><th>Cliente</th><th></th></tr></thead>
              <tbody>
                @for (d of documentos; track d.idDocumento) {
                  <tr>
                    <td class="fecha-celda">{{ d.subido | date:'dd/MM/yyyy' }}</td>
                    <td class="titulo-celda">{{ d.nombre }}@if (d.descripcion) {<br><small>{{ d.descripcion }}</small>}</td>
                    <td>{{ etiqueta(d.categoria) }}</td>
                    <td>{{ tamano(d.tamano) }}</td>
                    <td>
                      @if (juridico) {
                        <label class="chip-check"><input type="checkbox" [checked]="d.visibleCliente" (change)="cambiarVisible(d)"
                               [attr.aria-label]="'Visible para el cliente: ' + d.nombre" /> {{ d.visibleCliente ? 'Visible' : 'Oculto' }}</label>
                      } @else {
                        {{ d.visibleCliente ? 'Visible' : 'Oculto' }}
                      }
                    </td>
                    <td class="acciones-celda">
                      <button class="btn btn-secondary btn-sm" type="button" (click)="descargar(d)">Descargar</button>
                      @if (juridico) {
                        <button class="btn-quitar" type="button" (click)="eliminar(d)" [attr.aria-label]="'Eliminar ' + d.nombre">×</button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </section>
  `
})
export class DocumentosProcesoComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  readonly idProceso = input.required<number>();
  readonly categorias = CATEGORIAS_DOCUMENTO;
  readonly etiqueta = etiqueta;
  readonly tamano = tamanoLegible;
  readonly sube = this.auth.tiene(...JURIDICO_Y_DEPENDIENTES);
  readonly juridico = this.auth.tiene(...JURIDICO);

  documentos: Documento[] = [];
  abierto = false;
  archivo: File | null = null;
  categoria: CategoriaDocumento = 'PRUEBA';
  descripcion = '';
  visible = false;
  subiendo = false;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.api.documentos(this.idProceso()).subscribe({
      next: d => this.documentos = d,
      error: e => this.error = mensajeDeError(e)
    });
  }

  elegir(evento: Event): void {
    const archivo = (evento.target as HTMLInputElement).files?.[0] ?? null;
    this.error = '';
    if (archivo && archivo.size > 20 * 1024 * 1024) {
      this.error = 'El archivo pasa de 20 MB.';
      this.archivo = null;
      return;
    }
    this.archivo = archivo;
  }

  subir(): void {
    if (!this.archivo) return;
    this.subiendo = true;
    this.mensaje = this.error = '';
    this.api.subirDocumento(this.idProceso(), this.archivo, this.categoria, this.descripcion, this.visible).subscribe({
      next: d => {
        this.subiendo = false;
        this.mensaje = `"${d.nombre}" quedó en el expediente${d.visibleCliente ? ' y el cliente lo ve en su portal' : ''}.`;
        this.abierto = false;
        this.archivo = null;
        this.descripcion = '';
        this.visible = false;
        this.cargar();
      },
      error: e => { this.subiendo = false; this.error = mensajeDeError(e); }
    });
  }

  descargar(d: Documento): void {
    this.api.archivoDocumento(this.idProceso(), d.idDocumento).subscribe({
      next: b => guardarArchivo(b, d.nombre),
      error: e => this.error = mensajeDeError(e)
    });
  }

  cambiarVisible(d: Documento): void {
    const cambio = { ...d, visibleCliente: !d.visibleCliente };
    this.api.actualizarDocumento(this.idProceso(), cambio).subscribe({
      next: act => { d.visibleCliente = act.visibleCliente; },
      error: e => { this.error = mensajeDeError(e); this.cargar(); }
    });
  }

  eliminar(d: Documento): void {
    if (!confirm(`¿Eliminar "${d.nombre}" del expediente? No se puede deshacer.`)) return;
    this.api.eliminarDocumento(this.idProceso(), d.idDocumento).subscribe({
      next: () => { this.mensaje = 'Documento eliminado.'; this.cargar(); },
      error: e => this.error = mensajeDeError(e)
    });
  }
}
