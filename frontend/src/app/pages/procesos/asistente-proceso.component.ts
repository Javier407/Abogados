import { Component, NgZone, OnDestroy, OnInit, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AsistenteService } from '../../core/asistente.service';
import { EstadoAsistente, Providencia, ResumenPublicado, TipoBorrador } from '../../core/modelos';

/**
 * Borradores con el modelo local: contestación, demanda (con los hechos que escribe el abogado), recurso contra una
 * providencia del proceso (F10) o resumen del proceso para el cliente.
 * El resumen, una vez revisado por el abogado, se publica en el portal del cliente y se le avisa (F9).
 */
@Component({
  selector: 'app-asistente-proceso',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="card">
      <div class="card-header"><h2>Asistente de redacción</h2></div>
      <div class="card-body">
        <p class="form-ayuda">
          Modelo local {{ estado?.modelo ?? '' }}: los datos del proceso no salen del servidor.
          @if (estado && !estado.disponible) { <strong>No disponible ({{ estado.detalle }}): se entregará la plantilla.</strong> }
          Es un borrador: revíselo completo antes de usarlo.
        </p>
        <div class="form-grid">
          <div class="form-group">
            <label class="form-label" for="tipoBorrador">Borrador</label>
            <select id="tipoBorrador" class="form-input" [(ngModel)]="tipo" [disabled]="redactando">
              <option value="CONTESTACION">Contestación de la demanda</option>
              <option value="DEMANDA">Demanda</option>
              <option value="RECURSO">Recurso contra una providencia</option>
              <option value="RESUMEN_CLIENTE">Resumen del proceso para el cliente</option>
            </select>
          </div>
          @if (tipo === 'RECURSO') {
            <div class="form-group">
              <label class="form-label" for="providenciaRecurrida">Providencia que se recurre <span class="requerido">*</span></label>
              <select id="providenciaRecurrida" class="form-input" [(ngModel)]="idProvidencia" [disabled]="redactando">
                <option [ngValue]="null">Elija…</option>
                @for (p of providencias; track p.idProvidencia) {
                  <option [ngValue]="p.idProvidencia">{{ p.fechaEstado | date:'dd/MM/yyyy' }} · {{ p.clase }}</option>
                }
              </select>
            </div>
          }
          <div class="form-group form-group-full">
            @if (tipo === 'DEMANDA') {
              <label class="form-label" for="instrucciones">Hechos y pretensiones <span class="requerido">*</span></label>
              <textarea id="instrucciones" class="form-input form-textarea" [(ngModel)]="instrucciones" maxlength="800" rows="5"
                [disabled]="redactando"
                placeholder="Qué pasó (fechas, montos, documentos) y qué pide el cliente. El asistente no inventa hechos: solo ordena y redacta los que usted escriba."></textarea>
            } @else {
              <label class="form-label" for="instrucciones">Indicaciones (opcional)</label>
              <input id="instrucciones" class="form-input" [(ngModel)]="instrucciones" maxlength="800" [disabled]="redactando"
                [placeholder]="tipo === 'RECURSO' ? 'Ej: reposición y en subsidio apelación; el juez no valoró el dictamen' : 'Ej: proponer la excepción de contrato no cumplido'" />
            }
          </div>
        </div>
        <div class="form-actions">
          @if (!redactando) {
            <button class="btn btn-primary" type="button" (click)="generar()">Generar borrador</button>
          } @else {
            <button class="btn btn-secondary" type="button" (click)="detener()">Detener</button>
            <span class="form-ayuda">Redactando{{ segundos ? ' · ' + segundos + ' s' : '' }}… el modelo corre sin GPU y puede tardar unos minutos.</span>
          }
        </div>

        @if (aviso) { <div class="alerta alerta-error" style="margin-top: 12px">{{ aviso }}</div> }
        @if (tipo === 'RESUMEN_CLIENTE') {
          <p class="form-ayuda">
            @if (publicado) { Último resumen publicado en el portal: {{ publicado.publicadoEn | date:'dd/MM/yyyy HH:mm' }}. }
            @else { Aún no se ha publicado un resumen en el portal del cliente. }
            @if (!texto && !redactando) {
              <button class="btn btn-secondary btn-sm" type="button" (click)="escribir()">Escribirlo sin el asistente</button>
            }
          </p>
        }
        @if (mensaje) { <div class="alerta alerta-exito" style="margin-top: 12px">{{ mensaje }}</div> }
        @if (texto || redactando || editando) {
          @if (!editando) {
            <p class="aviso-ia" role="note">Borrador generado con IA: puede equivocarse o inventar datos.
              Revíselo completo antes de radicarlo o enviarlo al cliente.</p>
          }
          <textarea class="form-input borrador" [(ngModel)]="texto" [readonly]="redactando" rows="22"
            aria-label="Borrador generado"></textarea>
          <div class="form-actions">
            <button class="btn btn-secondary btn-sm" type="button" (click)="copiar()" [disabled]="redactando || !texto">
              {{ copiado ? 'Copiado' : 'Copiar' }}
            </button>
            <button class="btn btn-secondary btn-sm" type="button" (click)="descargar()" [disabled]="redactando || !texto">
              Descargar .txt
            </button>
            @if (tipo === 'RESUMEN_CLIENTE') {
              <button class="btn btn-primary btn-sm" type="button" (click)="publicar()"
                      [disabled]="redactando || publicando || texto.trim().length < 20">Publicar en el portal del cliente</button>
            }
            @if (resumen) { <span class="form-ayuda">{{ resumen }}</span> }
          </div>
        }
      </div>
    </div>
  `,
  styles: [`.borrador { margin-top: 14px; font-family: Georgia, 'Times New Roman', serif; font-size: 0.92rem; line-height: 1.55; white-space: pre-wrap; }
    .aviso-ia { margin: 14px 0 0; padding: 8px 12px; border-left: 4px solid #9a6b1f; background: #fbf6ec; color: #3d2f12; font-size: 0.88rem; }`]
})
export class AsistenteProcesoComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private asistente = inject(AsistenteService);
  private zona = inject(NgZone);
  readonly idProceso = input.required<number>();
  readonly radicado = input<string>('');

  estado: EstadoAsistente | null = null;
  tipo: TipoBorrador = 'CONTESTACION';
  instrucciones = '';
  texto = '';
  aviso = '';
  resumen = '';
  redactando = false;
  copiado = false;
  segundos = 0;
  editando = false;
  publicando = false;
  mensaje = '';
  publicado: ResumenPublicado | null = null;
  providencias: Providencia[] = [];
  idProvidencia: number | null = null;
  private cancelar: AbortController | null = null;
  private reloj: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.api.estadoAsistente().subscribe({ next: e => this.estado = e, error: () => this.estado = null });
    this.api.resumenCliente(this.idProceso()).subscribe({ next: r => this.publicado = r, error: () => {} });
    this.api.providencias(this.idProceso()).subscribe({ next: p => this.providencias = p, error: () => {} });
  }

  /** Redactar el resumen a mano (parte del último publicado, si hay). */
  escribir(): void {
    this.editando = true;
    this.texto = this.publicado?.texto ?? '';
  }

  /** El abogado revisó el texto: queda visible para el cliente y se le avisa por la app y el correo. */
  publicar(): void {
    if (!confirm('El cliente verá este texto en su portal y recibirá un aviso. ¿Publicarlo?')) return;
    this.publicando = true;
    this.mensaje = this.aviso = '';
    this.api.publicarResumen(this.idProceso(), this.texto.trim()).subscribe({
      next: r => { this.publicado = r; this.publicando = false; this.mensaje = 'Publicado en el portal. Se avisó al cliente.'; },
      error: e => { this.publicando = false; this.aviso = mensajeDeError(e); }
    });
  }

  async generar(): Promise<void> {
    this.texto = this.aviso = this.resumen = '';
    this.redactando = true;
    this.segundos = 0;
    this.reloj = setInterval(() => this.segundos++, 1000);
    this.cancelar = new AbortController();
    try {
      await this.asistente.redactar(this.idProceso(), this.tipo, this.instrucciones, e => this.zona.run(() => {
        if (e.evento === 'texto') this.texto += e.texto ?? '';
        else if (e.evento === 'aviso') this.aviso = e.mensaje ?? '';
        else if (e.evento === 'fin') {
          this.resumen = e.conModelo ? `Redactado por el modelo en ${e.segundos} s.` : 'Plantilla sin modelo.';
        }
      }), this.cancelar.signal, this.tipo === 'RECURSO' ? this.idProvidencia : null);
    } catch (err) {
      if (!(err instanceof DOMException && err.name === 'AbortError')) {
        this.aviso = err instanceof Error ? err.message : 'No se pudo generar el borrador.';
      }
    } finally {
      this.terminar();
    }
  }

  detener(): void {
    this.cancelar?.abort();
    this.resumen = 'Detenido: puede editar lo que alcanzó a redactar.';
  }

  async copiar(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.texto);
      this.copiado = true;
      setTimeout(() => this.copiado = false, 2000);
    } catch {
      this.aviso = 'El navegador no permitió copiar; seleccione el texto y cópielo a mano.';
    }
  }

  descargar(): void {
    const nombre = `${this.tipo.toLowerCase().replace('_cliente', '')}-${this.radicado() || this.idProceso()}.txt`;
    const url = URL.createObjectURL(new Blob([this.texto], { type: 'text/plain;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = nombre;
    a.click();
    URL.revokeObjectURL(url);
  }

  private terminar(): void {
    this.redactando = false;
    if (this.reloj) clearInterval(this.reloj);
    this.reloj = null;
  }

  ngOnDestroy(): void {
    this.cancelar?.abort();
    if (this.reloj) clearInterval(this.reloj);
  }
}
